"""
Exporta o Real-ESRGAN "realesr-general-x4v3" (SRVGGNetCompact) para ncnn: detail.param / detail.bin.

Pesos oficiais: github.com/xinntao/Real-ESRGAN, release v0.2.5.0 (repositório BSD-3-Clause, Copyright (c) 2021
Xintao Wang). A arquitetura abaixo foi escrita a partir da descrição do artigo/código (64 canais, 32 convoluções,
PReLU, PixelShuffle 4×, soma da entrada ampliada por vizinho mais próximo).

Uso no app ("IA de detalhe"): a foto ampliada digitalmente (zoom acima da tele) é reduzida à metade, a rede
gera 4× e a média 2×2 no fim (dentro do grafo exportado) devolve o dobro: o tamanho da foto. A checagem de
consistência por bloco fica no app (core/imaging/DetailGate.kt).

Mistura dos dois pesos oficiais como no `inference_realesrgan.py` ("denoise strength"):
    w = s · x4v3 + (1 − s) · wdn-x4v3
s baixo preserva textura (a foto já passou pelo NAFNet quando tinha ruído).

Uso no CI:
    python export_ncnn.py --weights realesr-general-x4v3.pth --wdn realesr-general-wdn-x4v3.pth --out app/src/main/assets/detail
Depois da conversão, saída ncnn × PyTorch precisa concordar (PSNR ≥ 40 dB); senão o modelo não entra.
"""
import argparse
import os
import shutil
import sys

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F


class SRVGGNetCompact(nn.Module):
    def __init__(self, num_feat=64, num_conv=32, upscale=4):
        super().__init__()
        self.upscale = upscale
        body = [nn.Conv2d(3, num_feat, 3, 1, 1), nn.PReLU(num_parameters=num_feat)]
        for _ in range(num_conv):
            body += [nn.Conv2d(num_feat, num_feat, 3, 1, 1), nn.PReLU(num_parameters=num_feat)]
        body += [nn.Conv2d(num_feat, 3 * upscale * upscale, 3, 1, 1)]
        self.body = nn.ModuleList(body)
        self.upsampler = nn.PixelShuffle(upscale)

    def forward(self, x):
        out = x
        for layer in self.body:
            out = layer(out)
        out = self.upsampler(out)
        return out + F.interpolate(x, scale_factor=self.upscale, mode="nearest")


class Double(nn.Module):
    """Gera 4× e reduz pela média 2×2: saída com o dobro do lado da entrada (a foto reduzida à metade volta ao tamanho)."""

    def __init__(self, net):
        super().__init__()
        self.net = net

    def forward(self, x):
        return F.avg_pool2d(self.net(x), 2)


def load(path):
    sd = torch.load(path, map_location="cpu", weights_only=True)
    return sd.get("params_ema", sd.get("params", sd))


def build(weights, wdn=None, strength=0.3):
    sd = load(weights)
    if wdn:
        sd2 = load(wdn)
        sd = {k: strength * v + (1 - strength) * sd2[k] for k, v in sd.items()}
    net = SRVGGNetCompact()
    net.load_state_dict(sd, strict=True)
    return net.eval()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--wdn")
    ap.add_argument("--strength", type=float, default=0.3)
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=216)  # bloco do app: 192 + 2·12 de margem
    a = ap.parse_args()
    model = Double(build(a.weights, a.wdn, a.strength))
    os.makedirs(a.out, exist_ok=True)
    work = os.path.join(a.out, "_work")
    os.makedirs(work, exist_ok=True)
    x = torch.rand(1, 3, a.size, a.size)
    import pnnx
    cwd = os.getcwd()
    os.chdir(work)
    try:
        pnnx.export(model, "detail.pt", x)
    finally:
        os.chdir(cwd)
    shutil.copy(os.path.join(work, "detail.ncnn.param"), os.path.join(a.out, "detail.param"))
    shutil.copy(os.path.join(work, "detail.ncnn.bin"), os.path.join(a.out, "detail.bin"))
    shutil.rmtree(work, ignore_errors=True)

    # Verificação: ncnn × PyTorch numa cena com bordas e textura.
    import ncnn
    rng = np.random.default_rng(7)
    h, w = a.size, a.size
    yy, xx = np.mgrid[0:h, 0:w]
    img = (0.5 + 0.3 * np.sin(xx / 3.0) * np.cos(yy / 5.0) + 0.05 * rng.standard_normal((h, w))).clip(0, 1)
    img = np.stack([img, img * 0.9, img * 0.8]).astype(np.float32)
    with torch.no_grad():
        ref = model(torch.from_numpy(img)[None])[0].numpy()
    net = ncnn.Net()
    net.load_param(os.path.join(a.out, "detail.param"))
    net.load_model(os.path.join(a.out, "detail.bin"))
    ex = net.create_extractor()
    ex.input("in0", ncnn.Mat(img).clone())
    _, out = ex.extract("out0")
    got = np.array(out)
    mse = float(np.mean((got - ref) ** 2))
    agree = 10 * np.log10(1.0 / max(mse, 1e-12))
    print(f"ncnn × PyTorch: PSNR {agree:.1f} dB, forma {got.shape}")
    if got.shape != ref.shape or agree < 40:
        print("REPROVADO: o modelo de detalhe não entra no APK", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
