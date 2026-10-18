"""
Exporta o Depth Anything V2 Small (profundidade relativa, Apache-2.0) para ncnn: depth.param / depth.bin.

Pesos oficiais: depth-anything/Depth-Anything-V2-Small-hf (Hugging Face, licença Apache-2.0 — só a
versão Small; Base/Large/Giant são CC-BY-NC-4.0 e NÃO podem ser usadas). Arquitetura da biblioteca
transformers (Apache-2.0). Entrada fixa [1,3,S,S] (S múltiplo de 14), RGB normalizado ImageNet;
saída [1,1,S,S] de profundidade inversa relativa (maior = mais perto).

Uso no CI:
    python export_ncnn.py --hf-dir <pasta baixada> --out app/src/main/assets/depth
Teste local do caminho de conversão (sem os pesos oficiais):
    python export_ncnn.py --random --out /tmp/depth

Depois da conversão, o ncnn é comparado ao PyTorch numa imagem de teste (correlação ≥ 0,995 e erro
relativo ≤ 3% da faixa); se reprovar, o modelo não entra no APK.
"""
import argparse
import os
import shutil
import sys

import numpy as np
import torch
import torch.nn as nn


class Wrapper(nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, x):
        return self.m(pixel_values=x).predicted_depth.unsqueeze(1)


def build(hf_dir, random):
    from transformers import DepthAnythingConfig, DepthAnythingForDepthEstimation, Dinov2Config
    if hf_dir:
        return DepthAnythingForDepthEstimation.from_pretrained(hf_dir)
    if not random:
        sys.exit("informe --hf-dir ou --random")
    torch.manual_seed(0)
    backbone = Dinov2Config(hidden_size=384, num_hidden_layers=12, num_attention_heads=6, image_size=518,
                            patch_size=14, out_features=["stage3", "stage6", "stage9", "stage12"],
                            apply_layernorm=True, reshape_hidden_states=False)
    cfg = DepthAnythingConfig(backbone_config=backbone, reassemble_hidden_size=384, patch_size=14,
                              neck_hidden_sizes=[48, 96, 192, 384], fusion_hidden_size=64,
                              depth_estimation_type="relative")
    return DepthAnythingForDepthEstimation(cfg)


def test_image(s):
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32) / s
    img = np.stack([0.3 + 0.4 * yy, 0.35 + 0.3 * np.sin(6 * xx) * yy, 0.5 - 0.3 * yy])
    blob = ((xx - 0.5) ** 2 + (yy - 0.55) ** 2) < 0.06
    img[:, blob] = np.array([0.8, 0.55, 0.45])[:, None]
    mean = np.array([0.485, 0.456, 0.406], np.float32)[:, None, None]
    std = np.array([0.229, 0.224, 0.225], np.float32)[:, None, None]
    return ((img - mean) / std).astype(np.float32)


def perspective_floor(s):
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32) / s
    horizon = 0.3
    img = np.zeros((3, s, s), np.float32)
    img[:] = np.array([0.65, 0.75, 0.9], np.float32)[:, None, None]  # céu/parede
    below = yy > horizon
    z = 1.0 / np.maximum(yy - horizon, 1e-3)                          # distância ao longo do piso
    u = (xx - 0.5) * z
    checker = ((np.floor(u * 4) + np.floor(z * 2)) % 2).astype(np.float32)
    shade = 0.35 + 0.4 * checker
    for c, k in enumerate((1.0, 0.9, 0.75)):
        img[c][below] = (shade * k)[below]
    mean = np.array([0.485, 0.456, 0.406], np.float32)[:, None, None]
    std = np.array([0.229, 0.224, 0.225], np.float32)[:, None, None]
    return ((img - mean) / std).astype(np.float32)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--hf-dir")
    ap.add_argument("--random", action="store_true")
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=392)
    a = ap.parse_args()
    assert a.size % 14 == 0
    model = Wrapper(build(a.hf_dir, a.random)).eval()

    os.makedirs(a.out, exist_ok=True)
    work = os.path.abspath(os.path.join(a.out, "_work"))
    os.makedirs(work, exist_ok=True)
    x = torch.rand(1, 3, a.size, a.size)
    import pnnx
    cwd = os.getcwd()
    os.chdir(work)
    try:
        pnnx.export(model, "depth.pt", x)
    finally:
        os.chdir(cwd)
    shutil.copy(os.path.join(work, "depth.ncnn.param"), os.path.join(a.out, "depth.param"))
    shutil.copy(os.path.join(work, "depth.ncnn.bin"), os.path.join(a.out, "depth.bin"))

    import ncnn
    img = test_image(a.size)
    net = ncnn.Net()
    net.opt.use_vulkan_compute = False
    net.load_param(os.path.join(a.out, "depth.param"))
    net.load_model(os.path.join(a.out, "depth.bin"))
    ex = net.create_extractor()
    ex.input("in0", ncnn.Mat(img).clone())
    _, out = ex.extract("out0")
    got = np.array(out).reshape(-1)
    with torch.no_grad():
        ref = model(torch.from_numpy(img)[None]).numpy().reshape(-1)
    corr = float(np.corrcoef(got, ref)[0, 1])
    span = float(np.percentile(ref, 99) - np.percentile(ref, 1)) or 1.0
    rel = float(np.sqrt(np.mean((got - ref) ** 2)) / span)
    print(f"ncnn × PyTorch: correlação {corr:.5f}, erro relativo {100 * rel:.2f}% da faixa, saída {got.size} valores")
    shutil.rmtree(work, ignore_errors=True)
    sane = True
    if a.hf_dir:
        # Sanidade com os pesos reais: piso quadriculado em perspectiva — a parte de baixo da
        # imagem está mais perto (profundidade inversa maior) que a de cima.
        floor = perspective_floor(a.size)
        ex = net.create_extractor()
        ex.input("in0", ncnn.Mat(floor).clone())
        _, fo = ex.extract("out0")
        f = np.array(fo).reshape(a.size, a.size)
        third = a.size // 3
        near, far = float(f[-third:].mean()), float(f[:third].mean())
        sane = near > far
        print(f"piso em perspectiva: baixo {near:.3f} × cima {far:.3f} ({'ok' if sane else 'INVERTIDO'})")
    if not (corr >= 0.995 and rel <= 0.03 and sane):
        for f in ("depth.param", "depth.bin"):
            os.remove(os.path.join(a.out, f))
        sys.exit("conversão reprovada: modelo removido")
    print("tamanho:", os.path.getsize(os.path.join(a.out, "depth.bin")) // 1024, "KiB")


if __name__ == "__main__":
    main()
