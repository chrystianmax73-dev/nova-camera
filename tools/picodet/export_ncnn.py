"""
Converte o PP-PicoDet (PaddleDetection, Apache-2.0) do ONNX oficial "sem pós-processamento" para ncnn:
detect.param / detect.bin. Saídas: 4 notas de classe (sigmoid, 80 classes COCO) e 4 distribuições de caixa
(DFL, reg_max 7) nos passos 8/16/32/64; a decodificação e o NMS ficam no app (core/Detections.kt), como no
demo oficial deploy/third_engine/demo_ncnn/picodet.cpp.

O exportador da Paddle grava o "roi" do Resize como tensor não vazio; o ncnn não aceita. Aqui o roi vira um
tensor vazio (só tem efeito no modo tf_crop_and_resize, que o modelo não usa) e os pesos saem das entradas.

Depois da conversão, ncnn × onnxruntime na imagem de exemplo do próprio PaddleDetection (000000014439.jpg):
notas de classe precisam concordar (dif. máx. < 0,05) e a detecção mais forte precisa ser "pessoa". Senão,
o modelo não entra no APK.

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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--onnx", required=True)
    ap.add_argument("--image", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=416)
    a = ap.parse_args()
    work = os.path.join(a.out, "_work")
    os.makedirs(work, exist_ok=True)
    fixed = os.path.join(work, "m.onnx")
    fix(a.onnx, fixed)
    pnnx = shutil.which("pnnx") or os.path.join(os.path.dirname(sys.executable), "pnnx")
    subprocess.run([pnnx, "m.onnx", f"inputshape=[1,3,{a.size},{a.size}]"], cwd=work, check=True)
    shutil.copy(os.path.join(work, "m.ncnn.param"), os.path.join(a.out, "detect.param"))
    shutil.copy(os.path.join(work, "m.ncnn.bin"), os.path.join(a.out, "detect.bin"))

    import ncnn
    import onnxruntime as ort
    from PIL import Image
    im = Image.open(a.image).convert("RGB").resize((a.size, a.size), Image.BILINEAR)
    x = ((np.asarray(im).astype(np.float32)[:, :, ::-1] - MEAN) / STD).transpose(2, 0, 1).astype(np.float32)
    so = ort.SessionOptions(); so.log_severity_level = 3
    sess = ort.InferenceSession(a.onnx, so)
    ref = [o[0] for o in sess.run(None, {sess.get_inputs()[0].name: x[None]})]
    net = ncnn.Net()
    net.load_param(os.path.join(a.out, "detect.param"))
    net.load_model(os.path.join(a.out, "detect.bin"))
    ex = net.create_extractor()
    ex.input("in0", ncnn.Mat(np.ascontiguousarray(x)).clone())
    outs = [np.array(ex.extract(f"out{k}")[1]) for k in range(len(ref))]
    shutil.rmtree(work, ignore_errors=True)
    print("saídas:", [o.shape for o in outs])
    cls = [o for o in outs if o.shape[-1] == 80]
    ref_cls = [r for r in ref if r.shape[-1] == 80]
    diff = max(float(np.abs(o - r).max()) for o, r in zip(cls, ref_cls))
    best = max(range(80), key=lambda c: max(float(o[:, c].max()) for o in cls))
    print(f"ncnn × onnxruntime: notas dif. máx {diff:.4f}; classe mais forte = {best} (0 = pessoa)")
    if len(cls) != 4 or diff > 0.05 or best != 0:
        print("REPROVADO: o detector não entra no APK", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
