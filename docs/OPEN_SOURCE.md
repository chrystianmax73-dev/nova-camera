# Avaliação de tecnologias open-source — Nova Camera 0.7

> **Atualização (0.16.1): o Nova passou a ser GPL-3.0-or-later.** Código GPL-3.0 (PhotonCamera da
> eszdman, MotionCam) agora pode ser aproveitado com atribuição, não só estudado. Continuam proibidos:
> código proprietário (ex.: Adobe, GCam) e pesos com licença não comercial (ex.: Depth Anything
> Base/Large, CC-BY-NC).

Critério (até a 0.16): só entra no app o que (1) tem licença compatível com um APK fechado e distribuído,
(2) roda offline no Snapdragon 8 Gen 3 / 8 Elite, (3) **não inventa detalhe** e (4) mostra ganho
medido sobre o motor atual. Licenças conferidas nos arquivos LICENSE/README de cada repositório e
nos model cards da MediaPipe em 14/10/2026; o que não pôde ser conferido está marcado.

| Projeto | Licença do código | Pesos/modelos | Uso na Nova | Decisão |
|---|---|---|---|---|
| PhotonCamera (eszdman) | **GPL-3.0** | — | Referência de algoritmo (burst RAW, alinhamento, merge) | Só estudo. Copiar código obrigaria o APK inteiro a ser GPL. |
| PhotonCamera (bjzhou) | Apache-2.0, **mas contém C++ GPL-3.0 (spektrafilm) e assets CC-BY-SA** | dncnn/blazeface/midas: não verificado | Idem | Só estudo; qualquer trecho exige auditoria de licença por arquivo. |
| HDR+ (timothybrooks) | MIT | — | Referência do align+merge em RAW (Halide) | Base do próximo passo de qualidade (fusão em RAW, subpixel). |
| Google HDRNet | Apache-2.0 | não declarada | Tom aprendido por grade bilateral | Não: TF1, sem Android, parado desde 2023. |
| OpenCV | Apache-2.0 | — | Alinhamento ECC/rotação, filtros | Candidato se o alinhamento por rotação precisar; +~10 MB. |
| MediaPipe | Apache-2.0 | selfie_multiclass_256x256 e selfie segmentation: **Apache-2.0** (model cards) | Segmentação com classe **cabelo** separada | **Próximo para o Retrato**: protege cabelo/barba melhor que a máscara única atual. |
| MODNet | Apache-2.0 | Apache-2.0 | Matting de retrato | Possível, mas projeto parado (2022) e sem Android; exige conversão para ncnn. |
| Depth Anything V2 | Apache-2.0 | **Small: Apache-2.0; Base/Large/Giant: CC-BY-NC-4.0 (proibido em produto)** | Profundidade para desfoque gradual | Só o Small, e só depois de medir latência no aparelho. |
| Tencent ncnn | BSD-3-Clause | — | Runtime de inferência ARM/Vulkan | **Escolhido** para qualquer rede futura (NAFNet, Depth Small) — mais leve que TFLite+GPU delegate para CNNs. |
| Halide | MIT | — | Gerar kernels rápidos para merge/tom | Adiar até medir o tempo atual no aparelho; exige pipeline NDK. |
| NAFNet | MIT | não declarada (vale MIT por inferência, não confirmado) | Redução de ruído de quadro único sem GAN | Candidato para selfie/noite em quadro único, via ncnn, se superar o atual em teste A/B. |
| Real-ESRGAN ncnn-vulkan | MIT | não verificado | Super-resolução com GAN | **Rejeitado**: gera detalhe sintético (letras/texturas inventadas) — contraria o requisito. |
| GrapheneOS Camera | MIT | — | Arquitetura CameraX enxuta | Referência de organização. |
| MotionCam (f0enix) | **GPL-3.0** | — | Pipeline RAW + Halide | Só estudo; parado desde 2021. |

## O que entrou na 0.9.5

Depth Anything V2 **Small** (Apache-2.0) via ncnn no Retrato: desfoque proporcional à distância ao plano de foco e retrato de objetos sem pessoa. Convertido e conferido no CI; tempo e qualidade no aparelho ainda sem medição.

## O que entrou na 0.8

MediaPipe Tasks + modelo multiclasse (Retrato), ncnn (runtime de IA com Vulkan) e NAFNet (redução de ruído, experimental). Detalhes e validação em STATUS.md; licenças em THIRD_PARTY.md.

## O que entrou na 0.7

Nenhum código de terceiros novo. As correções da 0.7 (fusão segura, tom adaptativo) foram
reimplementadas no núcleo próprio, inspiradas nas ideias publicadas do HDR+ (fusão robusta, rejeição
por pixel) — sem copiar código GPL. Motivo: os defeitos comprovados eram de **lógica** (alinhamento
sem verificação, tom forte e uniforme), não de falta de uma biblioteca; e nenhuma rede neural tem
ganho medido no S24 Ultra ainda.

## Ordem proposta (cada passo só entra com A/B medido no aparelho)

1. MediaPipe `selfie_multiclass` (Apache-2.0) no Retrato: proteger a classe cabelo/rosto da máscara.
2. Fusão em RAW (DNG/RAW_SENSOR) com alinhamento subpixel, no estilo HDR+ — maior ganho real
   possível em noite e zoom; depende de o S24U/S25U entregar RAW na lente usada.
3. ncnn + NAFNet (quadro único) para selfie/noite, se superar a fusão atual sem plastificar.
4. Halide para acelerar alinhamento/merge, se o tempo medido no aparelho passar de ~1,5 s.
