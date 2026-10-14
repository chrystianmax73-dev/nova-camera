"""
Exporta o NAFNet (denoise, SIDD width32) para ncnn: nafnet.param / nafnet.bin.

Arquitetura reproduzida de megvii-research/NAFNet (MIT License, Copyright (c) 2022 megvii-model),
com a LayerNorm2d reescrita em operações simples para a exportação (mesma matemática).

Uso no CI:
    python export_ncnn.py --weights NAFNet-SIDD-width32.pth --out app/src/main/assets/nafnet
Teste local do caminho de conversão (sem os pesos oficiais):
    python export_ncnn.py --random --out /tmp/nafnet

Depois da conversão, o resultado do ncnn é comparado com o do PyTorch na mesma entrada; se a
diferença passar do limite, o script falha (o modelo não entra no APK).
"""
import argparse
import os
import shutil
import sys

import numpy as np
import torch
import torch.nn as nn


class LayerNorm2d(nn.Module):
    def __init__(self, c, eps=1e-6):
        super().__init__()
        self.weight = nn.Parameter(torch.ones(c))
        self.bias = nn.Parameter(torch.zeros(c))
        self.eps = eps

    def forward(self, x):
        mu = x.mean(1, keepdim=True)
        var = (x - mu).pow(2).mean(1, keepdim=True)
        y = (x - mu) / torch.sqrt(var + self.eps)
        return self.weight.view(1, -1, 1, 1) * y + self.bias.view(1, -1, 1, 1)


class SimpleGate(nn.Module):
    def forward(self, x):
        x1, x2 = x.chunk(2, dim=1)
        return x1 * x2


class NAFBlock(nn.Module):
    def __init__(self, c, dw_expand=2, ffn_expand=2):
        super().__init__()
        dw = c * dw_expand
        self.conv1 = nn.Conv2d(c, dw, 1)
        self.conv2 = nn.Conv2d(dw, dw, 3, padding=1, groups=dw)
        self.conv3 = nn.Conv2d(dw // 2, c, 1)
        self.sca = nn.Sequential(nn.AdaptiveAvgPool2d(1), nn.Conv2d(dw // 2, dw // 2, 1))
        self.sg = SimpleGate()
        ffn = ffn_expand * c
        self.conv4 = nn.Conv2d(c, ffn, 1)
        self.conv5 = nn.Conv2d(ffn // 2, c, 1)
        self.norm1 = LayerNorm2d(c)
        self.norm2 = LayerNorm2d(c)
        self.beta = nn.Parameter(torch.zeros((1, c, 1, 1)))
        self.gamma = nn.Parameter(torch.zeros((1, c, 1, 1)))

    def forward(self, inp):
        x = self.norm1(inp)
        x = self.conv2(self.conv1(x))
        x = self.sg(x)
        x = x * self.sca(x)
        x = self.conv3(x)
        y = inp + x * self.beta
        x = self.conv5(self.sg(self.conv4(self.norm2(y))))
        return y + x * self.gamma


class NAFNet(nn.Module):
    def __init__(self, width=32, middle_blk_num=12, enc_blk_nums=(2, 2, 4, 8), dec_blk_nums=(2, 2, 2, 2)):
        super().__init__()
        self.intro = nn.Conv2d(3, width, 3, padding=1)
        self.ending = nn.Conv2d(width, 3, 3, padding=1)
        self.encoders, self.decoders, self.ups, self.downs = nn.ModuleList(), nn.ModuleList(), nn.ModuleList(), nn.ModuleList()
        chan = width
        for num in enc_blk_nums:
            self.encoders.append(nn.Sequential(*[NAFBlock(chan) for _ in range(num)]))
            self.downs.append(nn.Conv2d(chan, 2 * chan, 2, 2))
            chan *= 2
        self.middle_blks = nn.Sequential(*[NAFBlock(chan) for _ in range(middle_blk_num)])
        for num in dec_blk_nums:
            self.ups.append(nn.Sequential(nn.Conv2d(chan, chan * 2, 1, bias=False), nn.PixelShuffle(2)))
            chan //= 2
            self.decoders.append(nn.Sequential(*[NAFBlock(chan) for _ in range(num)]))

    def forward(self, inp):
        # Entrada com lados múltiplos de 16 (o app manda blocos de 288×288): sem preenchimento.
        x = self.intro(inp)
        encs = []
        for encoder, down in zip(self.encoders, self.downs):
            x = encoder(x)
            encs.append(x)
            x = down(x)
        x = self.middle_blks(x)
        for decoder, up, skip in zip(self.decoders, self.ups, encs[::-1]):
            x = decoder(up(x) + skip)
        return self.ending(x) + inp


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights")
    ap.add_argument("--random", action="store_true")
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=288)
    ap.add_argument("--tol", type=float, default=2e-2)
    a = ap.parse_args()

    model = NAFNet()
    if a.weights:
        ckpt = torch.load(a.weights, map_location="cpu")
        state = ckpt.get("params", ckpt)
        missing, unexpected = model.load_state_dict(state, strict=True), None
    elif not a.random:
        sys.exit("informe --weights ou --random")
    else:
        torch.manual_seed(0)
        for p in model.parameters():
            nn.init.normal_(p, std=0.02)
    model.eval()

    work = os.path.abspath(os.path.join(a.out, "_work"))
    os.makedirs(work, exist_ok=True)
    x = torch.rand(1, 3, a.size, a.size)
    import pnnx
    cwd = os.getcwd()
    os.chdir(work)
    try:
        pnnx.export(model, "nafnet.pt", x)
    finally:
        os.chdir(cwd)
    shutil.copy(os.path.join(work, "nafnet.ncnn.param"), os.path.join(a.out, "nafnet.param"))
    shutil.copy(os.path.join(work, "nafnet.ncnn.bin"), os.path.join(a.out, "nafnet.bin"))

    # Verificação numérica: ncnn (fp32, CPU) × PyTorch na mesma entrada.
    import ncnn
    net = ncnn.Net()
    net.opt.use_vulkan_compute = False
    net.load_param(os.path.join(a.out, "nafnet.param"))
    net.load_model(os.path.join(a.out, "nafnet.bin"))
    ex = net.create_extractor()
    ex.input("in0", ncnn.Mat(x.squeeze(0).numpy()).clone())
    _, out = ex.extract("out0")
    got = np.array(out)
    with torch.no_grad():
        ref = model(x).squeeze(0).numpy()
    diff = float(np.abs(got - ref).max())
    print(f"diferença máxima ncnn × PyTorch: {diff:.5f}")
    shutil.rmtree(work, ignore_errors=True)
    if diff > a.tol:
        for f in ("nafnet.param", "nafnet.bin"):
            os.remove(os.path.join(a.out, f))
        sys.exit(f"conversão divergente ({diff} > {a.tol}): modelo removido")


if __name__ == "__main__":
    main()
