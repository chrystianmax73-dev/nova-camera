# Auditoria do pipeline — Nova Camera 0.13

Ponto de partida: 0.12.0. Classificação de cada pedido em **já existe**, **existe e precisa de
otimização** e **ausente**. As fotos Samsung e as referências vivo X300 Pro, Pixel 10 Pro e
iPhone 18 Pro servem para testes de qualidade; nenhum algoritmo proprietário é copiado (não há
acesso a eles).

## 1. Processamento automático integrado

| Item | Situação | Onde |
|---|---|---|
| Escolha automática do caminho na Foto (um quadro / HDR / noturno) a partir do 1º quadro real | Já existe | `AutoPhotoPolicy`, `NovaCamera.captureForProcessing` |
| HDR com fusão de exposições só onde coincidem + gainmap Ultra HDR | Já existe | `Engine.fuseInto`, `HdrGain`, `UltraHdr` |
| Redução de ruído multiquadro com alinhamento subpixel e rejeição de movimento | Já existe | `Engine.align`, `Engine.mergeInto` |
| Tom adaptativo com proteção contra piora (contraste, textura, preto, estouro) | Já existe | `Adaptive`, `AdaptiveImaging` |
| Contraste local de média escala no zoom (não cria detalhe) | Já existe | `ToneParams.clarity` |
| Recursos do fabricante (extensões Noturno/HDR/Bokeh do CameraX) com fallback para o motor Nova | Já existe | `ModeEngine.OEM_EXTENSION` |
| ZSL (disparo sem atraso) quando suportado | Já existe | captura `ZERO_SHUTTER_LAG` |
| Tele 5×/10× com pouca luz em um quadro só | **Ausente → feito na 0.13** | `AutoPhotoPolicy.Path.ZOOM`, `JobKind.ZOOM` |
| Nitidez fina artificial (unsharp) | Ausente **por decisão**: realça ruído e cria halo; o detalhe vem da fusão | — |
| Super-resolução multiquadro (detalhe além do sensor) | Ausente — ver §5 | — |

## 2. Pós-processamento em segundo plano

| Item | Situação |
|---|---|
| Fila de processamento: a câmera continua livre enquanto as fotos são finalizadas | Já existe (`ProcessingQueue`, até 3 na fila) |
| Uma imagem final por captura na galeria (originais desligados por padrão) | Já existe (`keepOriginals = false`) |
| IA (NAFNet) depois que a foto já está na galeria, trocando a imagem no lugar | Já existe (fila de refino) |
| IA pausada com bateria baixa fora do carregador | **Ausente → feito na 0.13** (`DenoisePolicy.LOW_BATTERY = 15%`) |

## 3. Noturno e selfies

| Item | Situação |
|---|---|
| Tom do Noturno | Mantido como na 0.12 (decisão do usuário na 0.13.1) |
| Quadros longos com ISO baixo pelo giroscópio (+0,5 EV) | Feito na 0.12, mantido |
| Preto firme pelo canal escuro (evita aspecto lavado sem clarear) | Feito na 0.12, mantido |
| Foco em pouca luz | **Depende do aparelho**: o AF contínuo do Samsung já é usado; forçar disparo de AF antes da rajada precisa de teste real (risco de atrasar o disparo) |
| Textura de pele, barba e cabelo | Já existe: sem suavização de pele; fusão não borra textura. A IA (NAFNet) é opcional e só no Noturno/Retrato |

## 4. Retrato

| Item | Situação |
|---|---|
| Segmentação MediaPipe multiclasse (cabelo/acessórios) + profundidade Depth Anything V2 Small | Já existe |
| Matte com filtro guiado em cor; objeto inteiro por Otsu/conectividade | Já existe |
| Desfoque ao vivo com prévia = foto, controle em tempo real | Já existe (0.10/0.11) |
| Folhas, cabelos, objetos transparentes | Precisa de **teste real**: o modelo de pessoa não conhece transparência; a profundidade ajuda em folhas. Sem fotos de teste, não há como calibrar aqui |

## 5. Zoom e vídeo

| Item | Situação |
|---|---|
| Curva de zoom temporizada, lente física ativa, transições sem flash | Já existe |
| Zoom suave no vídeo proporcional à ampliação; estabilização de prévia/vídeo do sistema | Já existe |
| Tele com pouca luz: 3 quadros alinhados (5× e 10×) | **Feito na 0.13** |
| 20×–100× | **Não implementado, de propósito.** Acima de 10× a 5× (50 MP) já está em recorte digital. Sem acesso ao modo de resolução máxima do sensor (remosaic) pelo Camera2 nesta tela, só restaria interpolação. Arquitetura prevista: super-resolução multiquadro sobre os mesmos quadros alinhados do `JobKind.ZOOM`, liberada só com teste real de legibilidade |

## 6. Modelos e aceleração

| Item | Situação |
|---|---|
| Modelos dentro do APK, sem download | Já existe: NAFNet e Depth Anything no APK (convertidos no CI), MediaPipe nos assets. O app **não tem permissão de internet** (`INTERNET` removida no manifesto) |
| GPU | Já existe: NAFNet e profundidade em ncnn/Vulkan; prévia do retrato em OpenGL ES |
| NPU | **Ausente.** NNAPI está obsoleto no Android 15; o acesso à NPU Qualcomm exige SDK proprietário (QNN). Fica para avaliação de licença antes de qualquer código |
| Segmentação MediaPipe na CPU | Precisa de **medição**: 256×256 a 10 Hz é leve; a GPU já está ocupada com a prévia. Sem número real, não troco |

## 7. Energia e temperatura

| Item | Situação |
|---|---|
| Política térmica (rajadas menores, IA pausada, prévia do desfoque pausada, vídeo limitado) | Já existe (`ThermalPolicy`, `DenoisePolicy`, `PortraitMaskAnalyzer`) |
| Câmera liberada por inatividade; análise do retrato só no Retrato; giroscópio só no Noturno | Já existe |
| Custo por foto (tempo, variação de temperatura) | Já existe (`CostLog`) |
| **Consumo real (mW), queda de bateria por hora, temperatura e folga térmica por atividade** | **Ausente → feito na 0.13** (`EnergyLog`, a cada 10 s, no Diagnóstico) |
| Efeito de prévia (OpenGL) ligado na Foto mesmo com Tom/Temperatura em zero | Precisa de **medição**: desligá-lo exige religar a câmera quando o controle sai do zero (pisca). Decido com o número do `EnergyLog`, não no escuro |

## 8. Pós-processamento de alta precisão

| Item | Situação |
|---|---|
| Alinhamento subpixel, fusão robusta, nenhum detalhe inventado | Já existe |
| Proteção contra halo (filtro guiado na camada-base, contraste local limitado) | Já existe |
| Seleção do quadro mais nítido como referência | Já existe no Noturno; **estendida à tele na 0.13** |

## O que depende de teste real (não verificável no CI)

- Noturno: quadros longos na mão e apoiado; tremido; foco no escuro.
- Tele 5×/10× com pouca luz: 3 quadros versus 1 (letras pequenas, texturas).
- Energia: sessão de 20–30 min fora do carregador, Diagnóstico → "Energia e temperatura da sessão".
- Retrato: folhas, cabelo contra o céu, copo/vidro.
