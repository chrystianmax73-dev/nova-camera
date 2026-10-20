"""
Converte o PP-PicoDet (PaddleDetection, Apache-2.0) do ONNX oficial "sem pós-processamento" para ncnn:
detect.param / detect.bin. Saídas: 4 notas de classe (sigmoid, 80 classes COCO) e 4 distribuições de caixa
(DFL, reg_max 7) nos passos 8/16/32/64; a decodificação e o NMS ficam no app (core/Detections.kt), como no
demo oficial deploy/third_engine/demo_ncnn/picodet.cpp.

O exportador da Paddle grava o "roi" do Resize como tensor não vazio; o ncnn não aceita. Aqui o roi vira um
tensor vazio (só tem efeito no modo tf_crop_and_resize, que o modelo não usa) e os pesos saem das entradas.

Depois da conversão, ncnn × onnxruntime na imagem de exemplo do próprio PaddleDetection (000000014439.jpg):
notas de classe precisam concordar (dif. máx. < 0,05) e a detecção mais forte precisa ser "pessoa". Tenta fp16 e,
se falhar, fp32; os arquivos só vão para o APK depois de aprovados (0.34.0 levou um modelo reprovado porque a
cópia vinha antes da checagem). Se nenhum passar, uma busca binária no grafo mostra o primeiro nó que diverge.

Uso no CI:
    python export_ncnn.py --onnx picodet_s_416_coco_lcnet.onnx --image 000000014439.jpg --out app/src/main/assets/detect
"""
import argparse
import os
import shutil
import subprocess
import sys

import numpy as np
import onnx
import onnx.helper
import onnx.utils
from onnx import numpy_helper

MEAN = np.array([103.53, 116.28, 123.675], dtype=np.float32)
STD = np.array([57.375, 57.12, 58.395], dtype=np.float32)


def fix(src, dst):
    m = onnx.load(src)
    g = m.graph
    n = 0
    for node in g.node:
        if node.op_type == "Resize" and len(node.input) >= 2 and node.input[1]:
            node.input[1] = "nova_empty_roi"
            n += 1
    if n:
        g.initializer.append(numpy_helper.from_array(np.zeros((0,), dtype=np.float32), "nova_empty_roi"))
    inits = {i.name for i in g.initializer}
    keep = [i for i in g.input if i.name not in inits]
    del g.input[:]
    g.input.extend(keep)
    onnx.save(m, dst)
    print(f"Resize corrigidos: {n}")


def preprocess(path, size):
    from PIL import Image
    im = Image.open(path).convert("RGB").resize((size, size), Image.BILINEAR)
    return ((np.asarray(im).astype(np.float32)[:, :, ::-1] - MEAN) / STD).transpose(2, 0, 1).astype(np.float32)


def ncnn_run(param, bin_, x, n_out):
    import ncnn
    net = ncnn.Net()
    net.load_param(param)
    net.load_model(bin_)
    # Referências explícitas: o binding não segura a entrada nem as saídas sozinho, e um Mat temporário
    # liberado cedo dá lixo (primeiro valor -7e19 visto num corte de uma convolução só).
    xc = np.ascontiguousarray(x)
    mat_in = ncnn.Mat(xc)
    ex = net.create_extractor()
    ex.input("in0", mat_in)
    outs = []
    for k in range(n_out):
        ret, mat = ex.extract(f"out{k}")
        outs.append(np.array(mat, copy=True))
    del ex
    return outs


def convert(pnnx, work, size, fp16):
    subprocess.run([pnnx, "m.onnx", f"inputshape=[1,3,{size},{size}]", f"fp16={fp16}"], cwd=work, check=True,
                   stdout=subprocess.DEVNULL)
    return os.path.join(work, "m.ncnn.param"), os.path.join(work, "m.ncnn.bin")


def first_divergence(pnnx, fixed, x, size, work):
    """Corta o grafo no nó k, converte e compara; busca binária pelo primeiro nó que diverge. Só diagnóstico."""
    import onnxruntime as ort
    m = onnx.load(fixed)
    nodes = [n for n in m.graph.node if len(n.output) == 1 and n.op_type != "Constant"]
    names = [n.output[0] for n in nodes]
    probe = onnx.load(fixed)
    del probe.graph.output[:]
    probe.graph.output.extend(onnx.helper.make_empty_tensor_value_info(o) for o in names)
    p = os.path.join(work, "probe.onnx")
    onnx.save(probe, p)
    so = ort.SessionOptions(); so.log_severity_level = 3
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
    sess = ort.InferenceSession(p, so)
    ref = dict(zip(names, sess.run(None, {sess.get_inputs()[0].name: x[None]})))

    def bad(k):
        d = os.path.join(work, f"cut{k}")
        os.makedirs(d, exist_ok=True)
        try:
            onnx.utils.extract_model(fixed, os.path.join(d, "m.onnx"), [m.graph.input[0].name], [names[k]])
            pr, bn = convert(pnnx, d, size, 0)
            o = ncnn_run(pr, bn, x, 1)[0].reshape(-1)
        except Exception as e:  # corte que o pnnx não converte: trata como divergente para seguir a busca
            print(f"  corte {k} ({nodes[k].op_type}) não converteu: {e}")
            return True
        r = ref[names[k]].reshape(-1)
        if o.size != r.size:
            print(f"  corte {k} ({nodes[k].op_type}): tamanho {o.size} × {r.size}")
            return True
        rel = float(np.abs(o - r).max()) / (float(np.abs(r).max()) + 1e-6)
        print(f"  corte {k} ({nodes[k].op_type} {names[k]}): dif. relativa {rel:.4f}")
        return rel > 0.02

    lo, hi = 0, len(nodes) - 1
    if not bad(hi):
        print("diagnóstico: o último nó concorda; a diferença está na ordem/forma das saídas")
        return
    while lo < hi:
        mid = (lo + hi) // 2
        if bad(mid): hi = mid
        else: lo = mid + 1
    n = nodes[lo]
    print(f"PRIMEIRO NÓ DIVERGENTE: #{lo} {n.op_type} {n.name} entradas={list(n.input)}")
    print("  atributos:", {a.name: onnx.helper.get_attribute_value(a) for a in n.attribute})
    for i in n.input:
        init = next((t for t in m.graph.initializer if t.name == i), None)
        if init is not None and init.dims and np.prod(init.dims) <= 16:
            print(f"  {i} = {numpy_helper.to_array(init).tolist()}")


def check(outs, ref):
    cls = [o for o in outs if o.shape[-1] == 80]
    ref_cls = [r for r in ref if r.shape[-1] == 80]
    if len(cls) != 4 or [c.shape for c in cls] != [r.shape for r in ref_cls]:
        return False, 9.0, -1
    diff = max(float(np.abs(o - r).max()) for o, r in zip(cls, ref_cls))
    best = max(range(80), key=lambda c: max(float(o[:, c].max()) for o in cls))
    return diff <= 0.05 and best == 0, diff, best


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--onnx", required=True)
    ap.add_argument("--image", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=416)
    a = ap.parse_args()
    import onnxruntime as ort
    # Tudo acontece em _work; detect.param/bin só são copiados para o APK depois de aprovados.
    work = os.path.join(a.out, "_work")
    for f in ("detect.param", "detect.bin"):
        if os.path.exists(os.path.join(a.out, f)):
            os.remove(os.path.join(a.out, f))
    os.makedirs(work, exist_ok=True)
    fixed = os.path.join(work, "m.onnx")
    fix(a.onnx, fixed)
    pnnx = shutil.which("pnnx") or os.path.join(os.path.dirname(sys.executable), "pnnx")
    x = preprocess(a.image, a.size)
    so = ort.SessionOptions(); so.log_severity_level = 3
    sess = ort.InferenceSession(a.onnx, so)
    ref = [o[0] for o in sess.run(None, {sess.get_inputs()[0].name: x[None]})]
    print("saídas onnx:", [r.shape for r in ref])
    for fp16 in (1, 0):
        pr, bn = convert(pnnx, work, a.size, fp16)
        outs = ncnn_run(pr, bn, x, len(ref))
        ok, diff, best = check(outs, ref)
        print(f"fp16={fp16}: saídas {[o.shape for o in outs]}; notas dif. máx {diff:.4f}; classe mais forte = {best} (0 = pessoa)")
        if ok:
            shutil.copy(pr, os.path.join(a.out, "detect.param"))
            shutil.copy(bn, os.path.join(a.out, "detect.bin"))
            shutil.rmtree(work, ignore_errors=True)
            print("APROVADO")
            return
    print("diagnóstico (busca do primeiro nó divergente):")
    try:
        first_divergence(pnnx, fixed, x, a.size, work)
    except Exception as e:
        print("diagnóstico falhou:", e)
    shutil.rmtree(work, ignore_errors=True)
    print("REPROVADO: o detector não entra no APK", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
    main()
