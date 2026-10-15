# Componentes de terceiros incluídos no APK

| Componente | Uso | Licença | Origem |
|---|---|---|---|
| CameraX / AndroidX | câmera, interface | Apache-2.0 | Google (Maven) |
| MediaPipe Tasks Vision 0.10.14 | segmentação do Retrato | Apache-2.0 | google-ai-edge/mediapipe |
| Modelo `selfie_multiclass_256x256.tflite` (assets) | pessoa, cabelo, pele, roupa, acessórios | Apache-2.0 (model card "Multiclass Segmentation") | storage.googleapis.com/mediapipe-models, sha256 `c6748b12…e0e0` |
| ncnn 20240820 (estático em `libnovadenoise.so`) | inferência local, Vulkan | BSD-3-Clause (+ componentes zlib/BSD listados no LICENSE do ncnn) | github.com/Tencent/ncnn, sha256 conferido no build |
| NAFNet-SIDD-width32 (quando incluído, `assets/nafnet`) | redução de ruído por IA, experimental | MIT (código e pesos publicados pelo repositório) | github.com/megvii-research/NAFNet; convertido no CI por `tools/nafnet/export_ncnn.py` |
| Depth Anything V2 Small (quando incluído, `assets/depth`) | profundidade relativa no Retrato | Apache-2.0 (só a versão Small; Base/Large/Giant são CC-BY-NC-4.0 e não são usadas; o CI confere a licença no model card antes de converter) | huggingface.co/depth-anything/Depth-Anything-V2-Small-hf; arquitetura da biblioteca transformers (Apache-2.0); convertido no CI por `tools/depth/export_ncnn.py` |

Nenhum código GPL/AGPL é incluído. PhotonCamera (eszdman) e MotionCam (GPL-3.0) foram usados só como
referência de algoritmo, sem cópia de código (ver OPEN_SOURCE.md).

A biblioteca MediaPipe traz um componente de telemetria de uso (datatransport); o app remove as
permissões de internet no manifesto, então nada é enviado.
