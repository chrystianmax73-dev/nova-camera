# Pesquisa pública e decisões — Retrato com vídeo e energia

Fontes consultadas antes das mudanças,17/10/2026:

- [CameraX ImageAnalysis](https://github.com/androidx/androidx/blob/androidx-main/camera/camera-core/src/main/java/androidx/camera/core/ImageAnalysis.java): KEEP_ONLY_LATEST não enfileira todos os quadros; ImageProxy.close libera o quadro para o produtor. A inferência agora usa cópia própria e não retém esse lease.
- [MediaPipe MPImage](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/framework/image/MPImage.java) e [BitmapImageContainer](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/framework/image/BitmapImageContainer.java): fechamento decrementa referência e recicla Bitmap da imagem. Não fechar wrapper de Bitmap emprestado: entrada recebe cópia reduzida própria, fechada em finally; saídas fechadas após copiar planos. Testes exercitam essa posse com Bitmap real.
- [ImageSegmenter](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/tasks/vision/imagesegmenter/ImageSegmenter.java): caminho síncronoIMAGE permite liberar entrada/saídas após consumir resultado. Mantidos pesos e algoritmos existentes.

Decisões de produto: gestos comuns de câmeras (toque/foto, pressão/vídeo, direita/lock, vertical/zoom) implementados sobre controles Nova, ViewConfiguration e zoom já amortecido. Nenhum algoritmo/interface proprietário copiado, nenhum modelo/dependência novo.

Bateria primeiro: Vídeo comum nunca abre profundidade, cinema somente por intenção explícita do Retrato; perfilFHD30 separado com confirmação e sem sobrescrever4K60 comum. Liberação de quadros/modelos, cancelamento por geração, tempo de profundidade medido e budget de ocupação são mudanças verificáveis em código. Não são prova de autonomia/temperatura melhor. Sem reduzir FPS/resolução selecionados silenciosamente nem simular desfoque com overlay de tela apenas.

Evidência física disponível: arquivo pessoal0.24.6 analisado localmente apresentou1080×1920/AVC,29.92fps pelos PTS,508quadros,16.98s,marca horizontal. Diagnóstico posterior mostrou rebinds, máscara indisponível mesmo sem limitação térmica e consumo alto do aparelho. Não prova profundidade/rastreamento estável nem uma comparação energética controlada. Nenhuma mídia pessoal incluída neste documento/repositório.
