# Etapa 0.36.2 (Claude) — vídeo tele no escuro
- **Feito:** `VideoTeleLight` segura 4,9× na 3× com pouca luz (via `lightZoom` em `sendZoom`, no bind e em `watchInitialZoom`).
- **Proposta pendente, a decidir com o dono — "Vídeo noturno":**
  - Ganho digital +1–2 EV, curva de sombras e denoise temporal recursivo no GPU (kmax 0,6 luma / 0,8 croma, máscara de movimento 4×4, histórico 16F).
  - Exige desligar HDR/HLG nessa situação.
  - Resolveria o 10× e o 5× sem perder a tele.
- **A medir:** `CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE` e `TONEMAP_AVAILABLE_TONE_MAP_MODES` por lente.

# Etapa 0.36.1 (Claude)
- **Contraluz:** o clareamento de pessoa (`BacklitLift.plan`) exige rosto ≥ 0,3%. O segmentador multiclasse marca pano e sofá como roupa; não tirar essa trava.
- **Retrato:** objeto em contraluz via `planSubject` + `saveBacklit`, que o `render()` já aplica antes do desfoque. `OBJECT_RATIO` 0,38 foi calibrado em uma garrafa; calibrar com mais fotos reais.

# Etapa 0.36.0 (Claude) — retorno dos testes da 0.35 (detalhes em `docs/STATUS.md`)
- **Pesquisa usada:**
  - **Pouca luz:** Night Sight com quadros longos e mais quadros, preto no p0,5–1%, saturação 1,1–1,3 com o ganho.
  - **Contraluz:** rosto 0,45–0,55 e até +1,5–2 EV linear, com mapa de ganho sem halo.
  - **Retrato:** CoC linear na disparidade, R_max ≈ 2% da largura, desfoque em luz linear com oclusão (já existia), fundo −0,2 a −0,35 EV.
  - **Cena:** EfficientNet-Lite0 do MediaPipe (Apache-2.0 nos metadados). Places365 e AIY evitados: licença dos pesos não confirmada.
- **Não feito, de propósito:**
  - Saturação no Noturno: o tom do Noturno é decisão do dono.
  - Exposição por quadro mais longa na tele: `NightExposure.maxFrameNs`, risco de tremido sem teste físico.
  - Clareamento extra dos olhos: precisa de face mesh.
- **Próximo passo, se o dono pedir:** depois de confirmar o "N de 8" no aparelho, testar quadros mais longos na tele com o celular apoiado.
- **Cena:**
  - `SceneWatcher` só roda na FOTO, ~1×/s, e pula a leitura se outra IA nativa estiver ocupada.
  - A cena vai no `Job.scene` → meta `scene` → `render()` ("acabamento/cena"). Só planta e comida mudam a cor.
  - Exceção: o caminho de 200 MP em faixas (`JpegBands`) não aplica a cena.

# Etapa 0.35.0 (Claude) — bug dos controles ao vivo + itens 1, 2, 3, 6 da pesquisa (detalhes em `docs/STATUS.md`)
- **Bug dos controles ao vivo, corrigido:**
  - A causa era `portraitPreviewFailed`, permanente na sessão.
  - Agora é `EffectBreaker` (4 s → 20 s → 120 s, janela de 180 s). Em `NovaCamera.pauseEffect()`, ele religa via `bind()` passada a espera.
  - **Não voltar a usar flag permanente.** Se o efeito falhar em loop, o disjuntor já espaça as tentativas.
- **Detector:** `tools/picodet/export_ncnn.py` só copia para assets depois de aprovar. Manter as referências explícitas dos `ncnn.Mat` no Python: temporários dão lixo.
- **Volume:** + dispara no aperto; − dispara ao soltar ou trava AE/AF segurando 0,35 s (`VOLUME_HOLD_MS`).
- **Próximos:** 0.36 com Ultra HDR, estilos 2D com subtom, distorção + macro. 0.37 com borracha (MI-GAN MIT + MobileSAM Apache), melhor rosto (blendshapes), céu (U²-Net: conferir licença dos pesos antes).

# Retorno do dono sobre a 0.31.6 → tratado na 0.32.1 (ver `docs/STATUS.md`)
- Pendente só o que depende do aparelho: confirmar a galeria atualizando, o custo do QR duplo e do botão de texto no S24, e o deslocamento do próximo fechamento nativo, se houver.

# Etapa 0.32.0 (Claude) — galeria no app, palma, IA de detalhe (pedido do dono)
- Detalhes em `docs/STATUS.md` (0.32.0).
- `runNextRefine` foi reorganizado: o NAFNet dispensado ou recusado não encerra mais o refino, porque a IA de detalhe pode entrar depois. Estado na sessão: `detail`=done/skipped e `detailReport`. `Session.meta` agora guarda `srFactor`, e o caminho AUTO guarda `zoom`.
- Os limiares da IA de detalhe (`DetailGate.LO/HI`) foram calibrados em 3 fotos de 10×. Precisam de mais fotos reais (texto, rosto de longe, folhagem) antes de ampliar o uso.

# Etapas 0.30.2–0.31.6 (Claude) — IA automática, estabilidade, ferramentas da câmera (pedido do dono)
- Detalhes, limites e tabela "mudou × não mexi" em `docs/STATUS.md` (0.30.2 a 0.31.6). Noturno, tom, vídeo e Retrato não foram tocados nessas etapas, salvo onde o STATUS diz.
- **IA automática:** `core/AiAutoPolicy.kt` (zoom ≥ 1,3×, ISO ≥ 800, Noturno, Retrato, até 52 MP). `core/imaging/AiQuality.kt` mede antes, escolhe a força e valida depois: recusa se o ruído não cair ≥ 10% ou se a borda perder > 8%. `HalfResidual` cobre fotos acima de 16,5 MP. `DenoiseBands` processa em faixas.
- **Estabilidade:** `NativeGate` (uma IA nativa por vez; a prévia ao vivo pula o quadro em vez de esperar), `OutputMemory` (RAM do sistema, não heap), `NativeTombstone`.
- **Atenção ao assunto:** `core/imaging/SubjectAttention.kt` (sombra com contorno, ou o ponto tocado, até +0,8 EV com validação). Vale só na FOTO com ISO ≥ 800 e é reaplicada pela revisão.
- **Ferramentas:**
  - QR: `camera/QrScanner.kt` com zxing-cpp.
  - Texto: `processing/TextReader.kt` + `cpp/ocr.cpp` com PP-OCRv5; modelos baixados no build com sha256 fixo.
  - Documento: `core/DocQuad.kt`.
  - Lente suja: `core/LensSmudge.kt`.
  - Local: `core/GeoTag.kt`, desligado por padrão; `Job.geo` copia a posição no disparo.
- **CI:** cache do NAFNet convertido (o Google Drive pode recusar o download). Não remover a verificação final que barra APK sem modelo.

# Etapa 0.30.1 (Claude) — vídeo travado no rosto/assunto + IA por região e foto inteira (pedido do dono)
- Detalhes e limites em `docs/STATUS.md` (0.30.1). Para o Codex: `TileNoise.CLEAN_SIGMA` (1,2) é um palpite sem medição — calibrar com o "limpos pulados" do diagnóstico em fotos reais de Noturno e Retrato. A força da rede (0,75) e o acabamento não mudaram.
- Pontos de integração: `ProcessingQueue.render(..., plans)` registra/reaplica os planos tonais (`RenderPlans`); `Engine.toneInPlace(..., region)`; `Engine.lowLightPlan`/`profilePlan` separados de `lowLightInPlace`/`profileInPlace` (saída idêntica). Mudou o tom? Mantenha o registro/reaplicação em todo passo com estatística global, senão a prévia por região diverge (`RegionRenderTest` pega).
- Rastreio: `SubjectTracker` com adaptação presa ao modelo do toque e busca ampla quando incerto; `SubjectTrackingSession` aceita `faces` (rostos do sensor em coordenadas da prévia, `NovaCamera.facesInView`).

# Etapa 0.29.7 (Claude) — auditoria do Noturno em zoom: ler `docs/pendente/AUDITORIA_NOTURNO_ZOOM.md`
- Codex: propostas de qualidade A–D lá (mais quadros na tele, YUV, crominância pelo ruído medido, RAW). 0.29.2–0.29.6 mexeram em `Merge`, `Consistency`, `Engine.align/mergeInto/superRes`, `NightLensPolicy` (pedido do dono enquanto o teu plano estava encerrado) — revisar antes de mudar a fusão.
- `Engine.mergeInto` agora paralelo (imagem idêntica, teste pixel a pixel); `readAligned` = `fetchAligned` + `finishAligned`.

# Etapa 0.29.2 (Claude) — qualidade de imagem passou para o Claude (plano do Codex encerrado)
- **Fusão (Noturno/zoom):** sigma da fusão e da verificação de confiabilidade agora = máx(espacial, medido entre quadros alinhados). Tocados: `Merge.temporalNoise`, `Consistency.pairNoise`, `Engine.align` (`Alignment.pairNoise`), `Engine.mergeInto` (`onNoise` virou String). Motivo e números em STATUS 0.29.2. Tetos: 8 níveis / 0,025.
- **Cinema:** pessoa só vira assunto no vídeo com ≥12% do quadro ou sob o toque (`CinematicPolicy.personSubject`).

# Etapa 0.29.0 (Claude) — Nova Live, branch `claude/nova-interface`
- Novos (meus): `core/MotionPhoto.kt`, `camera/LiveRecorder.kt`, `processing/LiveClips.kt`. Tocados: `MediaWriter.write` (lock + reanexo depois do EXIF), `ProcessingQueue` (`Job.liveClip`, `finish`, reedição em faixas), `NovaCamera` (análise YUV só na Foto com Nova Live ligado; recuo de bind; clipe por disparo). **Pixels da foto inalterados**: o vídeo vai depois do fim do JPEG.
- Codex: se o Ultra HDR passar a ser gerado em mais fotos, o Nova Live some nelas (recusa MPF). Juntar os dois exige um Container com GainMap + MotionPhoto (formato do Pixel) — próximo passo, se quiser.

# Etapa 0.28.2 (Claude) — Cinema estilo iPhone, branch `claude/nova-interface`
- `core/CinematicPolicy.kt` (`CinematicSceneGuard.selectionOnly`), `camera/PortraitMaskAnalyzer.kt` (rack focus só no cinematográfico), `NovaCamera.publishFaces/cinemaFace` (rosto como alvo sem toque). Sem mudança de pixels fora do vídeo cinematográfico.

# Etapa 0.28.1 (Claude) — Cinema e 4K/60, branch `claude/nova-interface`
- `camera/PortraitMaskAnalyzer.kt` (área compartilhada): só no `cinematic`, profundidade assíncrona (`depthExecutor`, `depthDone`, `depthGeneration`); o caminho da foto/Retrato ao vivo não mudou. `DepthDutyBudget` com campos voláteis (lido/escrito em duas threads).
- `NovaCamera`: recusa definitiva de fps troca para a maior taxa aceita. `MainActivity.startVideo`: espera de máscara no CINEMA (`cinemaWarmup`, 4 s).

# Etapa 0.28.0 (Claude) — pendentes da reformulação, branch `claude/nova-interface`
- Arquivos meus nesta etapa: `core/ContourMask.kt`, `processing/ContourSegmenter.kt`, `assets/magic_touch.tflite`, `ui/LiveControls.kt` (rótulo f/), `ui/ViewfinderOverlay.kt` (contorno), modo CINEMA (`core/Modes.kt`, `NovaCamera.setMode/cinemaMode`), HDR de vídeo e recusa 4K/60 (`NovaCamera` bloco de vídeo, `NovaSettings.videoRefused/videoHdr`).
- **Toca em imagem (atenção Codex):** (1) `autoVideoFps` agora 15–60 a 60 fps no escuro; (2) "Fundo escuro" do Retrato multiplica o fundo na foto final (depois do desfoque, antes do estilo). Ambos opcionais/ajustáveis; nada mudou no Noturno, tom, assinatura ou processamento da Foto.
- Corrigido bug do `onTrimMemory` (UI_HIDDEN cancelava acabamento do vídeo).

# Etapa 0.27.4 (Claude) — zoom do disparador em degraus, branch `claude/nova-interface`
- `core/ShutterZoom.kt` (meu): arraste do disparador anda em degraus da escada (paradas + inteiros), cada degrau via `NovaCamera.zoomTo(z, allowLensSwitch = false)`. `MainActivity` (onZoomStart/onZoom do disparador) só chama isso. Barra/pinça/`zoomFollow` inalterados. Sem efeito em imagem.

# Etapa 0.27.2 (Claude) — confiabilidade e interface, branch `claude/nova-interface`

**Codex: ao trabalhar em qualidade de imagem, estas peças são minhas e não precisam ser refeitas; se precisar mudá-las, me avise pelo handoff.**
- `processing/ProcessingService.kt` (novo) e, em `ProcessingQueue`: `ProcessingService.start/stop` em `submit`, marca `interrupted` por sessão (`run` cria, `submit` apaga), `recoverInterrupted`, `previewOf` + executor `thumbs`, campo `JobProgress.preview`, `session.save()` logo após EXIF/zoom/assinatura. **Nada disso altera pixels**: o pipeline de imagem (`Engine`, `render`, fusão, retrato, NAFNet) não foi tocado.
- `core/ShutterZoom.kt`, `ui/ShutterButton.kt` (arraste = zoom), `ui/SmallViews.kt` (anel na miniatura), `ui/ViewfinderOverlay.kt` (rostos), `NovaCamera.applyInterop` (STATISTICS_FACE_DETECT_MODE) e `publishFaces`, `settings.showFaces`.
- **Vigia da prévia (`NovaCamera`): marca `visible`.** Era a provável causa raiz do P0 da prévia (ida à galeria tratada como travamento → escalada que desligava Tom/Temperatura). A escalada em `onPreviewStalled` continua como estava; se ainda houver paradas com o app visível, o StallLog dirá.
- **0.27.3:** `ZoomRequestGate(maxWaitMs)` + `sequence`; no vídeo `NovaCamera` usa 33 ms (`VIDEO_ZOOM_REQUEST_MS`). Se mexer no zoom do vídeo, manter o número de sequência (callbacks de pedidos substituídos chegam cancelados).
- Se o pipeline ganhar uma etapa nova que demore, a marca `interrupted` já cobre (é por sessão, não por etapa).

---

# Aviso (Claude, 18/10) — reformulação visual em branch próprio: `claude/nova-interface`

- O dono aprovou a nova interface; a etapa 1 (topo enxuto, painel Ferramentas, faixa Retrato · Foto · Vídeo · Noturno · Pro) está no branch `claude/nova-interface` (PR #6 contra este branch, rascunho), APK de teste build84.
- **Enquanto a reformulação durar, evite mexer em `MainActivity.kt` (montagem da tela: `buildInterface`, `layoutAll`, `updateTopBar`, faixa de modos) e em `ui/`.** Câmera, processamento, modelos e vídeo seguem aqui; trago para o branch da interface por merge.
- Detalhes da etapa: STATUS/handoff do branch `claude/nova-interface`.
# Etapa 0.27.0 (Claude) — Interface Nova, etapa 1, no branch `claude/nova-interface`; APK de teste build84 entregue

- **[Baixar ZIP com app-release.apk — build84 (1084)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39159177607/artifacts/12148223515)**. Mesma assinatura permanente. Artefato 12148223515, 132857618 bytes; digest do ZIP sha256:46b0487c4cd3379eeeac2102611db00f86a688f78b4bda6a46a9d01fa0c6f070; expira 2026-11-17T19:40:18Z. [Actions 39159177607](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39159177607) com sucesso; **publicação pulada** (condição do passo estendida a este branch só no commit do gatilho f5180f3; o run 83 sem essa condição foi cancelado antes de publicar — nenhuma release nova). Gatilho restaurado em eba9f72 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
**Codex: a reformulação visual acontece neste branch (pedido do dono). Enquanto ela durar, evite mexer em `MainActivity.kt` (montagem da tela: `buildInterface`, `layoutAll`, `updateTopBar`, faixa de modos) e em `ui/`; mudanças de câmera/processamento seguem no teu branch e eu trago por merge.**
- Arquivos: `MainActivity.kt` (topo, `showTools`/`hideTools`, `PRIMARY_MODES` explícito), `ui/ToolsPanel.kt` (novo), `ui/NovaIconView.kt` (ícones TOOLS/MODES), `core/Modes.kt` (Noturno e Pro primários), teste de render.
- Nenhuma mudança de captura, processamento, modelos ou vídeo.

---

# Etapa 0.26.15 (Claude, autorizado pelo dono) — P0 "prévia trava ao abrir o Retrato": primeira correção; APK de teste build82 entregue

- **[Baixar ZIP com app-release.apk — build82 (1082)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39155946626/artifacts/12147286475)**. Mesma assinatura permanente. Artefato 12147286475, 132854397 bytes; digest do ZIP sha256:8b276082e602fc8b140d790b5ea2a86827df71dcc80da4a0bc672b46eab7aaee; expira 2026-11-17T18:57:49Z. [Actions 39155946626](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39155946626) com sucesso; publicação skipped. Fonte do app 42f2f4e (CI 39154941357 aprovado) + docs; fonte do APK 6d1140d; gatilho restaurado em 05990ca (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
**Codex: esta frente era tua (P0). O dono pediu que eu atacasse e registrasse tudo aqui.**
- Arquivos tocados: `core/ModelLoadWindow.kt` (novo), `processing/DepthEstimator.kt` (`Session.open` marca begin/end), `camera/NovaCamera.kt` (`watchTick`: ramo `ModelLoadWindow.defersStall` antes de contar a parada; linha do StallLog com `ModelLoadWindow.describe`), `CameraDiagnostics.kt` (linha de relatório), teste `ModelLoadWindowTest`.
- **Não mexi** na escalada (`onPreviewStalled`: 1ª parada com análise → `liveBlurBlocked`; 2ª em 60 s com efeito → `portraitPreviewFailed`, que some com Tom/Temperatura até reabrir o app). Se a hipótese da carga for descartada pelo diagnóstico, a próxima suspeita é o próprio efeito GL/rebind; aí vale rever essa escalada (ex.: não derrubar Tom/Temperatura por parada que ocorreu com a análise ativa).
- Hipótese, evidência e como ler o diagnóstico: ver STATUS 0.26.15.
- **Resultado do teste (dono, build82):** hipótese da carga do modelo **descartada** (0 s sem quadros durante a carga; traseira sem paradas ao abrir/trocar). As paradas que restam são na **câmera frontal**, logo após Retratos frontais, com efeito=false análise=false. **Codex:** se pegar esta frente, começar por: tempo desde a última captura no StallLog; testar se a frontal retoma a repetição da prévia após `takePicture` (ZSL/`CAPTURE_MODE_MINIMIZE_LATENCY` na frontal?); e rever a escalada que desliga Tom/Temperatura.
- Ao mexer em qualidade de imagem no Retrato: a profundidade é **um modelo só** compartilhado (0.26.12, `DepthEstimator.shared`); a prévia com pessoa não usa profundidade (0.26.14, `LivePortraitPolicy`); nada disso altera a foto final.

---

# Etapa 0.26.14 (Claude) — prévia só-pessoa, pouca energia, IA sem carga inútil; APK de teste build81 entregue

- **[Baixar ZIP com app-release.apk — build81 (1081)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39154343920/artifacts/12146122862)**. Mesma assinatura permanente. Artefato 12146122862, 132852886 bytes; digest do ZIP sha256:d27f7797728786f1fc606c3537afcdccd66d805b4421119a9cb3068553acf2b5; expira 2026-11-17T18:35:18Z. [Actions 39154343920](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39154343920) com sucesso; publicação skipped. Fonte do app 6fd2815 (CI 39154336359 aprovado); fonte do APK a41ba57; gatilho restaurado em dd465f1 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- `core/LivePortraitPolicy` (+ `PortraitMaskAnalyzer`, só `!cinematic`), `core/DenoiseSkip` (+ `NcnnDenoiser`), `DepthRefreshPolicy.needed(maxAgeMs)`. Testes `LivePortraitPolicyTest`.
- **Fim da frente de bateria do Claude** salvo regressão: o que resta (~46 pontos com desfoque ao vivo) é estrutural — efeito GL + ImageAnalysis 640×480 RGBA a 30 fps + MediaPipe + binder. Reduzir exige juntar análise e desenho (ex.: amostrar a textura da prévia na GPU em vez de um ImageAnalysis separado). Núcleo da câmera = área do Codex/redesign; não mexi.
- **Codex:** atualizar ncnn (build usa 20240820; versões 2026 reduzem queda para CPU em camadas de transformer no Vulkan) — afeta profundidade da foto e NAFNet; fazer em versão própria com A/B.

---

# Etapa 0.26.13 (Claude) — blocktime OpenMP por inferência; APK de teste build80 entregue

- **[Baixar ZIP com app-release.apk — build80 (1080)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39149596567/artifacts/12144698246)**. Mesma assinatura permanente. Artefato 12144698246, 132851566 bytes; digest do ZIP sha256:9eb9d15e885b17e519332438404364310eba65d6b97ab47b06ec2af8a986ad1b; expira 2026-11-17T17:34:28Z. [Actions 39149596567](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39149596567) com sucesso; publicação skipped. Fonte do app ba4abcc (CI 39149582242 aprovado); fonte do APK 70bd341; gatilho restaurado em e2cd0a9 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- `depth.cpp`/`denoise.cpp`: `ncnn::set_kmp_blocktime(0)` também em `nativeRun` (vale por thread chamadora; sessão de profundidade agora é usada por várias threads).
- **Codex (tempo do Retrato, diagnóstico 0.26.12, não alterado por mim):** "acabamento/pouca luz" 1024 ms na foto ISO 1282 vs 105 ms na ISO 1883 (inverso do esperado); "perfil" ~1,1 s e "desfoque" ~1,4 s; ~360–410 ms sem marca entre o fim do refino de máscara e o início do acabamento (provável gravação de matte/mapa de desfoque/sessão).
- Prévia ainda trava ao abrir o Retrato em 0.26.12 (StallLog 14:19:47 efeito=true análise=false; "+6007 ms prévia sem resultados; reabrindo") — P0 do Codex segue aberto.

---

# Etapa 0.26.12 (Claude) — sessão de profundidade compartilhada prévia/foto; APK de teste build79 entregue (inclui 0.26.11)

- **[Baixar ZIP com app-release.apk — build79 (1079)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39147041905/artifacts/12143679020)**. Mesma assinatura permanente. Artefato 12143679020, 132851523 bytes; digest do ZIP sha256:3d9c5824e18963d0a6e7c6fdf3e9fdf8c78e405f4dcfdaf08b2ba980c49c3433; expira 2026-11-17T17:00:34Z. [Actions 39147041905](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39147041905) com sucesso; publicação skipped. Fonte do app 67fc611 (CI 39146832594 aprovado; 0.26.11 c4fd20f CI 39146478679 aprovado); fonte do APK 698a9a6; gatilho restaurado em db184b2 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- `DepthEstimator.shared()`; `PortraitMaskAnalyzer` não abre nem fecha sessão própria (calor SEVERO → `DepthEstimator.release()`; `close()` → `ProcessingQueue.scheduleDepthRelease()`). Motivo: 1º Retrato com prévia ligada pagava 3,3 s de carga e o modelo ficava duplicado na memória (diagnóstico 0.26.10).
- **Codex:** se mexer na profundidade da prévia/cinematográfico, não volte a usar `Session.open` direto no analisador; use `DepthEstimator.shared`.

---

# Etapa 0.26.11 (Claude) — profundidade ao vivo só com cena nova; fonte em validação

- `core/DepthRefreshPolicy` + `PortraitMaskAnalyzer` (só `!cinematic`): recalcula profundidade se luminância por bloco mudou > 0,045 desde o último cálculo, se o toque mudou (> 0,04) ou após 15 s. Motivo medido: profundidade era ~23% de um núcleo com a câmera parada (diagnóstico 0.26.10).
- Medições de bateria até aqui (Retrato parado com desfoque): 128% (0.26.7) → 95% (0.26.9, modelo leve) → 65% (0.26.10, sem espera OpenMP). Sem desfoque: ~12%.

---

# Etapa 0.26.10 (Claude) — análise ao vivo por etapa + ncnn sem espera ativa; APK de teste build78 entregue

- **[Baixar ZIP com app-release.apk — build78 (1078)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39144239874/artifacts/12143221335)**. Mesma assinatura permanente. Artefato 12143221335, 132851127 bytes; digest do ZIP sha256:5891955c7d02e153e79d95d2d418c8247f8554b8f913f88a2f2eb88c758c5f37; expira 2026-11-17T16:25:13Z. [Actions 39144239874](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39144239874) com sucesso; publicação skipped. Fonte do app eb9b71a (CI 39143628291 aprovado); fonte do APK 93ef47b; gatilho restaurado em 25ef88e (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- `depth.cpp`/`denoise.cpp`: `ncnn::set_kmp_blocktime(0)` após configurar threads (resultado idêntico; só evita busy-wait das threads OpenMP).
- `core/LiveAnalysisCpu` + medição em `PortraitMaskAnalyzer` (CPU da thread por etapa) e bloco no diagnóstico.
- **Codex, P0 que afeta bateria e interface:** ao abrir o Retrato com prévia do desfoque, a prévia trava (StallLog 0.26.8/0.26.9: efeito=true análise=true no 1º registro) e a escalada em `NovaCamera` (stall) desliga análise e depois `portraitPreviewFailed` — Tom/Temperatura somem até reabrir o app (relato do dono). Cada travamento + rebind também gasta energia. Hipótese a testar: carga simultânea dos modelos (segmentador + profundidade Vulkan) logo após o bind; não verificada.

---

# Etapa 0.26.9 (Claude) — profundidade mantida ao sair para a galeria; APK de teste build77 entregue

- **[Baixar ZIP com app-release.apk — build77 (1077)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39141521326/artifacts/12142415588)**. Mesma assinatura permanente. Artefato 12142415588, 132853716 bytes; digest do ZIP sha256:6f53cd030da8d0c88b37b20157a4ef496e631b2bd0050700ed7cd722d3806688; expira 2026-11-17T15:46:18Z. [Actions 39141521326](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39141521326) com sucesso; publicação skipped. Fonte do app a0f4c4a (CI 39140925891 aprovado); fonte do APK bd1c03f; gatilho restaurado em a600428 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- `MainActivity.onTrimMemory`: `UI_HIDDEN` (20) ≥ `RUNNING_LOW` disparava `trimModelMemory`, que liberava a profundidade a cada ida à galeria. `trimModelMemory` volta a liberar só NAFNet; profundidade sai via `ProcessingQueue.releaseDepthModel()` apenas em `RUNNING_CRITICAL`/`COMPLETE` (e pelo timer de 60 s / calor SEVERO).
- **Codex, avaliar:** no mesmo bloco, `tracking.stop("Memória limitada")` e `VideoFinishJob.cancel(...)` também disparam em `UI_HIDDEN` — o acabamento de vídeo é cancelado quando a pessoa só abre a galeria. Não mexi (área de vídeo).
- Medição de CPU da prévia com o modelo leve ainda pendente: no diagnóstico 0.26.8 a prévia do desfoque estava desligada pela recuperação de travamento.

---

# Etapa 0.26.8 (Claude) — bateria: prévia do Retrato com modelo leve; APK de teste build76 entregue

- **[Baixar ZIP com app-release.apk — build76 (1076)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39138561980/artifacts/12140374817)**. Mesma assinatura permanente. Artefato 12140374817, 132853213 bytes; digest do ZIP sha256:beccb8682601389cfca34ae3c97fffc8c38c02074a6e337a717d4c10d3bc6f14; expira 2026-11-17T15:07:16Z. [Actions 39138561980](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39138561980) com sucesso; publicação skipped. Fonte do app 4ba0c51 (CI 39137984831 aprovado); fonte do APK 05ee274; gatilho restaurado em f7e6db0 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- Causa medida dos 128–155% de CPU no Retrato parado: segmentação multiclasse na prévia ao vivo ("Thread-#" ~75% + análise ~30%). Correção: `PersonSegmenter.Live(context, light = !cinematic)` usa `assets/selfie_segmenter.tflite` (250 KB) só na prévia da foto. **Codex:** foto final e cinematográfico seguem no multiclasse; se mudar a máscara da prévia, manter o modelo leve (21× menos CPU por quadro no servidor).
- Executores nomeados e pilhas de "Thread-N" no diagnóstico (`ThreadCpu.addStacks`).
- Próximo passo de bateria: confirmar a queda no S24; depois olhar `binder` (6–8%) e o compositor GL (`nova-portrait-g` 5–7%, também no modo Foto).

---

# Etapa 0.26.7 (Claude) — bateria/CPU; APK de teste build75 entregue

- **[Baixar ZIP com app-release.apk — build75 (1075)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39136130725/artifacts/12139614731)**. Mesma assinatura permanente. Artefato 12139614731, 132640136 bytes; digest do ZIP sha256:d96ff445b76e0fc84ed99a6a5b0b78113eba17c5ddd68e51b393cdd74ed75424; expira 2026-11-17T14:35:06Z. [Actions 39136130725](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39136130725) com sucesso; publicação skipped. Fonte do app 5a3038f (CI 39135581746 aprovado); fonte do APK c52a6e6; gatilho restaurado em f173471 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Divisão (dono, 18/10):** Claude = bateria, CPU e consumo da prévia/processamento; Codex = qualidade de imagem (Noturno, Retrato, SR, máscaras). Depois virá uma repaginação de interface (estilo iPhone, transições suaves, mantendo os recursos) — ainda não iniciada. **Não editar ao mesmo tempo**; buscar HEAD remoto antes.
- Feito: (1) `DepthEstimator` mantém a sessão entre fotos (`IDLE_MS` 60 s; `release()` em calor SEVERO e em `ProcessingQueue.trimModelMemory`); (2) `core/ThreadCpu` + bloco "CPU por thread" no diagnóstico (amostrado no `energyTick`, fila io); (3) contraluz e auxílios Pro usam `PreviewFrameReader` em vez de `preview.bitmap`.
- Causa dos 155% de CPU da prévia do Retrato: **ainda não identificada**. Hipóteses a confirmar com o novo bloco: RenderThread/UI (invalidações do overlay), threads do CameraX (callbacks 30/s), análise/rastreamento, compositor GL. Só otimizar depois de ver os números.
- Codex: se mexer em `DepthEstimator.estimate`, preservar o cache (não voltar a fechar a sessão a cada foto).

---

# Etapa 0.26.6 (Claude) — alinhamento e super-resolução em vários núcleos; APK de teste build74 entregue

- **[Baixar ZIP com app-release.apk — build74 (1074)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39133451951/artifacts/12138453579)**. Mesma assinatura permanente, instala sobre a anterior. Artefato 12138453579, 132634522 bytes; digest do ZIP sha256:4be52c2aa1eea6613c4b8fae28824025459e013534309d67fbf49f733f376f27; expira 2026-11-17T14:00:31Z. [Actions 39133451951](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39133451951) concluído com sucesso (testes, lint, assembleRelease, assinatura, upload); publicação skipped. Fonte do app 01b5bd7 (CI 39133124284 aprovado); fonte do APK 9513b5e; gatilho original restaurado em 732a180 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- Branch **codex/video-estabilidade-0-20**, base 6266847 (APK 0.26.5/build73). Dono pediu: Claude e Codex no mesmo branch, **sem os dois editarem ao mesmo tempo**; buscar o HEAD remoto antes de retomar.
- **Causa medida do tempo:** todo o processamento rodava em 1 núcleo. Diagnóstico 0.26.4: alinhamento 7,5–8,5 s + SR 13–13,7 s ≈ 21 s de 38–49 s. Ambos são trabalhos independentes (quadro × referência; blocos da saída).
- **Mudança:** `core/Workers.kt` (núcleos por calor: frio até 4, MODERADO 2, SEVERO/economia 1) e `processing/Parallel.kt` (pool fixo de 4 threads daemon, resultado na ordem dos índices). `Engine.align` alinha os quadros 1..N em paralelo; `Engine.superResTiles` lê em série (cache de faixas do JPEG intacto), calcula lotes de 2×núcleos blocos em paralelo e grava em série na mesma ordem. `ProcessingQueue` define `Parallel.workers` por foto e marca no PhotoTrace. Núcleos C++ são sem estado (seguros entre threads). **Isto revoga conscientemente a regra "sem threads novas"**, pelo pedido do dono de priorizar tempo; com calor volta ao comportamento antigo.
- **Evidência local (servidor, não S24):** `ParallelEngineTest` — campos de alinhamento, quadros usados e pixels idênticos com 1 e 4 núcleos; 640×480 572→293 ms; 2000×1500 7,2→2,3 s (3,2×). Suíte completa e lint aprovados localmente; CI do commit a conferir.
- **Não comprovado:** tempo real no S24, temperatura, energia. Próximo passo físico: PhotoTrace de Foto 5× alta resolução e Retrato 5× com "núcleos de processamento: 4", comparar alinhamento/SR com 0.26.4/0.26.5.
- **Pendente de Claude fora deste branch:** 0.21.0 (branch ccr-7443eaac-tqygyg, commit aaa2a8e) — Noturno com SR no 10× (só na mão) e núcleo adaptativo da SR em pouca luz (área lisa ~3 dB menos granulada no teste sintético). Não portado: o `mergeTile` daqui usa `PhotoKernels.merge` em C++; portar exige o núcleo largo no C++ e teste de igualdade Kotlin×C++.
- Próximos candidatos de tempo (mesma abordagem, depois de medir): perfil de contraste (4,4–5,0 s), desfoque do Retrato (6,6 s), estilo (2,3 s).

---

# Entrega 0.26.5 — APK de teste build 73 (1073)

- **[Baixar ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39131104555/artifacts/12138172823)**. Extrair e instalar sobre a versão anterior, com a mesma assinatura permanente. Artefato **12138172823**, 132629039 bytes; SHA-256 **do ZIP**: **9dc817152193b544f0e18231a41b3c3e30b2280b0e1b8c61441412335576ce71**. Expira em **2026-11-17T13:28:31Z**. Nenhum release foi publicado.
- [Actions39131104555](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39131104555), job **117686274567**, concluído com sucesso: **301 testes aprovados, 5 dispensados, 0 falhas**, lint, codec JPEG sintético200MP (RSS host49860KiB), NAFNet e DepthAnything, assembleRelease, profundidade offline=True, continuidade da assinatura/versionCode e upload. Publicação **skipped**. Número real73; versionName0.26.5; versionCode1073 pela fórmula1000+run_number, com verificação do APK aprovada. Testes Android do host não são execução noS24/API37.
- Fonteapp **4344b8ef3188471df9c005021767088e24173c2e**, aprovada no CI **39130691263**; fonteAPK **e2b10b947632bfaf3eec986d2ba709885b04b8b3**. Nenhuma alteração de app após aprovação. Gatilho original já restaurado em **f3c8fcd02ded88f7afdda1d74d93c3f5c2924f8f**, blob **a82bc113b2de207b7a3e9a9660cc19af520167ff**. Não gerar outro APK sem nova etapa estável nem usar workflow_dispatch.

## Ajustes entregues e evidência

1. **Retrato de objeto:** uma única semente elegível próxima do toque/centro, conservando o componente inteiro; regiões desconectadas não entram só por estarem na área central. Pessoas e objetos segurados mantêm múltiplas sementes. Mediana3×3 no toque preserva estrutura2px com suporte local; pixel isolado não determina plano. Não recupera estrutura que o modelo monocular não resolveu.
2. **Referência do Noturno/Foto/Zoom:** ruído correlacionado não deve contar como nitidez; energia é compensada no agregado e a troca por quadro mais ruidoso exige margem adicional. Bordas realmente melhores ainda podem vencer. É seleção entre quadros capturados, sem filtro novo, sharpening, detalhes inventados, aumento de tolerânciaMerge, tom/WB/exposição alterados ou redução de quadros/resolução. AlgoritmosSR, modelos e otimizações0.26.4 preservados.
3. Diagnóstico registra índice/ruído/custo da referência, sigmaMerge em0..255 separado do alinhamento0..1 e origem/faixa relativa/cobertura da máscara. Cobertura não comprova precisão. Rejeição é descrita como contribuições inconsistentes, não movimento comprovado.
4. **Antes/depois sintético real noCI:** regressão e1e2831/39130459743 reproduziu3falhas antes do app: ilha1,0, estrutura tocada0,3333 e referência ruidosa escolhida. Fonte final e run assinado: ilha0,0, estrutura0,6667, referência limpa escolhida. Energia limpa0,00039181/ruidosa0,00048692 após compensação; teste de borda realmente mais nítida também passa. Testes existentes de pessoa, bordas, fusão, SR e orientação passaram. Não são resultados físicos destas fotos.
5. Custo de três scores768×576 medido nohost:27,539ms na fonte final e116,050ms no run assinado; intermediários44,168/79,023ms. Variação do servidor, sem comparação causal de latência total ou autonomia. Trabalho adicional de seleção fica no plano reduzido, sem modelo/threads/GPU novos; medir custo noS24. Não anunciar processamento gratuito nem ganho físico.

## Fotos e diagnóstico: associação ainda limitada

Três imagens declaradas pelo dono como0.26.3: Noturno3x, Noturno5x e Retrato5x. Cópias960×1280 semEXIF, pessoais foraGit, analisadas individualmente em **NOTURNO_RETRATO_0265.md**. O diagnóstico posterior é explicitamente **0.26.4/1072**; não tratar isso como A/B nem vincular versões por suposição.

Dados desse diagnóstico: Retrato49,012s/49MP/8quadros, ISO136/16,949ms, assunto automático pelo centro, alinhamento7,493s/SR12,957s/depth5,735s/refino2,398s/perfilContraste5,009s/desfoque6,579s. Noturno5x3/3 com rejeição53–60% e13–14,7s;5/5 com rejeição16% e19,17s; Noturno3x3/3 com5% rejeição e11,93s. JPEG18,6/7,9mm compatíveis com5x/3x, ID físico não confirmado. NAFNet abortou0/192 após2,349s com temperatura1→2; não tratar como redução porIA concluída. PSSaté624,5MiB/CPU149,4%, bateriaaté40,8°C com parte da sessão carregando; sem potência confiável ou economia demonstrada. Congelamentos semGL/análise continuam.

## Próximo passo exato

TestarAPK73 noS24, mantendo originais e diagnósticos localmente. Se ainda estiver na0.26.4, registrar referência antes de atualizar, sem desinstalar/apagar dados para downgrade. Comparar mesma cena/luz/foco/temperatura, fora do carregador e cinco repetições: Noturno3x/5x, Retrato5x automático e por toque, curvas/vãos metálicos, estrutura fina, pessoas/cabelo/objeto segurado e frontal. Inspecionar recortes nativos de grão, estampas, costuras, halos, máscara e altas luzes; capturar novo PhotoTrace com cobertura e sigmaMerge. Conferir oito quadros/resolução quando altares ativa, tempos separados, PSS/térmica e retorno da prévia. Sem isso não declarar qualidade física premium, menor tempo ou autonomia comprovados.

Continuam pendentes: fusão5x com rejeição elevada, foco real/óptica confirmada, HDR/highlights doRetrato, precisão do modelo em metal e objetos complexos, custo deSR/alinhamento/depth/blur/perfil e estilos, NAFNet carregando e abortando, congelamentos e escala energética. Não desligar perfil/estilo do usuário silenciosamente, ampliar orçamento da rede para dezenas de segundos, mudar tomNoturno, inventar detalhe ou remover8quadros/altares. Preservar main, chave, arquitetura/modelos, história e recursos.

## Mensagem pronta para Claude

Continue Nova Camera em chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, pelo HEAD remoto atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topoSTATUS e NOTURNO_RETRATO_0265.md. APK0.26.5/build73/1073 entregue noActions39131104555/artefato12138172823, 301 testes aprovados/5 dispensados, lint/modelos/offlineDepth=True/assinatura/upload reais, publicação skipped. Fonteapp4344b8ef3188471df9c005021767088e24173c2e. Três causas reproduzidas antes do app corrigidas: ilha desconectada noRetrato, alvofino perdido no toque e referência favorecida por ruído. Pessoas, tomNoturno/WB/8quadros/resolução/fusão/SR preservados; diagnóstico mostra referência, máscara e sigmaMerge. Fotos declaradas0.26.3 e diagnóstico0.26.4 foram mantidos separados; testes sintéticos não comprovam qualidadeS24. Próximo: feedback físico doAPK73, originais e PhotoTrace para ruído/texturas/metal/máscaras/tempos/térmica. Rejeição alta doNoturno5x, HDR/highlights, foco, SR/depth caros, NAFNet abortando e congelamentos seguem abertos. Não declarar otimização concluída, não mudar main/assinatura/modelos, não enviar fotos aoGit nem publicar release definitivo automaticamente. Atualize handoff com evidências e próxima ação.

---

## Histórico preservado — estados pendentes desta etapa abaixo foram superados

# APK de teste 0.26.5 — build 73 em geração (1073 esperado)

- Fonte de app **4344b8ef3188471df9c005021767088e24173c2e**, aprovada em compilação/testes/lint no CI **39130691263**. Nenhuma alteração de app após essa aprovação. Três causas reproduzidas antes do aplicativo foram corrigidas nos testes: ilha desconectada, estrutura fina tocada e referência favorecida por ruído. Resultados sintéticos, não validação física de qualidade.
- Fonte APK **e2b10b947632bfaf3eec986d2ba709885b04b8b3**, run **39131104555**, job **117686274567**, número real de build **73**. Link: https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39131104555 . **APK ainda NÃO confirmado neste registro.** VersionName0.26.5; versionCode1073 esperado pela fórmula, conferir verificação real do job.
- Gatilho original já restaurado em **f3c8fcd02ded88f7afdda1d74d93c3f5c2924f8f**, blob **a82bc113b2de207b7a3e9a9660cc19af520167ff**. Não gerar outro build, workflow_dispatch ou release. Publicação do push deve ficar skipped.
- Próximo passo exato: acompanhar run existente até conclusão; conferir testes/lint e contagem real, JPEG200MP, modelos offline/Depth=True, assembleRelease, certificado/versionCode e upload/publicação skipped. Depois registrar artefato real/link/digestZIP/tamanho/expiração emSTATUS/handoff e só então enviar ao dono. Se falhar, ler erro concreto e preservar progresso.
- Detalhes e análise individual em **NOTURNO_RETRATO_0265.md**. Fotos declaradas0.26.3 e diagnóstico0.26.4 foram mantidos separados. Diagnóstico informaRetrato49,0s eNoturno5x com rejeição53–60%; não declarar tudo resolvido, nem ganhos de autonomia/qualidade/S24 sem novas fotos. Foco, highlights/HDR, rejeição alta, tempos deSR/profundidade/desfoque, NAFNet abortando e congelamentos continuam pendentes. Preservar tomNoturno,8quadros/SR/resolução, modelos/main/chave permanente e mídia foraGit.

---

# Etapa atual 0.26.5 — Noturno e Retrato, fonte em validação

- Branch única **codex/video-estabilidade-0-20**. Base c128f496950e4ad7f0d972c1b14dc35149ca0639, CI39127776999 aprovado antes do app. Buscar HEAD remoto antes de retomar. APK anterior confirmado0.26.4/build72/1072 permanece no histórico; **nenhum APK0.26.5 confirmado neste registro**.
- Fonte atual de app **4344b8ef3188471df9c005021767088e24173c2e**, versionName0.26.5; CI **39130691263** ainda em fila neste registro. Fix principal51e6d63 teve erro de chamada Kotlinseed (CI39130473446); corrigido em a2206e5/CI39130495636, ainda consultar. Diagnóstico9c531f7/CI39130577011 também consultar. Não declarar aprovado sem execução real.
- Três fotos novas foram identificadas pelo dono como0.26.3:139082=Noturno3x,139081=Noturno5x,139078=Retrato5x. Arquivos recebidos960×1280 SEM EXIF, hashes/análise em **docs/pendente/NOTURNO_RETRATO_0265.md**, pessoais foraGit. Diagnóstico solicitado chegou como **0.26.4/1072**, registrar separado; não associar versões/arquivos univocamente sem EXIF.
- Diagnóstico físico0.26.4: Retrato5x49,012s/8quadros49MP/ISO136/16,949ms, assunto automático pelo centro; aquisição4,963s, align7,493s, SR12,957s, depth5,735s, refino2,398s, perfilContraste5,009s, blur6,579s. Noturno5x3/3/ISO2056/41,667ms total13,016–14,740s, rejeição0,528–0,600;5/5 total19,170s/rejeição0,160. Noturno3x3/3/ISO1870/33,333ms total11,933s/rejeição0,0508. JPEG18,6/7,9mm compatíveis, ID físico não confirmado. NAFNet abortou0/192 após2,349s, térmica1→2 durante carga. PSSaté624,5MiB/CPU149,4%, bateriaaté40,8°C, parte carregando; sem economia comprovada. Perfil/estilo são escolhas do usuário, não desligar silenciosamente.

## Falhas reproduzidas antes de modificar o aplicativo

- Regressão7dd0829/CI39130019486:303totais,2falhas,5dispensados. Objeto central1,0 e ilha desconectada1,0: ambos viram assunto. FrameQuality escolheu quadro com ruído correlacionado como mais nítido (limpo0,00040736 versus ruidoso0,00707443, índice1).
- Teste de estrutura fina foi fortalecido em **e1e2831/CI39130459743** antes do app:303totais,3falhas,5dispensados. Toque em estrutura de2px gerou máscara0,3333 (fundo suavizado), não assunto. Essa regressão também reproduz as outras duas falhas. Não mascarar com tolerâncias frouxas.
- São causas comprovadas em cenários sintéticos, não prova de serem os únicos defeitos das fotosS24. Não usar correção de código como confirmação física de qualidade.

## O que mudou

1. DepthPortrait usa uma única semente elegível próxima do centro/toque no modo de objeto, conservando todo o componente conectado. Pessoas mantêm múltiplas sementes/membros/objeto segurado. Toque usa mediana3×3 em vezde5×5: preserva estrutura2px com suporte local, sem deixar pixel isolado definir plano. Não resolve detalhe ausente do modelo monocular, nem é segmentação semântica de qualquer objeto.
2. FrameQuality usa estimativa correlacionada já existente; soma energia dos gradientes antes de subtrair variância, sem viés do corte positivo por pixel. Para trocar por quadro mais ruidoso, exige margem adicional de detalhe; teste garante que bordas suficientemente mais nítidas ainda podem vencer. Não filtra pixels, não inventa detalhe, não altera tom/WB/exposição/resolução/quadros/fusão/SR.
3. Diagnóstico registra índice/ruído/custo da referência, sigma de rejeiçãoMerge em0..255 separado de sigma de alinhamento0..1 e cobertura/faixa relativa da máscara. Cobertura não mede precisão. Mensagem de rejeição informa “contribuições inconsistentes”, pois rejeição alta não prova movimento. Limiares de fusão não foram aumentados às cegas.
4. Testes cobrem ilha separada, alvofino, pixel isolado, troca de referência por ruído e manutenção de bordas reais; benchmark score3×768×576, sem modelo novo/GPU. O teste de outlier passou a verificar o plano de foco (não cobertura de um pixel que o blur da máscara pode preencher), antes da aprovação final; não alegar tolerância menor.

## Próximo passo exato

- ConferirCIs4344b8e/39130691263 e9c531f7/39130577011, alémdea2206e5/39130495636. Ler logs concretos e corrigir causas se falharem; preservar regressões e não remover recursos/tolerâncias. Compilação/testes/lint obrigatórios porActions; Gradleoffline local foi tentado e não existe neste snapshot.
- Só depois de fonte estável, gerarAPKteste permanente pelo fluxo autorizado: adicionar temporariamente branch ao push de nova-release.yml, mantendo guarda que pula publicação; NUNCA workflow_dispatch. Restaurar bloboriginal **a82bc113b2de207b7a3e9a9660cc19af520167ff** assim que run aparecer. Consultar build real, não presumir73; conferir testes/lint/JPEG200MP/modelos/offlineDepth=True/assembleRelease/certificado-versionCode/upload/publicação skipped. Registrar artefato real/link/digestZIP/expiração e só então entregar.
- Validar noS24 com originais/burst e novas capturas: seleção de objeto e vãos metálicos, folhas/cabelo/pessoas, ruído/texturas/foco, tempos e calor, sem mudar tomNoturno. Continuam pendentes rejeição53–60%noNoturno5x, alinhamento/SR lentos, highlights/HDR doRetrato, congelamentos semGL/análise e escala energética. Investigar também custo de cargaNAFNet que aborta antes do1ºbloco/liberação em calor; não ampliar orçamento sem qualidade/desempenho medidos. Nenhuma otimização física concluída.

## Texto para Claude

Continue Nova Camera em chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, HEAD remoto atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topoSTATUS e NOTURNO_RETRATO_0265.md. Fonte atual0.26.5:4344b8ef3188471df9c005021767088e24173c2e, CI39130691263 pendente neste registro; verificar também39130577011 e39130495636. Base0.26.4 aprovada, APK72 anterior preservado; nenhum APK0.26.5 confirmado. Três regressões reproduzidas antes do app em39130459743: ilha desconectada vira assunto, alvofino vira fundo e ruído é favorecido como nitidez. Fix limita sementes só em objetos, usa mediana3×3 e seleção de referência conservadora contra ruído, sem alterar tom/pixels/fusão/SR/resolução/quadros. Diagnóstico novo mede referência, máscara e sigma de rejeição sem afirmar movimento. Fotos declaradas0.26.3 e diagnóstico0.26.4 devem ficar separados. Após CI completo, gerar sóAPKtesteassinado com publicação skipped e restauração do gatilho; conferir artefato antes de avisar. Preservar main/modelos/chave/histórico, fotos foraGit; qualidade/tempo/autonomiaS24 não comprovados. P0prévia, rejeição noturna alta, perfil/SR/depth caros, NAFNet abortando e HDR/highlights seguem abertos. Documente resultados reais e próxima ação.

---

## Histórico preservado — a atualização acima substitui estados anteriores desta etapa

# Entrega 0.26.4 — APK de teste build 72 (1072)

- **[Baixar ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39127091262/artifacts/12137010846)**. Extrair e instalar sobre a versão anterior. Artefato **12137010846**, 132627982 bytes; SHA-256 **do ZIP**: **6e926a82da4b8416857580d20b68adbe99c76e1bee108166c2291be0393d8907**. Expira em **2026-11-17T12:30:40Z**. Chave permanente preservada; nenhum release criado.
- [Actions 39127091262](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39127091262), job **117674387380**, concluído com sucesso: **295 testes aprovados, 5 dispensados, 0 falhas**, lint, codec JPEG sintético 200 MP (RSS host 49916 KiB), NAFNet e Depth Anything, assembleRelease, profundidade offline=True, continuidade da assinatura/versionCode e upload. Publicação **skipped**. Build real72; versionName0.26.4; versionCode1072 pela fórmula1000+run_number, com verificação do APK aprovada. Testes Android de host usam Robolectric, não execução no S24/API37.
- Fonte de app **142c648dde009250527b3525d807e4625078f8c2**, aprovada no CI **39126725397**; alternativa Kotlin anterior 96848c1/39126506961 também aprovada. Fonte APK **e8ae0037892a171fa74d24a47edb9e5ce02e86cf**. Nenhuma alteração de app depois da aprovação. Gatilho original já restaurado em **8842d6faafe2472e5ef6c8fb73c6581caf863b34**, blob **a82bc113b2de207b7a3e9a9660cc19af520167ff**; não disparar outro build ou workflow_dispatch.

## O que foi entregue e o que continua aberto

1. ISO/exposição JPEG por quadro, com origem separada de Camera2; acabamento usa a referência efetivamente escolhida. Focal compatível continua diferente de ID físico confirmado. Sem espera extra na aquisição.
2. Cálculo tonal permanece Kotlin: reutilização de coordenadas bilineares e faixa64linhas; planos neutros deixam de copiar a foto inteira. Food, proteção cromática, cancelamento, parâmetros, amostras e arredondamentos preservados; testes exigem pixels iguais. Plano tonal, perfil e estilo agora aparecem separados nos tempos. Não há novo filtro de ruído, nitidez ou mudança de tom Noturno.
3. Experimento tonalJNI foi testado e descartado antes do APK por falta de vantagem consistente; arquivos nativos foram restaurados à0.26.3. Kernels rápidos anteriores de alinhamento/SR/detalhes permanecem intactos. Não reativar esse experimento com base apenas em igualdade de pixels.
4. Microbenchmarks Kotlin, cinco pares com aquecimento: CI39126506961 medianas129,884→110,713ms; CI39126725397 154,589→125,825ms; run assinado39127091262 105,011→83,409ms. Há variação do servidor; são grupos de três faixas8160×64 com planos1024×768. **Não medem tempo total49MP, energia, PSS ou autonomia no S24.** Menos buffers é consequência do código, não medição física de memória.
5. Três fotos reais e diagnóstico0.26.3 analisados individualmente em **QUALIDADE_0263_BASELINE.md**; cópias1280×960 sem EXIF, pessoais foraGit. Diagnóstico informa8/8 e49MP em39,89–41,52s, Noturno5/5 em15,27s. FocaisJPEG18,6mm/7,9mm compatíveis com5x/3x, ID não confirmado. Nada disso valida melhora física desta atualização.

## Próximo passo exato

O dono deve testar o APK72 no S24 em Foto3x/5x alta resolução e Noturno5x apoiado, mesmas cena/luz/foco/temperatura inicial e cinco repetições, seguindo o roteiro no baseline. Antes de atualizar, se ainda estiver na0.26.3, guardar referência sem desinstalar/apagar dados. Conferir ISO/exposição por quadro, dimensão, quadros usados, tempo de aquisição/alinhamento/SR/plano/tons/gravação, grão/texturas/bordas/cor/halos/fantasmas, PSS/térmica e retorno da prévia. Sem esse ensaio, eficiência e qualidade física continuam abertas.

Depois, investigar os gargalos ainda medidos de alinhamento/SR/fusão/planejamento e os congelamentos fora da rajada. Não remover oito quadros, reduzir resolução silenciosamente, clarear Noturno ou forçar rotas físicas/ZSL que já travaram. Ruído fino, foco real e texturas ainda precisam de evolução com originais/burst e evidência; esta entrega preserva a matemática visual e melhora a eficiência/diagnóstico, não comprova detalhe novo. NAFNet anterior abortou3/192blocos, não tratá-lo como refinamento concluído. Escala energética ainda não confirmada; nenhuma economia de bateria demonstrada. Main, modelos, assinatura, recursos e histórico preservados.

## Mensagem pronta para Claude

Continue a Nova Camera em chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, pelo HEAD remoto atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topo de docs/STATUS.md e QUALIDADE_0263_BASELINE.md. APK de teste0.26.4/build72/1072 entregue no Actions39127091262/artefato12137010846, 295 testes aprovados/5 dispensados, lint/modelos/assinatura/upload reais e publicação skipped. Fonte app142c648dde009250527b3525d807e4625078f8c2. Tom permanece Kotlin, com coordenadas/faixas reutilizadas e dispensa de cópias neutras, pixels iguais nos testes; experimento tonalJNI descartado por falta de ganho. Metadados JPEG/referência e cronômetros corrigidos. Próximo passo é feedback físico do APK72 comparando3x/5x/Noturno, qualidade, tempo por etapa, PSS e temperatura. Ainda não há ganho físico comprovado; ruído/texturas/foco, aquisição/alinhamento/SR e congelamentos continuam abertos. Preserve oito quadros/resolução, tom Noturno, main/modelos/chave/histórico e fotos foraGit. Não gere release definitivo automaticamente nem outro APK sem nova etapa estável.

---

## Histórico preservado — estados de CI/APK pendentes abaixo foram superados

# 0.26.4 — APK de teste build 72 em geração (1072 esperado)

- Fonte de app **142c648dde009250527b3525d807e4625078f8c2**, aprovada em compilação, testes e lint no CI **39126725397**. Alternativa Kotlin anterior 96848c1/39126506961 também aprovada. Nenhuma alteração de app após aprovação.
- Fonte do APK **e8ae0037892a171fa74d24a47edb9e5ce02e86cf**, run **39127091262**, job **117674387380**, número real de build **72**. Link: https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39127091262 . **APK ainda NÃO confirmado neste registro.** VersionName0.26.4; versionCode esperado1072 conforme fórmula do projeto, verificar no job.
- Workflow original já restaurado em **8842d6faafe2472e5ef6c8fb73c6581caf863b34**, blob **a82bc113b2de207b7a3e9a9660cc19af520167ff**. Não iniciar outro build, workflow_dispatch ou release. Publicação deste push deve ficar skipped.
- **Próximo passo:** acompanhar o run existente. Conferir testes/lint, resumo real dos testes, codec JPEG200MP, NAFNet/DepthAnything offline, assembleRelease, continuidade da assinatura/versionCode e upload. Só depois registrar artefato real, link, digest do ZIP, tamanho e expiração, atualizar STATUS e avisar o dono. Se falhar, ler erros concretos e preservar este handoff.
- Resultado do código: metadadosJPEG/referência corrigidos; tom permanece Kotlin, com reuso de geometria/faixa64 e dispensa de cópias neutras preservandoFood/cancelamento; plano/perfil/estilo medidos. Experimento tonalJNI foi descartado. Cinco pares no CI final: medianas154,589/125,825ms, quatro favoráveis; ensaio anterior129,884/110,713ms. Apenas microbenchmarks no servidor. Sem ganho físico de qualidade, autonomia ou velocidade total comprovado; prévia ainda pendente.
- Baseline e análise das três fotos em QUALIDADE_0263_BASELINE.md. Preservar8quadros/49MP, Night/WB, modelos, main, assinatura e mídia pessoal foraGit. Texto completo para Claude e detalhes abaixo; estados de CI pendente da fonte abaixo foram superados, não os limites físicos.

---

# Etapa atual 0.26.4 — Kotlin preservado; validação e APK pendentes

Trabalhar somente em `codex/video-estabilidade-0-20`. Buscar HEAD remoto atualizado. A fonte de app mais recente é **142c648dde009250527b3525d807e4625078f8c2**. A versão continua 0.26.4; ainda NÃO há APK dessa versão neste registro. O APK confirmado anterior é 0.26.3, build 71 (1071), no histórico abaixo.

**Correção do estado anterior:** o laço tonal em JNI foi testado e descartado antes de gerar APK. O CI 39126084412 passou, mas cinco pares AB/BA não mostraram vantagem consistente (medianas Kotlin 133,877 ms / JNI 162,440 ms). Em 96848c1, photo_kernels.cpp e PhotoKernels.kt foram restaurados ao conteúdo anterior. Os kernels nativos existentes de alinhamento, SR e detalhes permanecem intactos. Não habilitar o experimento antigo com base em testes de pixels: igualdade não provou velocidade.

A alternativa atual é inteiramente Kotlin: ToneMap reutiliza índices e pesos bilineares; Engine usa uma faixa curta de 64 linhas reutilizável, com cancelamento entre faixas. A fórmula, parâmetros, amostras, arredondamento, proteção de cor e tom Noturno não mudam. A referência anterior fica disponível para testes exatos e microbenchmark pareado. Em 142c648, planos neutros deixam de copiar a imagem inteira inutilmente; Food continua aplicando seu acabamento e cancelamento continua respeitado. Testes Android comparam pixels, costuras, última faixa, plano neutro e Food. O CI da fonte 96848c1 é **39126506961**, ainda em andamento neste registro; também consultar o run da fonte final 142c648. Não usar aprovação do JNI descartado para declarar essa alternativa validada.

Também mudaram ISO/exposição por quadro no relatório (JPEG e Camera2 com origens separadas), ISO/exposição da referência escolhida no acabamento e cronômetros de planejamento tonal, perfil e estilo. Não houve novo denoise, modelo, nitidez, clareamento ou mudança de resolução/quadros.

As três fotos e o diagnóstico reais de 0.26.3 foram analisados; veja **docs/pendente/QUALIDADE_0263_BASELINE.md**. Fotos pessoais estão fora do Git. Cópias recebidas 1280×960 sem EXIF não comprovam detalhes de 49 MP. Diagnóstico: três fotos 8/8 e 49 MP em 39,89–41,52 s; Noturno apoiado 5/5 em 15,27 s. Focais JPEG 18,6 mm compatíveis com 5x e 7,9 mm com 3x, ID físico não confirmado. PSS observado até 634,4 MiB e bateria até 38,6 °C, sem comparação controlada. Congelamentos fora da rajada continuam abertos. Escala energética ainda não confirmada. NAFNet abortou 3/192 blocos; não tratar isso como redução de ruído por IA concluída.

## Próximo passo exato

1. Consultar os CIs de 96848c1 e 142c648, ler logs completos se falharem e corrigir a causa. Registrar os cinco pares **TONE_KOTLIN**, contagem real de testes, compilação e lint. Não reduzir tolerâncias de pixels. Gradle offline local foi tentado e retornou command not found; usar Actions conforme autorizado.
2. Se a fonte final passar, gerar apenas APK de teste com a chave permanente pelo fluxo existente. Adicionar temporariamente esta branch ao push de nova-release.yml mantendo a guarda que pula publicação; NUNCA usar workflow_dispatch. Assim que o run surgir, restaurar o blob original **a82bc113b2de207b7a3e9a9660cc19af520167ff**. Conferir testes, lint, JPEG 200 MP, modelos offline/Depth=True, assembleRelease, certificado/versionCode e upload; publicação deve ser skipped. Consultar build real, não presumir 72. Só avisar APK disponível depois do artefato real.
3. Atualizar STATUS e este handoff com SHA/run/artefato/link/digest/expiração reais. Nenhum ganho físico de velocidade, memória, qualidade ou autonomia foi comprovado nesta sessão. Menos buffers é consequência do código, não PSS medido.
4. No S24, comparar 3x/5x de alta resolução e Noturno5x apoiado com mesma cena, luz, foco e temperatura inicial, cinco repetições. Medir PhotoTrace, textura/grão/cor/halos/fantasmas em recortes nativos, PSS/térmica e ISO/exposição. Continuam pendentes qualidade fina, foco real, aquisição, alinhamento/SR/fusão e prévia. Não forçar lentes/ZSL que já travaram, reduzir quadros/resolução silenciosamente ou alterar atmosfera Noturna.

## Mensagem pronta para Claude

Continue Nova Camera em chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, pelo HEAD remoto atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topo de docs/STATUS.md e QUALIDADE_0263_BASELINE.md. Fonte atual de app 0.26.4: 142c648dde009250527b3525d807e4625078f8c2. Consulte CIs de 96848c1/39126506961 e da fonte final; pendentes neste registro. O experimento tonal JNI foi descartado por falta de ganho consistente; não reativar. A alternativa Kotlin reutiliza coordenadas e faixa64, dispensa cópias de planos neutros preservando Food; exige pixels exatos e benchmark AB/BA. Metadados JPEG/referência e cronômetros também corrigidos. Há três fotos e diagnóstico real 0.26.3, mas nenhum ganho físico novo comprovado. Depois de fonte estável, gerar só APK de teste assinado, com publicação skipped e restauração do gatilho original; registrar artefato real. Preserve main, histórico, modelos, assinatura, oito quadros/resolução, tom Noturno e fotos fora do Git. Prévia, foco, ruído/microtexturas, aquisição e SR continuam abertos. Não declarar otimização concluída sem S24.

---

## Histórico preservado; atualizações acima substituem estados anteriores

# Continuidade 0.26.4 — fotos reais recebidas, fonte em validação

- Branch única codex/video-estabilidade-0-20, base cf6f617cee143f50b79b25cfafbc9f02f3fe6420/CI39123476501 aprovada antes de editar. Buscar HEAD remoto antes de retomar; main e assinatura permanente intactas. APK anterior confirmado0.26.3/build71/1071 no histórico abaixo. Nenhum APK0.26.4 disponível neste registro.
- Fonteapp mais recente **8d98028e8826ed9d5e6fc026b452e194a193e082**, versionName0.26.4. Baseline documentado75a8c7b/CI39125533744 aprovado. a1413b6 metadados JPEG/referência; 07ccf74 laço tonalJNI/testes; 68cf8da faixas reutilizáveis/Androidteste; 8d98028 cronometra plano tonal/perfil/estilo. CIs39125696313 e39125866904 ainda em andamento neste registro. Consultar também run da fonte8d98028; não declarar aprovado sem execução real.
- Dono forneceu3fotos0.26.3:3xFoto/5xFoto/5xNoturno e diagnóstico completo. Cópias1280×960 SEM EXIF, hashes/análise/limites/tempos em docs/pendente/QUALIDADE_0263_BASELINE.md. Arquivos pessoais foraGit. Não é possível associar univocamente4registros a3imagens semEXIF. JPEGs do diagnóstico:18,6mm compatível5x e7,9mm compatível3x; ID físico ainda não confirmado. Não forçar rota física por presunção.
- DadosS24:3rajadas49MP8/8 total39,89–41,52s; aquisição5,61/8,34/7,25s; align6,32–6,83s; SR11,31–12,75s; acabamento11,94–14,47s, tons4,62–5,15s. Noturno apoiado5/5 total15,27s/align2,88/fusão4,89/acabamento2,04. PSSaté634,4MiB/CPU144,2%; bateria35,4→38,5°C/máx38,6°C, térmico1. Não é A/B controlado contra0.25.3, não declarar autonomia/ganho causal. Corrente bruta continua escala não confirmada. Prévia ainda congela semGL/análise. Falha nativa17/10 não tem stack/versão suficiente; Java0.20.0 histórico.

## O que mudou nesta etapa

1. CaptureOptics armazena ISO/exposiçãoJPEG por quadro e fonte de cada dado, Camera2 separado; ausência não vira zero nem confirmação física. NovaCamera lê uma vez após liberar JPEG, sem espera extra. Session usa ISO/exposição da referência efetivamente escolhida no acabamento (não de outra imagem da rajada). Testes paraJPEGsemCamera2, divergência das fontes e sentinelas.
2. ToneMap porta mesmo laço para photo_kernels.cpp/novadenoise existente: bilinear/pesos/parâmetros/expDouble→Float/arredondamento iguais, sem LUT aproximada ou fastmath/FMA. Kotlin preservado, proteção cromática>0 segueKotlin. Constantes log calculadas noKotlin. JNI valida geometria/limites, não retém ponteiroJava; planos reduzidos viaGetFloatArrayElements/ReleaseJNI_ABORT, semCritical; bloco separado atéterminar, bad_alloc→OOM protegido pela fila.
3. Engine.toneInPlace comnative disponível e nãoFood usa faixa64linhas reutilizável, coordenadas globais iguais, cancelamento a cadafaixa; resto caminho antigo. NativeTone limite4Mpx/bloco; planoaté4096²; fora dissofallbackKotlin. TesteAndroid4601×137 comparaBitmapinteiro, última faixa, pixels exatos/cancelamento. Host compara ruído/pele/pretos/altas/cores/Noturno/intensidade/proteção/costuras; microbenchmarkincluiJNI e planos1024×768, faixa8160×64. Esses testes ainda não aprovados neste registro.
4. Cronometra planejamento tonal, perfil e estilo para localizar parte antes ausente do acabamento. Não muda modelos/denoise/SR/fusão/8quadros/resolução/seleçãofoco/tonalidadeNoturno/WB/brilho/nitidez. Não alegar melhoria de detalhes físicos por portar pixels iguais. NAFNet no diagnóstico abortou3/192blocos, não tratar como refinamento concluído.

## Próximo passo exato

- ConsultarCIs dafontefinal8d98028 e anteriores; lerlogs sefalharem, corrigir causa sem apagar testes/tolerância. Registrar microbenchmarkpareado TONE_HOST e contagemreal. Gradleoffline local tentou e retornou commandnotfound; ambiente snapshotsemgit/SDK/JDKheaders completo. Actions é validação autorizada.
- Se código finalcompilar/testar/lint comsucesso, gerar sóAPKteste assinado pelo fluxo existente: adicionar branch temporariamente ao push denova-release.yml com guarda de publicação mantida; NUNCA workflow_dispatch (publicariarelease). Restaurar imediatamente bloboriginala82bc113b2de207b7a3e9a9660cc19af520167ff assim que runaparecer. Conferir teste/lint/JPEG200MP/modelos/offlineDepth=True/certificado/versionCode/upload, publicaçãoSKIPPED. Próximo build não presumir72: consultarvalorreal. RegistrarartefatoZIP/link/digest/expiração, sóentão avisardono.
- S24: repetirFoto3x/5xaltares8quadros eNoturno5xapoiado, mesmascena/luz/temperatura/brilho/foco e5repetições. Compararcores/texturas/grão/halos/fantasmas, tempos, PSS/térmica e óptica/ISO/exposiçãoporquadro. Melhoradevelocidade/energia noaparelho NÃOcomprovada. CongelamentosaindaP0aberta. Novomapeamentotonal mantémimagem; evoluçãorealderuído/microtexturasexigeburst/originais nativos, nãosóJPEGredimensionado.
- Depoisdeestável: perfilar alinhamento/SR/fusão/planejamentocomnovasmedições e investigar callbacks/prévia/foco semreativarZSLrotasproblemáticas. Não trocar8quadros/49MPporampliação/reduçãooculta nemclarearNoturnoparafingirdetalhes. NenhumapostagemdemídiapessoalnoGit/Actions.

## Texto para Claude

Continue Nova Camera em chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, HEAD remoto atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topoSTATUS e QUALIDADE_0263_BASELINE.md. Fonteapp0.26.4 destaetapa8d98028e8826ed9d5e6fc026b452e194a193e082; CIspendentesnestedocumento, consultarestadosreaisantesdeAPK. Dono forneceu3fotos3x/5x/Noturno5x e diagnóstico0.26.3:8/8/49MP~40–42s,5/5noturno15,27s, PSS634MiB; focaisJPEGcompatíveis masIDnão confirmado. Mudançaslimitadas: ISO/exposiçãoJPEGporquadro/referênciacorreta, ToneMapJNIequivalente/faixa64linhasreutilizada, cronometrarplanotonal. Exigirpixelsiguais/regressõesCI e compilar/lint, depoisAPKtestepermanenteassinado semreleaseautomático, restaurargatilhooriginal. Não declararmelhoraS24dequalidade/velocidade/bateria semensaiofísico. Préviacongela, alinhamento/SR/ruído/texturas continuamabertos. Preservequalidade,8quadros/resolução, tomNoturno, main/modelos/chave/histórico; pessoaisforaGit. Atualizehandoffcomrun/artefatocorretos.

---

## Histórico anterior preservado — a atualização acima substitui seus estados pendentes

# Entrega 0.26.3 — APK de teste build 71 (1071)

- **[Baixar ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39122820061/artifacts/12134983449)**. Artefato 12134983449, 132625602 bytes; SHA-256 **do ZIP**: cb1cb622e0b162bd12c8c8ba1e02f7834ad2af26a79f8e253a6ac84157ee5f70. Expira em 2026-11-17T11:28:36Z. Extrair e instalar sobre a versão anterior; chave permanente preservada.
- Actions [39122820061](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39122820061), job 117661716743: **289 testes aprovados, 5 dispensados, 0 falhas**, lint, codec JPEG sintético 200 MP (RSS host 49860 KiB), NAFNet e Depth Anything, assembleRelease, modelos offline (profundidade=True), continuidade da assinatura/versionCode e upload aprovados. Publicação **skipped**, nenhum release criado. Run_number 71 e fórmula versionCode=1000+run_number produzem 1071; versionName 0.26.3 na fonte.
- Fonte app `7f6de132258a33827ae819e67e667a0d1237f4a1` aprovada no CI 39122555216; óptica anterior cff1719/39122412407 também aprovada. Fonte APK `a150588a5cae3849eddb3c678fd259ede5cdc26f`. Nenhuma alteração de app após aprovação. Gatilho original restaurado em `fa14f24431363d36c4ba8ea7f12074b6d422afb0`, blob `a82bc113b2de207b7a3e9a9660cc19af520167ff`.
- Dono confirmou: foto 5x no modo **Foto**; Noturno **apoiado**. Fotos recebidas 1280×960 sem EXIF. Alta resolução ainda não explicitamente esclarecida. Não confirmar retrospectivamente lente 3x/5x apenas com esses arquivos. Fotos pessoais fora do Git.
- Defeitos reproduzidos na base: plano ISO/tempo inviável e ruído correlacionado estático rejeitando 4/4 quadros. Correção passou nos Actions: RMS sintético 11,650→6,266, 4/4 aproveitados, testes de cor/contraste/movimento aprovados. Isso NÃO é resultado de redução de ruído no S24. Tom, BOOST_EV, WB, algoritmos SR e kernels rápidos anteriores preservados.
- **P0 continua aberta para validação física.** Aproveitar mais quadros úteis também aumenta trabalho de fusão comparado a rejeitá-los; medir captura/alinhamento/fusão/acabamento/total e térmica, sem prometer eliminar 60–79s por teste sintético. Nenhuma melhora física de velocidade, autonomia ou qualidade foi declarada. Congelamentos fora da rajada, aquisição 7–9s e escala energética seguem pendentes.

## Próximo passo para Claude/Codex

Receber JPEGs originais como arquivos/ZIP e diagnóstico dos testes 0.26.3: repetir Foto 5x e Noturno apoiado sob condições semelhantes, conferir alta resolução/plano de captura, óptica confirmada/compatível/desconhecida, ISO/exposição reais e quadros usados. Comparar texturas, grão, cor ambiente, sombras, foco, fantasmas/halos e tempos por etapa/PSS/térmica. Não forçar lente física na sessão Retrato que já travou, não confundir zoom da interface com óptica. Se a 3x em Foto5x for confirmada, avaliar uma escolha óptica explícita e suportada com fallback comunicado, baseada em qualidade real. Se a fusão virar gargalo, perfilar readAligned/interpolação cúbica/Merge antes de mudar amostras ou textura. Não gerar outro APK sem necessidade; este build está disponível.

## Texto pronto para enviar ao Claude

Continue Nova Camera em chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, pelo HEAD remoto atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topo de docs/STATUS.md e P0_NOTURNO_0262.md. APK 0.26.3/build71 (1071) entregue no Actions39122820061/artefato12134983449, 289 testes aprovados/5 dispensados, lint/modelos/assinatura/upload conferidos, release skipped. Fonte app7f6de132258a33827ae819e67e667a0d1237f4a1. Foram corrigidos plano noturno inviável e rejeição de ruído correlacionado; CaptureOptics separa dados da captura de zoom/prévia. Resultado sintético11,65→6,27 não comprova ganho S24. Dono testou Foto5x e Noturno apoiado; fotos recebidas semEXIF não confirmam lente antiga. Próximo: comparar JPEGs/diagnóstico reais, óptica/ISO/quadros/texturas/tempo/térmica, preservar oito quadros/SR/resolução, tomNoturno, modelos, main e chave permanente; não publicar release definitivo automaticamente. Fotos pessoais foraGit. Atualize handoff com evidências e pendências.

---

## Histórico preservado — estados pendentes de APK abaixo foram superados

# APK 0.26.3 em geração — build71 (1071 esperado)

- Fonteapp `7f6de132258a33827ae819e67e667a0d1237f4a1` aprovada no CI39122555216 (compile/test/lint); óptica intermediatecff1719/39122412407 também aprovada. FonteAPK `a150588a5cae3849eddb3c678fd259ede5cdc26f`. Nenhuma edição de app depois da aprovação.
- Build assinado real **71**, run https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39122820061 , job117661716743. A previsão70 abaixo era anterior: usar número real71; versionCode esperado1071. APK ainda NÃO confirmado.
- Workflow original já restaurado em `fa14f24431363d36c4ba8ea7f12074b6d422afb0`, blob`a82bc113b2de207b7a3e9a9660cc19af520167ff`. Não disparar outro workflow/release.
- Próximo passo exato: consultar o job39122820061, verificar testes/lint/JPEG200MP/modelos/assembleRelease/offlineDepth=True/certificado-versionCode/upload/publicação skipped. Só depois registrar artefato real/link/digest/tamanho/expiração emSTATUS e aqui, e enviar ao dono.
- Nova correção foi reproduzida em teste real no servidor: ruído correlacionado estático RMS11,65 inalterado→6,27 com4/4 quadros aceitos; plano ISO/tempo inviável corrigido. Isso não valida qualidade/óptica/desempenho/térmica no S24. Fotos da0.26.2 são1280×960 semEXIF. Dono confirmou depois: Foto na5x e Noturno apoiado. Alta resolução não foi explicitamente esclarecida. Detalhes, causas e texto para Claude abaixo/P0_NOTURNO_0262.md.

---

# Continuidade 0.26.3 — feedback5x/Noturno, fonte em verificação

- Branch única codex/video-estabilidade-0-20. Buscar HEAD remoto atualizado; main/chave permanente intactas. Fonteapp7f6de132258a33827ae819e67e667a0d1237f4a1 (0.26.3), CI final39122555216 ainda em andamento neste registro. Óptica anteriorcff171961625e441e5dfda988b855205e2af08cd/CI39122412407 também consultar. Não declarar APK disponível nem S24 validado. APK mais recente confirmado continua0.26.2/build69/1069, histórico abaixo.
- Dono confirmou0.26.2 e prioridades: P0 óptica5x versus3x+digital/ruído noturno; P1 ISO/exposição/atmosfera/nitidez/desempenho. Duas fotos reais recebidas1280×960 SEM EXIF; não foram enviadas aoGit. Não afirmar qual lente fez essas fotos sem JPEG original/diagnóstico. Resposta “sim” a pergunta composta sobre modo/altares/apoio foi ambígua; foram abertas perguntas separadas Foto/Retrato e mão/apoiado, ainda pendentes neste registro. Alterações não dependem dessas respostas.
- Ler este handoff inteiro, topoSTATUS e docs/pendente/P0_NOTURNO_0262.md. Baseline antesapp79b2c3/CI39113294053 aprovado. Teste de regressão b6a3823/39121910927 falhou de propósito em duas causas reproduzidas: plano ISO800/100ms sem luz suficiente e ruído correlacionado estático rejeitando4/4, RMS11,65 inalterado. Fix5a9f660/CI39122118028 aprovado compile/test/lint:4/4 e RMS6,266. Sintético, não medição real do aparelho.
- Correlação: helperCorrelatedNoise usa amostra limitada1/2/4px e aumento apenas com platô; Merge.estimateNoise/Consistency.noiseOf mantêm padrão antigo, flag sóEngine.align/mergeInto na fusãoNight ouZoomISO≥800. AltaresSR/HDR/nativo permanecem idênticos. Testes de cor/contraste/movimento/independente/rampas/padrões passaram. NightExposure recusa plano que perderia luz ao limitarISO; não altera0,5EV/tom/WB. Camera2future de manual agora confereget e volta aAE se recusado.
- CaptureOptics novo: resultadoSTILL_CAPTURE+timestamp exato/cache16, JPEG focal/focal35 separada, compatibilidade versus confirmação/conflito; não inventar ID com pin solicitado. Evidence porCapturedFrame, referência/série nasession.meta e descriçãoEXIF; diagnósticoPhotoTrace. NAI primeirafoto usaISO/exposiçãoJPEG e óptica confirmada em vez de prévia. Zoom solicitado congelado no início. Sem forçar5x física nem mudar firmwarepolicy de Retrato/Vídeo, que tem histórico de travamento. Fotos antigas não podem ser confirmadas retroativamente com arquivos recebidos semEXIF.

## Próximo passo exato

1. Consultar CI39122555216 (e39122412407), ler logs reais e corrigir falhas se existirem. Não dispensar testes/tolerâncias para ocultar diferenças. Nenhuma mudança de app após fonte final aprovada sem nova validação.
2. Gerar APK assinado para teste somente após etapa estável, autorizado pelo dono. Próximo build esperado70/versionCode1070, conferir real. Workflow nova-release.yml original bloba82bc113b2de207b7a3e9a9660cc19af520167ff. Usar gatilho push temporário desta branch mantendo guarda de publicação skipped; após run surgir restaurar blob original imediatamente. NÃO workflow_dispatch (publicaria release), não criar nova chave nem pedir segredos. Conferir testes/lint/JPEG200MP/modelos/Depth=True/certificado-versionCode/upload e publicação skipped. Só então publicar link do artefato real no handoff/STATUS/conversa.
3. S24: confirmarFoto/Retrato/altares/apoio; medir no mesmo cenário, luz e temperatura. Comparar JPEGs originais (localmente) e diagnóstico com óptica confirmada/compatível, ISO/exposição reais, quadros aproveitados/rejeição, detalhes/cores/texturas/ghosting/halos, tempos por etapa e memória/térmica. Não encerrarP0 nem prometer60–79s resolvidos sem medir. Fonte rápida0.26.2 preservada; novo estimador não adiciona inferência/modelo/blur.
4. Se5x usar3x confirmada, avaliar escolha explícita e segura de física5x com perfil suportado e fallback comunicado; não forçar em sessãoRetrato que travava, nem confundir maiorISO da tele com má calibração sem teste. Continuam abertas aquisição7–9s, congelamentos fora da rajada e escala energética não confiável.

## Mensagem para Claude

Continue Nova Camera em chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, pelo HEAD atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topoSTATUS e P0_NOTURNO_0262.md. Fonte0.26.3 desta etapa7f6de132258a33827ae819e67e667a0d1237f4a1, CI39122555216 ainda a conferir; não invente aprovação/APK. Fix de ruído correlacionado e plano noturno impossível já passou em39122118028; RMS sintético11,65→6,27 não é S24. CaptureOptics separa óptica confirmada/compatível/desconhecida do zoom solicitado. Preserve modelos, oitoquadros/SR/resolução, desempenho0.26.2, atmosfera/tomNoturno, main e chave permanente; fotos pessoais foraGit. Termine validação e APK de teste autorizado sem release definitivo, depois compare fotos/diagnóstico reais no aparelho. Handoff histórico abaixo permanece íntegro.

---

## Histórico preservado — entrega anterior não é APK0.26.3

# Entrega P0 etapa 2 — Nova Camera 0.26.2 / build 69 (1069)

- **APK assinado confirmado:** [ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39089635870/artifacts/12122029001). Artefato12122029001,132619077 bytes, digest **do ZIP** sha256:ba236736aac0688b8fd9f6eb317fb945f610a4c0ad6baa63568ed3f380d9ec80. Expira2026-11-17T02:26:55Z. Extrair ZIP e instalar app-release.apk sobre a versão anterior; assinatura permanente preservada.
- [Actions39089635870](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39089635870), job117563423216: **277 aprovados,5 dispensados,0 falhas**, lint, codec JPEG sintético200MP (RSS host49868KiB), NAFNet/Depth Anything, assembleRelease, pesos offline (Depth=True), continuidade do certificado/versionCode e upload aprovados. Publicação **skipped**; nenhum release criado. Run_number69 e fórmula versionCode1000+run_number=1069, versionName0.26.2 na fonte.
- Fonte app `beb201fd4fb661b93aaaf7f7c7ad35a4b4badf9b`, CI39089312391 aprovado. Fonte APK `3cad7b111e164e7b395cfc111b26dc342be79b34`; nenhum app alterado após aprovação. Gatilho original restaurado em `0b82310fb281f03b044a440529aee3c228ac5439`, blob`a82bc113b2de207b7a3e9a9660cc19af520167ff`, conferido no HEAD37214a1. Main/assinatura/modelos/permissões preservados. Buscar HEAD atualizado antes de continuar; só documentação muda nesta entrega.
- **P0 permanece aberta.** Equivalência e microbenchmarks Linux executados, memória de buffers calculada; ainda sem comparação física nova de velocidade, aquisição, PSS, térmica, autonomia ou qualidade no S24. Diagnóstico completo e fotos anunciados ainda não anexados nesta etapa. Os estados “APK pendente” abaixo são histórico superado.

## Próximo passo para Claude

Receber e comparar os testes físicos da0.26.2 com o baseline0.25.3 de P0_ETAPA2.md, seguindo P0_ETAPA2_VALIDACAO.md. Priorizar intervalos pedido/início/entrega da rajada de oito quadros, congelamentos fora da rajada e escala energética. Não gerar outro APK nem declarar otimização completa sem necessidade/evidência. Não enfileirar oito JPEGs simultaneamente nem reativar ZSL às cegas. Usar subtimings para escolher o próximo gargalo do acabamento. O código e as pendências detalhadas permanecem no handoff abaixo.

## Texto pronto para enviar ao Claude

Continue a Nova Camera em github.com/chrystianmax73-dev/nova-camera, branch codex/video-estabilidade-0-20, pelo HEAD remoto atualizado. Leia inteiro docs/pendente/NOVA_HANDOFF_CODEX.md, topo de docs/STATUS.md, docs/pendente/P0_ETAPA2.md e P0_ETAPA2_VALIDACAO.md. A etapa de código0.26.2/build69(1069) foi entregue assinada no Actions39089635870, artefato12122029001;277 testes aprovados/5 dispensados, lint, modelos offline e certificado verificados, release skipped. Fonte appbeb201fd4fb661b93aaaf7f7c7ad35a4b4badf9b. Kernels JNI equivalentes, mapas SR liberados e foco/rajada protegidos; não há melhora física comprovada. Priorize os testes reais no S24 e investigue os7,4–9,1s de aquisição, congelamentos sem efeitos, memória e escala de corrente. Preserve oito quadros, resolução/fusão/qualidade, main, modelos, chave permanente, tom do Noturno e fotos pessoais fora do Git. Faça commits pequenos e testes/compilação/lint pelo Actions; não publique release definitivo automaticamente. Documente limites e atualize o handoff antes de encerrar.

---

## Histórico preservado — estados de geração abaixo foram superados

# APK de teste 0.26.2 em geração — build69 (1069 esperado)

- Fonte app `beb201fd4fb661b93aaaf7f7c7ad35a4b4badf9b` aprovada no CI39089312391. Fonte APK `3cad7b111e164e7b395cfc111b26dc342be79b34`; nenhum app alterado depois.
- Run assinado: https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39089635870 , build69, job117563423216. APK ainda NÃO confirmado. Próximo passo: conferir job inteiro, testes/lint/JPEG200MP/modelos/assembleRelease/offline Depth=True/certificado-versionCode/upload, publicação skipped; depois registrar artefato real e avisar o dono.
- Workflow original já restaurado em `0b82310fb281f03b044a440529aee3c228ac5439`, blob `a82bc113b2de207b7a3e9a9660cc19af520167ff`. Não disparar outro build, workflow_dispatch ou release. Confirmar resultado do run existente.
- P0 ainda exige S24: nenhuma melhora física de aquisição, PSS, térmica, autonomia ou qualidade comparativa declarada. Detalhes e texto para Claude abaixo. Estados antigos superados por este registro.

---

# Atualização: fonte 0.26.2 aprovada; APK ainda não confirmado

Fonte app `beb201fd4fb661b93aaaf7f7c7ad35a4b4badf9b`, CI final39089312391 aprovado em assembleDebug/testDebugUnitTest/lintDebug. CIs intermediários39089067201 e39089230751 aprovados. Nenhuma alteração de app posterior. Preparar apenas APK assinado para teste pelo fluxo autorizado, mantendo publicação skipped; ainda não informar APK disponível. Handoff completo e texto para Claude logo abaixo, com estados antigos de CI superados por esta atualização. P0 física continua aberta.

---

# P0 etapa 2 — 0.26.2 em validação, otimização física ainda aberta

## Estado para continuar com Claude ou Codex

- Trabalhar somente em `codex/video-estabilidade-0-20` do repositório `chrystianmax73-dev/nova-camera`. Buscar HEAD remoto atualizado e ler este arquivo inteiro, topo de STATUS, P0_ETAPA2.md e P0_ETAPA2_VALIDACAO.md antes de editar. Main, histórico e assinatura permanente preservados.
- Fonte de app mais recente desta etapa: `beb201fd4fb661b93aaaf7f7c7ad35a4b4badf9b` (0.26.2). CI final `39089312391` em andamento no momento deste registro: não considerar aprovado sem consultar. Commits de documentação/gatilho posteriores podem mudar HEAD sem mudar app.
- Base `e14795c8a94863ae044c7d7394a403b8665da13f`, CI39087054435 aprovado antes de modificar app. Baseline físico0.25.3/1068: Retrato79,17s/65,80s, Foto60,44s; oito quadros7,41–9,10s; align9,82–10,06s; SR27,74–31,41s; saída49/28MP; PSS962,5MiB; CPU136,5%; bateria36,2→39,6°C. Diagnóstico completo e fotos anunciados, ainda não anexados nesta etapa. Não fingir análise dessas imagens.
- Nenhum novo APK confirmado neste registro. O APK anterior0.25.3/build68/1068 permanece no link histórico abaixo. Não enviar esse APK como atualização nova. Depois que a fonte final passar, gerar só APK de teste autorizado pelo fluxo existente, sem release definitivo automático.

## Alterações e evidências

1. `70698ec` baseline documentado; CI39088156612 aprovado.
2. `cad14a7` versão0.25.4: potência não confiável enquanto unidade do firmware não for confirmada; mantém bruto e hipóteseµA, nunca multiplica1000 por magnitude. Registra NOW/AVERAGE/CHARGE_COUNTER/ENERGY_COUNTER e deltas. CI39088313232 aprovado. Nenhuma calibração física concluída; BatteryStats externo precisa ADB do dono, não permissão de sistema no app.
3. `3a885bb`0.26.0: núcleos C++ próprios para Align.search/SuperRes.mergeTile/Detail.applyBand na biblioteca novadenoise existente; Kotlin preservado. Primeira CI39088522688 falhou com exceções C++ desabilitadas. `3bf8835` corrigiu -fexceptions só nesse fonte e acrescentou suavização/busca SSD da SR. CI39088782041 aprovou assembleDebug/testDebugUnitTest/lintDebug. Regressões compilam JNI no Linux e exigem pixels/decisões exatamente iguais aos Kotlin em ruído, movimento, cores, pele, bordas e subpixel. Sem fast-math/FMA. Não executar modelos/thread novos.
4. `74337c6`0.26.1: não reter sete mapas de confiabilidade que a SR não consulta, mantendo cálculo/rejeição/campos/fusão. Economia de buffers calculada~83,3MiB em4080×3060/fator2, NÃO PSS medido. Regressão de oito quadros compara saída com/sem retenção; CI39088922669 aprovado. Não reduz automaticamente escala2×→1,5×; tenta coletar temporários uma vez sob pressão e, se não couber, preserva original com aviso, sem vender28MP como49MP. Mantém proteção OOM.
5. `60772ad`/`8c0aa01`/`beb201f`0.26.2: foco só confirmado com sucesso AF; motivos de dispensa separados de não suportado; callbacks antigos não alteram nova captura/obturador/vigia/EV/zoom. StillCaptureLease adia recuperação só dentro de prazo por pedido e total; não bloqueia bind intencional nem elimina recuperação real. Registra óptica de STILL_CAPTURE quando a HAL informa, EXIF independente. Estado térmico severo adia alta qualidade explicitamente antes da rajada; se aquecer depois, original em vez de processar só1/8. Listas da aquisição liberadas após transferência; UI indica aquisição terminada. Validar esses commits pelos seus Actions, não presumir aprovação.
6. Diagnóstico: heap Java/nativo entrada/saída das etapas (não pico/PSS), faixas JPEG/tempo/cache, detalhe/tons/pouca luz/crominância/desfoque separados. PhotoTrace128eventos/8fotos. Margem de cache JPEG não reduzida sem medir.

Benchmark REAL do CI39088782041, grupos de três após aquecimento: Kotlin→JNI em ms: alinhamento229,113→158,048; blocoSR217,378→47,057; SSD36,927→29,190; detalhe53,707→28,172. É microbenchmark Linux, não tempo total49MP, S24, GPU, consumo ou autonomia. Outra corrida39088922669 também aprovada e imprimiu tempos; variabilidade de servidor é esperada.

## Pendências prioritárias — próximo passo exato

1. Conferir CI39089312391 da fontebeb201f e CIs intermediários39089067201/39089230751. Se falhar, ler logs concretos e corrigir; não remover testes/reduzir tolerâncias para ocultar diferenças. Só gerar APK após fonte final aprovada.
2. Gerar APK assinado para teste com o workflow existente, preservando secrets/chave. Não usar workflow_dispatch pois o fluxo publica release nesse evento. Procedimento já autorizado e usado: adicionar temporariamente esta branch ao push de nova-release.yml, mantendo guarda que pula publicação para push nessa branch; após o run surgir, restaurar imediatamente o blob original `a82bc113b2de207b7a3e9a9660cc19af520167ff`. Esperado próximo build69/1069, mas conferir valor real. Conferir testes/lint/JPEG200MP/modelos offline (incluindo Depth=True)/certificado/versionCode/upload e publicação skipped. Só então registrar artefato/link/digest/expiração. Não alterar app depois de fonte aprovada sem nova validação.
3. No S24: medir0.26.2 nos mesmos casos do baseline, oito quadros usados/aceitos, dimensão, detalhe/ruído/bordas/pele, captura por quadro/total, align/SR/acabamento/gravação, PSS/CPU/térmica. Sem isso P0 NÃO está concluída. Protocolo detalhado emP0_ETAPA2_VALIDACAO.md. Não há melhora física comprovada nesta sessão.
4. Investigar7,4–9,1s da aquisição com diagnóstico completo pedido/início/entrega e timestamps. Não foi implementado pipeline Camera2 novo nem disparo paralelo de oito JPEGs; não ativar ZSL de novo às cegas (travamentos/qualidade ruins históricos). Comparar CameraX/HAL/AF/exposição e somente então escolher aquisição limitada segura.
5. Congelamentos fora da rajada continuam abertos, inclusive sem GL/análise. Proteção de rajada não prova solução geral. Distinguir falta de CaptureResult de ausência de apresentação na tela; não culpar GPU por tempo wall/eglSwapBuffers. Regressão de interrupções, foco, lentes3x/5x/10x, frontal/traseira e vídeo comum/assinatura/cinema ainda física.
6. Confirmar escala energética via NOW/AVERAGE/deltas de carga/energia/BatteryStats/quedas controladas; não converter por magnitude, não usar5,8mW como consumo real. Medições do aparelho inteiro e sem GPU isolada; sem economia comprovada.
7. Após P0: retomar refinamentos de tracking/profundidade/acabamento temporal de vídeo já documentados, preservando Vídeo comum e cinema só no ambiente Retrato autorizado. Não aumentar escopo antes da estabilidade/térmica.

## Ambiente e preservação

Neste ambiente `/workspace/nova-tracking` é snapshot parcial sem `.git`, Gradle/SDK/JDK completos indisponíveis. Validação real via Actions autorizada pelo dono; Git publicado por API com expected_sha, sem força. Não inventar execução local. Não pedir/expor segredos, gerar chave nova, mudar assinatura, adicionar INTERNET, copiar código proprietário, alterar tom do Noturno ou colocar fotos pessoais no Git. Manter oito quadros/escala/fusão/recursos; avisos de incapacidade não são entrega de qualidade reduzida.

## Texto para enviar ao Claude

Continue a Nova Camera em `chrystianmax73-dev/nova-camera`, branch `codex/video-estabilidade-0-20`, pelo HEAD remoto mais recente. Leia inteiro `docs/pendente/NOVA_HANDOFF_CODEX.md`, topo de `docs/STATUS.md`, `docs/pendente/P0_ETAPA2.md` e `P0_ETAPA2_VALIDACAO.md`. A fonte0.26.2 desta etapa ébeb201fd4fb661b93aaaf7f7c7ad35a4b4badf9b; consulte atualizações de entrega acima antes de usar IDs históricos. Prioridade P0: eficiência e latência mantendo8quadros/alta resolução/fusão/qualidade, confirmar escala de corrente e investigar prévia/foco. Kernels JNI equivalentes e liberação de mapas SR implementados; ganhos de microbenchmark Linux não comprovam melhora física. Confirme CI/APK reais e teste no S24 antes de concluir otimização. Próximo ponto técnico é aquisição7,4–9,1s com intervalos reais pedido/início/entrega e congelamentos fora da rajada. Preserve main, histórico, modelos, chave permanente, Noturno e mídia pessoal foraGit; não publique release definitivo automaticamente. Faça commits pequenos e compile/teste/lint pelo Actions. Se faltar tempo, preserve handoff completo.

---

## Histórico anterior preservado — números e estados anteriores não substituem este registro

# Entrega P0 confirmada — Nova Camera0.25.3/build68 (1068)

- Branch única `codex/video-estabilidade-0-20`, origem/main preservadas. Fonteapp `3634fe000a70eb63127b5b6fba50dd39eb1805bf`, fonteAPK `11294a4d579e33f39066d75356f4ddebe295fb0b`; buscar HEAD atualizado antes de retomar. Nenhuma alteração deapp após aprovaçãoCI39085985023.
- **APK real:** [ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39086391072/artifacts/12120593095), artefato12120593095,132595692bytes, digest **doZIP** sha256:f4862631cc0244863fdbcaeea8699c9f505cab7e089f29c0c7abe051136940ca; expira2026-11-17T01:32:18Z (16/11 à noite emBrasília). Assinatura permanente conferida; nenhuma keystore nova/segredo solicitado/INTERNET/mídia pessoal enviada aoGit. Instala sobre versão anterior.
- [Build39086391072](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39086391072), job117553756728, concluído com sucesso: **267 aprovados/5 locais dispensados/0falhas**; lint, JPEG sintético200MP, NAFNet/DepthAnything, assembleRelease, pesos offline/profundidade=True, certificado/versionCode e upload reais aprovados. Publicação **skipped**, nenhum release/tag criado. Gatilhooriginal já restauradod918218de608bdc4f66c97cda16eb6be20888a8e, bloba82bc113b2de207b7a3e9a9660cc19af520167ff. CIs fonteassinada39086391077/restauração39086398000/handoff39086417350 aprovados.
- Baselineantesdoapp:c56ee20/39083686479 aprovado e docac01204/39084860093 aprovado. Etapas0.25.1corrigida0a2d1bb/39085319391,0.25.2b814212/39085617083 e0.25.3final3634fe0/39085985023 aprovadas. Falha inicial39084985949 preservada: EfficiencyTest exigia reescala indevida850→850000µA; corrigido conforme BatteryManager, sem apagar teste. Histórico abaixo continua íntegro, estados “APK pendente” foram superados por esta entrega.

## Etapa inicial concluída em código

- Energia emµA daAPI, sem inferir mA por magnitude; bruto/sinal/unidade/tensão/potência/conexão/temperatura/modalidade/n válido visíveis, sentinelas preservadas/rejeitadas para potência. Não explica sozinho14W anteriores. Auditoria SessionPerformance e suas limitações documentadas.
- PhotoTrace por disparo: AF/callback-sucesso/timeout,8pedidos/inícios/entregas/cópias e intervalos dos buffers, rajada/fila/decode/encode/align/SR/nusado/dimensão/modelos/refino/render/gravação. Histórico8disparos/80eventos, parcial durante aquisição, origem mesmo ShotTimer. Wall, não digitalizador/GPU. JPEG fecha antes de EXIF; prioridade da aquisição/processamento suspende novas inferências da prévia, limpa máscara/cache com aviso, sem rebind. Kernel em voo termina. Guard evita novo disparo sobreAF/rajada; obturador libera após aquisição, processamento na fila3 existente.
- GL suprime cópia+4blur+cópia intermediária descartados no Retrato sem máscara/contribuição; preserva calibração, transição e blur válido/shaders/raios/intensidade. Cinemajá tinha parte disso0.24.6. Cache de localizações por programa; TexSubImage emmesmageometria. FBO/logo já eram cacheados. GlPerformance mede wall/preparação/update/copy/blur/compose/swap/upload, cadência render e alocações/bytes conhecidos até1Hz, semglFinish ou falso tempoGPU.
- EntregaGL coalescida emuma máscara pendente, cinema750ms desde análise e não upload; novaentrada invalida máscara/temporal/calibração. LiveMediaPipe extrai só pessoa/fundo (1planoKotlin emvezde4) com pixels idênticos ao caminho anterior no teste; foto final mantém cabelo/rosto/completo. Diagnóstico separa carga/entrada/chamada/extração/depthpreparo/nativa-com-cópias/resultado ebackendsolicitado. Modelos/delegates/algoritmos e oitoquadros/outScale2 preservados.
- Todos os avanços0.25.0 permanecem: vídeo comum semdepth, cinema por gestoRetrato, tracking, marca, acabamento leve opcional, originais/fotos/HDR/Noturno/zoom/SR. Nenhum denoise temporal novo ou promessa de qualidade cinematográfica premium nesta investigação.

## Limites e próximo passo exato

**Sem S24 conectado/benchmark controlado nesta sessão.** Feedback “Testei e tá bom” é da0.25.0, não mede0.25.3. Não declarar economia de energia, menor temperatura, ganho de latência, sucessoSR/qualidade física ou custoGPU como comprovados peloCI. Baseline registrado é código/CI, tempos físicos não medidos. Instrumentação tem custo ainda não quantificado.

1. InstalarAPK68 e executar [P0_DESEMPENHO_BASELINE.md](P0_DESEMPENHO_BASELINE.md) / [P0_DESEMPENHO_RESULTADOS.md](P0_DESEMPENHO_RESULTADOS.md): aparelho fora do carregador, mesma cena/brilho/lente/perfil/temperatura, alternarAB/BA comAPK67 e5repetições, carga inicial separada de steady state. Retrato3x/5x/10x, altares8quadros; copiar Diagnóstico/Etapas fotográficas após disparos, conferir nSR/dimensão/qualidade de detalhe, ruído, bordas/textura e liberação do obturador/retorno da prévia. Guardar JPEG/MP4 pessoal localmente. Separar fimda aquisição de foto final pronta.
2. Pares isolados de vídeo comum/tracking/compositorsem máscara/depth/acabamento, medir energia/CPU/PSS/GL/MP4/térmica com condições iguais. ContagensGL podem incluir callbacks de mesma textura; CaptureResults eGL não substituemPTSMP4. GPU/energia por acelerador e alocações internas ainda não medidos. Excluir transições de grupos de SessionPerformance.
3. Prioridade seguinte conforme dados: localizar AF/3A/entrega/JPEG/align/SR; não enfileirar8JPEGs às cegas, reduzir48MP/quadros ou trocar paraCamera2paralela. Política herdada pode adquirir8 e processar1sob calorsevero: avaliar escolha explícita antesda rajada (aguardar máximaqualidade/capturar perfilnormal), evitando desperdício sem nova redução silenciosa. FotografiaNova teve só instrumentação/prioridade/liberação, nenhuma aceleração significativa demonstrada ainda.
4. Validar cachedepthN doRetrato fotográfico emmovimento/seleção/idade (SceneGuard ainda específico do cinema); foto final faznovainferência. Profundidadesíncrona pode ultrapassar650ms e invalidar composição no cinema mesmo com segmentação rápida; medir prévia, não atribuir937ms fotográficos aela. Considerar separar resultados com filas/validade próprias somente após medir. CPU/Vulkan/delegateGPU precisam comparação física de custo total, precisão e térmica, não apenasinferência. Modelos não trocados.
5. Gestos/tracking/cinema/frontal/traseira/tele/assinatura/MP4 e estabilidade ainda exigem regressão física; seguirroteiro anteriorVIDEO_EVOLUCAO_S24_VALIDACAO.md. Refino avançado/denoise temporal continuam pendentes apósP0. Commits de documentação posteriores a esta entrega não mudam app e disparamCIautomaticamente; consultar estado real ao retomar.

## Texto para continuar

Continue NovaCamera emcodex/video-estabilidade-0-20 pelo HEAD remoto atualizado. Leia handoff inteiro/topoSTATUS eP0_DESEMPENHO_BASELINE/RESULTADOS. Entrega0.25.3/build68(1068), APKartefato12120593095/run39086391072, fonteAPK11294a4d579e33f39066d75356f4ddebe295fb0b, app3634fe000a70eb63127b5b6fba50dd39eb1805bf;267testesaprovados/5dispensados/lint/JPEG200MP/modelos/certificado/upload conferidos, release skipped, gatilho já restauradod918218.../bloba82bc113.... FonteaprovadaCI39085985023, sem alteraçãoapp posterior.0.25.0preservada. Próximo: tratar feedbackfísicoS24 com comparação controlada, tempos8quadros/SR e componentesGL/modelos, sem alegar otimização completa ou autonomia melhor. Investigar política severa adquirir8/processar1com escolhaexplícita e validadecachefoto, não reduzirqualidade/quadrosàscegas. Preservar main/chave/Noturno/HDR/fotos/SR/modelos e mídia pessoal foraGit. Nenhum novoAPK/release sem necessidade; publicar sótesteautorizado pelo fluxo permanente.

---

## Histórico preservado — estados anteriores superados pela entrega acima

# APK P0 em geração — 0.25.3, build68

- Fonte app3634fe000a70eb63127b5b6fba50dd39eb1805bf aprovada no CI39085985023; sem alterações de app posteriores. Fonte APK11294a4d579e33f39066d75356f4ddebe295fb0b.
- Build assinado68 (versionCode esperado1068, confirmar) em execução: https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39086391072 . **APK ainda não confirmado.**
- Gatilho original nova-release.yml já restaurado emd918218de608bdc4f66c97cda16eb6be20888a8e, bloba82bc113b2de207b7a3e9a9660cc19af520167ff. Publicação deve ficar skipped. Não disparar outro build/release nem alterar a assinatura.
- Próximo passo: conferir jobs do39086391072, testes/lint/JPEG200MP/modelos/assembleRelease/pesos/certificado-versionCode/upload e publicação skipped; só então registrar artefato real/link/digest/tamanho/expiração e entregar. Se falhar, ler erros reais. Toda análise, alterações, protocoloS24 e limites estão no handoff preservado abaixo e emP0_DESEMPENHO_BASELINE/RESULTADOS.md.
- Continuar porHEAD atualizado na mesma branch, prioridadeP0: números físicos antes/depois ainda não medidos, qualidade/autonomia/velocidade não comprovadas. Oitoquadros/resolução máxima/algoritmos intactos. Não confundir instrumentação ouCI com benchmarkS24.

---

# Investigação P0 — 0.25.3, fonte final aprovada; APK em preparação

- Branch única `codex/video-estabilidade-0-20`; HEAD base conferido `c56ee20975ac4c3cec28251da68cb4a85645a3e8`.0.25.0/build67 preservada; dono testou e disse “Testei e tá bom”, sem benchmark controlado. Main/original/chaveAndroid preservadas. Buscar HEAD remoto atualizado antes de retomar.
- Antes de editar o app: baselineCI39083686479 aprovado; registro `docs/pendente/P0_DESEMPENHO_BASELINE.md` publicado emac01204, próprioCI39084860093 também aprovado. Sem S24 conectado; linha de base estática/CI e números físicos não medidos estão separados.
- Fonte atual do app `3634fe000a70eb63127b5b6fba50dd39eb1805bf`,0.25.3. CI39085985023 concluído com sucesso em assembleDebug/testDebugUnitTest/lintDebug; APK ainda não confirmado. Etapa0.25.1 corrigida0a2d1bb aprovada39085319391;0.25.2/b814212 aprovada39085617083. Falha inicial39084985949: teste legado EfficiencyTest exigia reescala850→850000µA (264totais/1falha/5dispensados). Ajustado à unidade documentada, não removido.

## O que mudou / o que não foi demonstrado

- EnergyLog respeita µA daAPI, sem multiplicar por1000 por magnitude; registra bruto/sinal/tensão/unidade/potência/conexão/temperatura/modalidade/n válido e preserva sentinelas para auditoria. Leituras altas anteriores não passavam pela heurística, portanto isso não explica sozinho14W. Potência calculada é do aparelho inteiro, instantânea; semWh/GPU/ganhoautonomia demonstrados. Auditoria de SessionPerformance e limitações de intervalos/transições documentadas.
- PhotoTrace correlaciona foco/callback-sucesso/timeout, oito pedidos/inícios/entregas/cópias/intervalos dos buffers, rajada, fila, decode/encode, align/SR/quadros usados/dimensão, modelos/refino/render/gravação final. Mesmo relógio do ShotTimer, últimas8capturas/80eventos; estado parcial durante aquisição. Não mede digitalizador ou tempoGPU; decode por faixas está contido emalign/SR. Original/algoritmos8quadros/outScale2/48MP permanecem; nenhum perfil rápido/redução arbitrária foi adicionado.
- ImageProxy JPEG fecha após cópia independente, antes de EXIF. Análise pesada de prévia fotográfica pausa enquanto captura/processa, informa estado, limpa máscara/cache e não religa câmera; kernel já em voo termina e modelos permanecem na pausa curta. Prévia comum responde e obturador libera após aquisição como antes (fila3). Guard impede segundo disparo sobre AF/rajada em andamento. Ainda não há prova de melhora significativa na latência física.
- GL: suprime6passes descartados no Retrato sem máscara/contribuição, preserva calibração e todos os passes válidos/intensidade/shaders. Cinema já tinha parte dessa otimização0.24.6. Localizações por programa cacheadas e textura de máscara atualizada porTexSubImage em mesma geometria. FBOs/logo já eram cacheados, não alegar ganho novo. GlPerformance agrega wall/preparação/update/copy/blur/compose/swap/upload, contagens/médias/máximos, cadência render e bytes/realocações conhecidos, relatório até1Hz; semglFinish/mediçãoGPU falsa.
- FilaGL só mantém última máscara pendente; cinema conta validade750ms desde início da análise, não do upload. Novaentrada invalida máscara/temporal/calibração. Ainda não foi provada apresentação ou qualidade física noS24.
- PersonSegmenter.Live copia só pessoa/fundo (um plano em vez de4 Kotlin), mesmos pixels/clamp; foto final mantém cabelo/rosto/caminho completo. Modelos/delegate/entrada não alterados. Diagnóstico separa carga/entrada/chamada/extração e depthpreparo/nativa-com-cópias/resultado/backendsolicitado; arraysdepth eBitmapMPImage próprios continuam necessários. DepthDutyBudget/leases já eram0.25, não duplicar.
- Pesquisa pública Android/AndroidX/Khronos/MediaPipe, sem código proprietário/modelos/dependências/INTERNET novos. Engine/Nighttone/SR/assinatura e mídia pessoal preservados; nenhum arquivo pessoal foi enviado aoGit.

## Próximo passo exato

1. Conferir CI39085985023. Se falhar, ler logs reais, corrigir e repetir. Só após aprovação gerar APK assinado pelo nova-release.yml existente: push temporário nesta branch, preservar condição que pula publicação. Assim que runassinado aparecer, restaurar bloboriginal `a82bc113b2de207b7a3e9a9660cc19af520167ff`. Nunca workflow_dispatch (publicariarelease). Próximo build esperado68, confirmar.
2. Confirmar testes/lint/JPEG200MP/modelosNAFNet/Depth/assembleRelease/pesos/certificado-versionCode/upload e publicação skipped. Só entregar com artefato real; registrarfonte/run/job/id/digest/tamanho/expiração. Sem release definitivo ou alteração da chave.
3. TesteS24 A/B conforme P0_DESEMPENHO_BASELINE.md e análise completa P0_DESEMPENHO_RESULTADOS.md: mesma cena/lente/brilho/perfil/temperatura, fora do carregador,5repetições, separar carga de steady state e fim da rajada de foto final. Retrato3x/5x/10x8quadros/altares, detalhe/ruído/bordas/textura/nSR/MP reais, duplotoque/novodisparo/AF/prévia; vídeo comum/tracking/compositor semmáscara/depth/acabamento em pares isolados. Sem resfriamento artificial no meio da comparação.
4. Usar tempos para localizar gargalo real. Não enfileirar8JPEGs às cegas, reduzirquadros/48MP nem reescrever paraCamera2paralela. Investigar3A/entrega/fila/align antes de pipelining; perfil rápido só explícito/comprova de qualidade. CompararCPU/Vulkan/delegateGPU em dispositivo incluindo custo total e energia, não apenas inferência. Profundidade síncrona >650ms invalida composição no cinema: separar pessoa/depth só após medir este caminho;937ms do relatório fotográfico não é métrica da prévia. Não ocultar lentidão com máscara antiga.

## Texto para continuar

Continue NovaCamera emcodex/video-estabilidade-0-20 pelo HEAD remoto atualizado. Leia handoff inteiro/topoSTATUS e P0_DESEMPENHO_BASELINE/RESULTADOS.0.25.3 fonte3634fe000a70eb63127b5b6fba50dd39eb1805bf, CI39085985023 aprovado.0.25.1corrigida e0.25.2 aprovadas; primeira falha era testelegado da unidadeµA. Ainda semAPK0.25.3 nesta preparação. Termine validação e gereAPKteste pelo fluxopermanente, publiqueartifact com release skipped, restauregatilhooriginala82bc113..., confirmecertificado/modelos/testes/upload e entregue linkreal. Preservar8quadros/outScale2/algoritmos/chave/main/HDR/Noturno/SR e mídia pessoal foraGit. Tempos/GL/modelos/energia agora instrumentados, desperdícios estáticos removidos, sembenchmark físico/ganhoautonomia/latência significativa demonstrados. Próximo passo é feedbackS24 controlado antes de mudar aquisição/backend; não afirmar otimização completa.

---

## Histórico preservado — estados anteriores abaixo

# Entrega confirmada —Nova Camera0.25.0 build67 (1067)

- **APK assinado disponível:** [ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39083102925/artifacts/12119587294), artefato12119587294, 132578292 bytes, digest **do ZIP** sha256:3c3d12390e73e712c5b8f5a880afea68bbdca7096b2efa0126c0a0f27cb5bb5e. Expira2026-11-17T00:39:48Z (16/11/2026 à noite em Brasília). Mesmo certificado permanente, nenhuma keystore nova ou assinatura Android alterada. Sem release publicado;0.24.6/build66 preservada como versão anterior.
- [Build39083102925](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39083102925), job117543971724, concluído com sucesso: **254 testes aprovados,5 locais dispensados,0 falhas**; lint, JPEG sintético200MP, NAFNet/DepthAnything, assembleRelease, pesos offline/profundidade=True, continuidade de certificado/versionCode e upload reais aprovados. Publicação skipped.
- Fonte do app `503ded4df96674375121873f1f9ce387688feb18`, aprovada no CI39082782966; fonte do APK `e8ea4f2a220fda25272f6704bf01a1c4ef8a9261` (só workflow/docs além da fonte aprovada). Gatilho original restaurado em `7e10473755fd98f5bbec15199897d73999d9b4f0`, blob a82bc113b2de207b7a3e9a9660cc19af520167ff. CIs da fonte assinada39083102905, restauração39083129889 e handoff39083192960 também aprovados. Branch única codex/video-estabilidade-0-20; buscar HEAD atualizado antes de continuar. Main/original preservadas.

## O que foi entregue

- Vídeo/Vídeo Pro e quickvideo da Foto conservam captura normal, marca, tracking opcional e acabamento leve pós existente; não iniciam profundidade/compositor cinematográfico. Cinematográfico fica exclusivamente no vídeo iniciado pelo Retrato.
- Retrato conserva foto por toque. Segurar prepara/grava e soltar semlock finaliza; arrastar à direita inicia/prepara e bloqueia; tocar obturador finaliza lock. Subir/descer enquanto pressionado controla zoom já amortecido. Aba permanece Retrato e indicadorRETRATO·VÍDEO. Preparação até8s aguarda sessão/máscara; soltar/cancelar antes de pronto impede arquivo tardio.
- Ativar “Vídeo cinematográfico no Retrato”, confirmar perfil separadoFHD30fixo/EISoff no primeiro uso e fazer novo gesto. Confirmação não grava nem altera qualidade/FPS/EIS/autoFPS do Vídeo normal. Frontal/traseira respeitam capacidades. Compositor ausente ou máscara inválida não permite iniciar silenciosamente um vídeo anunciado com desfoque; calor severo recusa preparação. Perda de máscara durante gravação pode pausar efeito com aviso, como já previsto.
- Tracking por toque no RetratoNova usa AF contínuo fotográfico/AE suportados, ponto acompanha foto/máscara; vídeo mantém rastreamento opcional.3x/5x/10x apenas quando alcançáveis,10x pode ser digital; frontal tem seus limites. Mudança de sessão/lente/enquadramento pode exigir nova seleção; não é reidentificação semântica nem acompanhamento ininterrupto garantido. OEM mantém foco por toque quando não aceita regiões.
- Eficiência: ImageProxy copiado/fechado antes da inferência; bitmaps/MPImages com posse explícita fechados inclusive em erro; modelos liberados na saída/calor; operação antiga deixa de publicar e nenhuma fila ilimitada. Profundidade tem cooldown3× tempo real gasto (incluindo carga) quando domina o intervalo, sem alterar resolução/FPS da gravação ou aceitar máscara antiga. Relatório novo separa custo de cópia/liberação/segmentação/profundidade/total e estado de análise ao vivo; contador da marca inclui compositor.
- Pesquisa em APIs públicas CameraX/MediaPipe, sem código proprietário/modelo/dependência/permissão novos. Fonte e decisões em PESQUISA_RETRATO_ENERGIA.md. Motor de fotos/HDR/Noturno/zoom/SR, ajustes existentes e assinatura preservados; nenhuma mídia pessoal noGit.

## Próxima etapa exata e limites

- Instalar APK67 e seguir VIDEO_EVOLUCAO_S24_VALIDACAO.md. Começar por Vídeo comumFHD30fixo/EISoff fora do carregador e com aparelho frio, depois tracking, depois Retrato foto/vídeo separadamente; comparar pares com mesma cena/lente/brilho/tempo e separar carga inicial de estado já preparado. Reproduzir MP4 e confirmar dimensões/FPS/marca/desfoque/áudio/orientação/espelho, frente/trás/tele, gestos/interrupções/retorno à foto. Uma máscara ou retículo na tela não prova qualidade final.
- **Sem medição física desta versão**: recursos de tracking/cinema seguem experimentais. Não afirmar melhora de bateria, temperatura, bordas ou foco apenas com CI. Próximo trabalho deve tratar o feedbackS24 e medir as causas remanescentes antes de mais processamento pesado. Transições entre lentes, robustez/reidentificação e máscaras finas ainda exigem refinamento. Denoise temporal avançado continua pendente; pós atual é só ajuste leve opcional de sombras, original preservado.
- Evidência física anterior0.24.6: ZIP pessoal analisado apenas localmente1080×1920/AVC,508quadros,16.98s,29.92fps porPTS,marca horizontal; diagnóstico posterior mostrou rebinds, máscara indisponível mesmo emtérmica0 e consumo alto do aparelho. Não correlacionar outroMP4log29.32fps sem arquivo. Nenhuma mídia pessoal enviada aoGit. Esses dados não são validação da0.25.0.

## Texto para continuar

Continue Nova Camera emcodex/video-estabilidade-0-20 pelo HEAD remoto atualizado. Leia handoff inteiro e topoSTATUS. Entrega0.25.0/build67(1067), APK artefato12119587294 no run39083102925, fontee8ea4f2a220fda25272f6704bf01a1c4ef8a9261, app503ded4df96674375121873f1f9ce387688feb18,254 testes aprovados/5 dispensados/lint/JPEG200MP/modelos/certificado/upload verificados, sem release. Gatilho já restaurado7e10473755fd98f5bbec15199897d73999d9b4f0. Vídeo normal semprofundidade; cinema só por gesto noRetrato comperfil separadoFHD30fixo/EISoff confirmado, semalterar normal4K60. Direita inicia+lock, pressão/vídeo, vertical/zoom, trackingpor toqueRetratoNova, recursos/lease/MPImage/cancelamento/dutybudget/diagnóstico revisados. Primeiro trate meu feedback físicoS24 e meça consumo/temperatura comparáveis; não prometer otimização completa ou qualidade premium semprova. RoteiroVIDEO_EVOLUCAO_S24_VALIDACAO.md e pesquisaPESQUISA_RETRATO_ENERGIA.md. Preserve chave/main/fotos/HDR/Noturno/zoom/SR e mídia pessoal fora doGit. Próximos: refinar estabilidade/máscaras/tracking/transições e só depois denoise temporal compensado por movimento, original/áudio/PTS preservados.

---

## Histórico de preparação preservado — a entrega acima substitui estados pendentes abaixo

# APK0.25.0 em geração — próximo passo é confirmar entrega

- Fonte app503ded4df96674375121873f1f9ce387688feb18 **aprovada** em assembleDebug/testDebugUnitTest/lintDebug no [39082782966](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39082782966). Sem alterações de app posteriores.
- **Build67/1067 em execução**, ainda sem APK confirmado: [Actions39083102925](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39083102925), fonte e8ea4f2a220fda25272f6704bf01a1c4ef8a9261. Gatilho temporário já restaurado em7e10473755fd98f5bbec15199897d73999d9b4f0 (blob originala82bc113b2de207b7a3e9a9660cc19af520167ff). Publicação deve ficar skipped. Não disparar outro build sem necessidade nem publicar release.
- Próximo passo exato: consultar jobs/run39083102925, confirmar testes/lint/JPEG200MP/modelos NAFNet eDepth/assembleRelease/pesos/certificado-versionCode/upload reais e publicação skipped. Só entregar quando houver artefato real, registrar link/ID/digest/tamanho/expiração em STATUS/handoff. Se falhar, ler erro real; nunca anunciar APK indisponível.
- Após confirmar, orientar: Vídeo normal semprofundidade mantém preferências; Retrato→ativar “Vídeo cinematográfico no Retrato”, confirmar perfil separadoFHD30fixo/EISoff no primeiro gesto, fazer novo gesto; toque fotografa, segurar prepara/grava, direita inicia+lock, vertical durante pressão faz zoom, tocar finaliza lock. Tracking por toque no RetratoNova; frente/trás e3x/5x/10x alcançáveis ainda requerem S24. Sem afirmar bateria/qualidade física melhor.
- Fonte/pesquisa/testes/correções/limites e texto para continuar completos abaixo. A seção de CI ainda emexecução abaixo é histórico superado pela aprovação acima; APK continua pendente até confirmação real.

---

# Em validação —0.25.0, Retrato com vídeo e prioridade energética

- Branch `codex/video-estabilidade-0-20`, base remota8bbb343d63d7c91681e647d5568b8e3536f8f79f e baseline39075324814 aprovados. Fonte final atual do app `503ded4df96674375121873f1f9ce387688feb18`, CI completo [39082782966](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39082782966) em execução. Buscar HEAD atualizado antes de continuar. Nenhum APK0.25.0 gerado ainda; não reenviar0.24.6 como novidade.
- Etapas já aprovadas: recursos0.24.7/2f06da170b8ec9550e3545858e74794559f35217 no39082038819; escopo/perfil1f68b43e9e08c9c9186d5ddbd24a40eb48b14b9f no39082206619; gestos29a3ff6775fd14b76d1dcf16f7eee38c4849c8b6 no39082254109; métricas/cancelamento680630a80f87de3c63fcd6bf2cd9a2ee07a131d2 no39082341619; prontidão80dc2f0bb20b3b76fdcb50839f35a1f5b103d3a8 no39082511102. Revisões finais: diálogo/contador412e6faa12aeea9d885fc97ba091964df099a96f (39082598026), faixa30/30 deRetrato9e1e9b040701a438fe70087c7106eff83f5d7374 (39082695793) e diagnóstico de pausa503ded4...; conferir resultados reais. Não inferir aprovação de execução ainda ativa.

## Alterações autorizadas e implementadas

- Pedido atual autoriza implementar e entregar teste nesta noite: Vídeo comum preservado com tracking opcional por toque e pós-processamento existente; cinematográfico passa exclusivamente ao vídeo iniciado pelo Retrato; foto por toque, segurar/vídeo, direita/iniciar+lock, vertical/zoom, frontal/traseira e3x/5x/10x alcançáveis. Bateria e temperatura têm prioridade; qualidade não deve ser reduzida silenciosamente.
- `portraitVideoSession` distingue origem sem adicionar outro motor: controlador usa VIDEO temporário, UI mantém aba Retrato e indicadorRETRATO·VÍDEO. Perfil separadoFHD30/fixo30–30/EISoff confirmado em diálogo no primeiro uso, novo gesto obrigatório; configurações normais4K/60/EIS/autoFPS ficam intactas. Preferência `cinematicVideo` agora só atua no Retrato. Foto quickvideo continua comum. Não há novo modelo/dependência/permissão.
- Warmup8s começa antes do bind e aguarda câmera/estadoVIDEO/máscara; não cancela só porque GL antigo precisa de liberação assíncrona. Soltar semlock/cancelar/sair impede início tardio; direita bloqueia intenção e permite gravar após ficar pronto. Falha/ausência de compositor não inicia cinema como vídeo comum silenciosamente; calor severo recusa preparação antes de bind. Retorno à foto acontece após Finalize.
- Tracking de aparência permitido no Retrato Nova: AF contínuo fotográfico e AE conforme regiões disponíveis; ponto acompanhado alimenta seleção da foto e máscara de prévia. Extensão OEM mantém foco por toque quando não permite controle. Perda, lente/enquadramento e calor seguem interrompendo; não é reconhecimento semântico, reidentificação ou garantia em oclusão.10x pode ser digital.
- Recursos: copiar/fecharImageProxy antes de inferir, reciclarBitmaps emfinally, MPImages com posse própria fechadas inclusive emerro; MPImage.close recicla seuBitmap, por isso não pode receberemprestado o mesmoBitmap do chamador. Sem alterar pesos/algoritmo fotográfico. DepthDutyBudget limita frequência pelo custo real (carga incluída), cooldown3× custo, sem mudar vídeoFPS/resolução ou aceitar mapa antigo.
- Ao trocar modo, analisador deixa de aceitar/publicar imediatamente; fecha modelos na thread proprietária após operação nativa em voo. Pausa térmica libera modelos também emprévia foto. Diagnóstico novo de análise ao vivo mostra última cópia/liberação/segmentação/profundidade-carga/total/estado e atualiza pausas sem usar amostra antiga como ativa. Contador da marca inclui compositor e identifica caminho, mas não prova pixels.
- Testes: posse realBitmap/MPImage, copy→close→inferência/erro, orçamento, escopo/prontidão, MotionEventdireita/vertical/esquerda/diagonal/recusa, gesto interrompido, diálogo cancelar/aceitar sem mudar normalFPS/EIS/qualidade, callback deActivity destruída. Testes existentes de pixel/metadata/assinatura/fotos devem permanecer aprovados. Pesquisa pública e decisões em `PESQUISA_RETRATO_ENERGIA.md`; nenhum código proprietário.

## Próximo passo exato

1. Concluir CI39082782966 e conferir também39082598026/39082695793. Se falhar, ler logs reais e corrigir sem reduzir exigências. Código local é snapshot parcial `/workspace/nova-tracking`, não checkoutGit; publicar com GitHub create_tree/base_tree/create_commit/update_ref(expected_sha) na mesma branch.
2. Se aprovado, gerar APK assinado pelo workflow `nova-release.yml` existente. Adicionar esta branch ao gatilho push por uma única execução, manter guard que pula publicação para push desta branch. Assim que runassinado aparecer, restaurar blob originala82bc113b2de207b7a3e9a9660cc19af520167ff. Nenhuma alteração de assinatura/keystore/secrets. Confirmar testes/lint/JPEG200MP/modelos/assembleRelease/pesos/certificado/versionCode/upload e publicação skipped; só então entregar artefato real (próximo build esperado67, confirmar).
3. Registrar fonte/run/job/artefato/digest/tamanho/expiração emSTATUS/handoff, entregar link com instruções de uso. Sem publicar release definitivo. Fotografia, HDR, Noturno, zoom/SR e assinaturas anteriores preservados; nenhuma mídia pessoal noGit.

## Evidência física e limites

- ZIP de vídeo pessoal foi analisado apenas localmente:1080×1920/AVC,508quadros,16.98s,29.92fps pelosPTS, intervalomáx35.88ms, marca horizontal nosquadros amostrados; áudioAACstereo presente/decodificação semerro, sem prova perceptiva de sincronismo ou profundidade. Não foi enviado ao Git.
- Diagnóstico0.24.6 no S24/API37 mostrou câmera cinematográfica com máscara indisponível inclusive em térmica0, rebinds/STOP, tracking27 análises eperda, cópia6.6msmédia, consumo aparelho14.3W em2 amostras ePSSaté712MiB. OutroMP4log29.32fps não foi correlacionado aoZIP. Crashes listados são0.20.0antigos. Isso orienta investigação, não prova causa completa ou economia.
- Sem S24 conectado nesta sessão. Não declarar melhora de bateria/temperatura ou qualidadecinema com CI. Usar roteiro `VIDEO_EVOLUCAO_S24_VALIDACAO.md`, frio/fora do carregador eperfis comparáveis, incluindo autoFPSoff para30fixos. Medir antes/depois. Máscaras/contornos/foco reais, frontal/traseira/tele, áudio eMP4 requeremfeedback físico.
- Próximos refinamentos ainda pendentes: robustez/reidentificação tracking, máscaras temporais/cabelos/folhas, transições1x/3x/5x/10x e denoise temporal compensado por movimento. Pós atual continua ajuste leve de sombras opcional comoriginal preservado. Não afirmar otimização completa ou adicionar processamento agressivo.

## Texto para continuar

Continue NovaCamera emcodex/video-estabilidade-0-20 pelo HEAD remoto atualizado. Leia handoff inteiro e topoSTATUS. App0.25.0 final503ded4df96674375121873f1f9ce387688feb18, CI39082782966 ainda estava emexecução; etapas anteriores aprovadas acima. Primeiro concluaCI e gere APK teste assinado pelo fluxo existente com publicação skipped, restauregatilho original, confirme artefato/modelos/certificado/testes eentregue link real. Não há APK0.25.0 confirmado ainda. Vídeo comum agora semprofundidade; cinema só no Retrato comperfilFHD30fixo/EISoff confirmado semalterar normal4K60. Gestos direita/lock, pressão/vídeo, vertical/zoom e trackingRetratoNova; recursos/lease/MPImage/dutybudget/cancelamento/diagnóstico ajustados. Bateria éprioridade, mas ganhos e qualidade física ainda precisamdemedições S24. Preserve main/chave/fotos/Noturno/todas as qualidades/mídia pessoal fora do Git. Denoise e refinamentos avançados continuam pendentes.

---

## Histórico anterior preservado — o estado vigente é o descrito acima

# 0.25.0 — Vídeo comum separado do cinematográfico do Retrato (17/10/2026)

- Vídeo/Vídeo Pro e vídeo rápido da Foto não abrem profundidade nem o compositor cinematográfico, mesmo com preferência ligada. Mantêm tracking opcional leve, assinatura e acabamento pós local existente. Cinematográfico fica exclusivamente na gravação iniciada no Retrato, com perfil FHD/30/EISoff separado e confirmação explícita antes do primeiro uso, sem sobrescrever qualidade/FPS/EIS do Vídeo normal. Não há redução silenciosa.
- Retrato conserva foto por toque e gravação por pressão; aba visual continua Retrato, indicador identifica RETRATO·VÍDEO. Warmup limitado começa antes do bind, não cancela prematuramente quando a liberação GL é assíncrona, e nunca começa após soltar/cancelar. Controle normal retorna após Finalize.
- Tracking de aparência passa ao Retrato Nova com AF contínuo de foto/AE compatíveis e seleção acompanhada para a foto/desfoque; câmera/lentes/zoom respeitam capacidade real. Extensão OEM mantém foco por toque se não oferece esse controle. Sem reidentificação semântica nem garantia de tele óptica10x (zoom10x pode ser digital).
- Perfil do Retrato solicita faixa fixa30/30 à API; não herda a preferência de15–30 automático do Vídeo comum. Preferência normal permanece intacta e FPS efetivo continua medido após Finalize.
- Confirmação do perfil tem testes de diálogo reais: cancelar/aceitar preserva FPS/EIS/qualidade normais e tela destruída não aceita callback tardio. Contagem de desenhos da marca agora inclui uploads do compositor, identificando o caminho; não prova pixels do MP4.
- Cinematográfico solicitado não inicia arquivo se compositor estiver ausente/falho; calor severo recusa preparação antes do bind. Roteiro atualizado VIDEO_EVOLUCAO_S24_VALIDACAO.md e fontes/decisões em PESQUISA_RETRATO_ENERGIA.md.
- Estados sem quadro analisado (calor/acabamento) também atualizam o relatório ao vivo; não mantém uma amostra antiga como se análise ainda estivesse rodando.
- Analisador para de aceitar/publicar quadros imediatamente ao trocar modo; modelos fecham na thread proprietária, sem interromper kernel nativo em voo. Pausa térmica libera modelos também na prévia fotográfica. Diagnóstico agora separa última cópia/liberação, segmentação, profundidade/carga e total da análise ao vivo; não são métricas de GPU/autonomia.
- Gestos: direita inicia e bloqueia mesmo antes do tempo de pressão longa; segurar grava até soltar; subir/descer durante pressão aplica zoom suave existente. Direção assinada evita bloquear para a esquerda, reconhecimento vertical evita lock por diagonal, gesto recusado não vira foto/zoom. Testes reais MotionEvent/Looper cobrem esses casos e mantêm toque, cancelamento, segundo dedo, bind e timeout. Inclui recursos0.24.7. CI pendente, sem APK novo ainda. Qualidade visual, temperatura e autonomia no S24 continuam sem comprovação desta revisão; não declarar otimização completa.

---

# 0.24.7 — recursos da análise e prioridade energética (17/10/2026)

- Base8bbb343d63d7c91681e647d5568b8e3536f8f79f conferida; baseline Actions39075324814 aprovado antes de editar. Novo pedido autoriza Vídeo comum sem cinema e gravação cinematográfica no Retrato, gestos/zoom/tracking, bateria primeiro. Próxima etapa0.25.0 fará essa separação; ainda não concluída nesta entrada.
- ImageProxy é copiado e fechado antes de segmentação/carga/inferência, com reciclagem de bitmaps mesmo em erro. Antes mantinha buffer da câmera durante todo o trabalho pesado. MPImages de entrada/saída fechadas explicitamente; entrada recebe Bitmap próprio porque MPImage.close recicla seu Bitmap. Foto do chamador preservada; algoritmos/pesos iguais.
- Orçamento de profundidade usa tempo real gasto, incluindo carga: pausa de3× o custo após terminar (ocupação alvo máxima25% quando esse limite domina), sem alterar resolução/FPS ou aceitar máscara velha. Não prova redução de temperatura/autonomia; precisa A/B no S24. Térmica visível entre threads.
- Pesquisa pública antes das mudanças: AndroidX ImageAnalysis.java (KEEP_ONLY_LATEST, fechamento de ImageProxy), MediaPipe MPImage.java/BitmapImageContainer.java (posse e reciclagem de Bitmap). Nenhum código proprietário copiado. Testes novos cobrem ordem copy→close→inferência, exceções, duty budget e posse real de Bitmap/MPImage. CI pendente.
- Feedback0.24.6: MP4 pessoal recebido e analisado localmente, não enviado ao Git;1080×1920/AVC,16.98s,508quadros,29.92fps pelos PTS, marca horizontal presente. Diagnóstico separado registrou29.32fps e máscara indisponível mesmo em estado térmico0; sem correlação segura entre os arquivos. Consumo do aparelho chegou a14.3W em2 amostras, sem atribuição por acelerador/A/B. Tracking27 análises,cópia6.6ms média,perda/ambiguidade. Não declarar cinema estável ou economia comprovada.

---

# Entrega confirmada — Nova Camera 0.24.6 build66 (1066)

- **APK assinado disponível:** [ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39074742550/artifacts/12116148402), artefato12116148402, 132573776 bytes, digest **do ZIP** sha256:ddbbae52c218a8c72713bb10487bb89229c6e7588b8b61965fb2bc07de70b793; expira 2026-11-16T22:28:41Z. Atualização de teste com a chave permanente, sem release publicado.
- [Actions39074742550](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39074742550), job117519451049, concluído com sucesso. **244 testes aprovados, 5 locais dispensados, 0 falhas**; lint, JPEG sintético200MP, modelos NAFNet/DepthAnything, assembleRelease, pesos offline (profundidade=True), continuidade do certificado/versionCode e upload aprovados. Publicação skipped.
- Fonte do app `da1e33b42a65c71a41c91e7235cc8745bd42d15d`, aprovada no CI39074393867. Fonte do APK `afcf41f1667ccec3abbf4982c9c242acdb45bf26`; código idêntico ao aprovado, só gatilho temporário diferente. Gatilho original restaurado em `25c8445411a2c930ca63b201de5048d6a19947c1`, blob a82bc113b2de207b7a3e9a9660cc19af520167ff. CIs da fonte assinada39074742577, restauração39074769248 e handoff39074823529 também aprovados. Buscar HEAD atualizado antes de continuar; docs posteriores não alteram aplicativo.
- Corrigidos o fluxo de espera de máscara sob calor (escolha explícita de comum na mesma qualidade, sem início tardio), vigia por sessão independente de metadados opcionais e sem repetição em modos sem callback/prévia isolada, geometria da marca compartilhada e passes de desfoque sem análise útil. Notas antigas descartadas por geração. Sem mudar assinatura Android, INTERNET, dependências, Noturno ou motor fotográfico.
- **Validação S24 ainda necessária:** rastreamento/cinematográfico são experimentais; teste frio/fora do carregador conforme VIDEO_EVOLUCAO_S24_VALIDACAO.md. Verificar foco/máscaras/contornos e marca de pé nos pixels do MP4, frente/trás/espelho, áudio, interrupções, fallback e dados reais de FPS/resolução. Diagnóstico0.24.3 confirmou comum1080×1920/AVC/29.97fps, não provou cinema/marca. Nenhum MP4 pessoal novo recebido ou enviado ao Git.
- Calor severo3 do feedback suspendeu modelos; potência não foi medida enquanto carregava e não há A/B/GPU/autonomia comprovados. Próximo: tratar feedback desta entrega e separar renderer de CaptureResult se houver congelamento com resultados ainda avançando; depois refinar máscaras/consumo com evidência. **Denoise temporal avançado/compensação de movimento permanecem pendentes**; acabamento atual é apenas ajuste leve de sombras opcional, original preservado.

## Texto para continuar

Continue Nova Camera pela branch codex/video-estabilidade-0-20 no HEAD remoto atualizado. Leia este handoff inteiro e topo STATUS. Entrega0.24.6/build66(1066), APK no artefato12116148402 do run39074742550, fonteafcf41f1667ccec3abbf4982c9c242acdb45bf26; fonte appda1e33b42a65c71a41c91e7235cc8745bd42d15d, 244 testes aprovados/5 dispensados/lint/compilação/modelos/certificado/upload verificados, sem release. Gatilho original já restaurado25c8445411a2c930ca63b201de5048d6a19947c1. Trate meu feedback do S24 antes de prometer estabilidade/qualidade de cinema ou autonomia; protocolo VIDEO_EVOLUCAO_S24_VALIDACAO.md. Máscara/tracking pausam no calor severo; alternativa comum explícita preserva qualidade. Marca orientada e vigia corrigidos em código, pixels MP4 ainda requerem aparelho. Próximo denoise temporal real só com testes sintéticos/compensação de movimento, original e áudio/PTS preservados. Preserve chave permanente/main/fotos/HDR/Retrato/Noturno/zoom/SR, sem INTERNET ou mídia pessoal no Git.

---

## Histórico de preparação preservado — a entrega acima substitui os estados pendentes abaixo

# Continuidade — 0.24.6 aprovada, APK66 em geração (17/10/2026)

- Branch `codex/video-estabilidade-0-20`; base conferida1f49b39bd9037f1a3a1a34e655dcf667fa50f680 e baseline39071730931 aprovado. Main, branch original e APK65/0.24.3 preservados. Buscar HEAD remoto atualizado antes de continuar.
- Fonte final do aplicativo `da1e33b42a65c71a41c91e7235cc8745bd42d15d`, **assembleDebug/testDebugUnitTest/lintDebug aprovados no Actions39074393867**. Térmica0.24.4 aprovada39073650405; vigia0.24.5 aprovado39073924133. Primeiras0.24.6 (39073994403 e39074083309) falharam no mesmo teste gráfico; revisão final rasteriza marca de pé antes de transformar, mantém tolerância e passou. Não omitir falhas históricas.
- **APK66/1066 ainda não confirmado:** execução assinada [39074742550](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39074742550), fonte `afcf41f1667ccec3abbf4982c9c242acdb45bf26`. Repetirá testes/lint, JPEG sintético200MP, modelos NAFNet/Depth, assembleRelease e certificado/versionCode antes do upload. Gatilho original já restaurado em `25c8445411a2c930ca63b201de5048d6a19947c1` (blob a82bc113b2de207b7a3e9a9660cc19af520167ff). Publicação deve ficar skipped; não criar release nem iniciar outro build sem necessidade.
- Próximo passo exato: consultar jobs do run39074742550, confirmar sucesso dos testes/lint/modelos/compilação/certificado/upload, publicação skipped e artefato real. Só então entregar link do ZIP contendo app-release.apk0.24.6, build66(1066), registrar ID/tamanho/digest/expiração em STATUS/handoff. Se falhar, ler erro real e corrigir; nunca anunciar APK antes do upload.

## Feedback e correções desta revisão

- S24/API37/0.24.3 esteve em calor severo3, carregando,42.7–43.6°C durante a sessão: tracking/profundidade pausados corretamente. Erro de fluxo era solicitar aguardar máscara que não poderia chegar; agora obturador oferece Aguardar/Usar vídeo comum explicitamente, conserva qualidade/FPS/EIS e preferência para próxima sessão. Não inicia arquivo após diálogo/rebind; novo toque necessário. QuickVideo não aguarda máscara impossível se já severo. Proteções mantidas.
- Vigia não depende mais de ISO/exposição opcionais; resultados por geração, descarta aviso se câmera voltou antes do tratamento. Não vigia extensão OEM/alta velocidade sem callback nem prévia isolada por perfil recusado. Fotos também desligam vigia e respeitam limite antes das retiradas de efeito; vídeo mantém orçamento/Finalize. Isso corrige causas no código, não prova explicar todos os congelamentos do HAL/GL. Se captura avança mas tela congela, próximo diagnóstico deve separar entrega GL do sinal de CaptureResult.
- Marca vertical do screenshot: compositor usa geometria pública sensor→entrada/saída, recorte/rotação/espelho e bitmap desenhado de pé, textura só na geometria. Comum/OverlayEffect e liberação serial preservados, sem API interna/novo efeito. Testes nativos gráficos passaram; MP4 físico ainda pendente.
- Calibração antes sem máscara mantinha quatro passes de blur. Cinematográfico só copia para calibrar quando há amostra; blur apenas com máscara/transição visível. Não afirmar economia de GPU/bateria sem medição. Diagnóstico distingue máscara atual de compositor ligado/suspenso; callbacks antigos não sobrescrevem estado após troca para comum.
- Sem mudanças na assinatura Android, keystore, INTERNET, dependências, Noturno ou motor de fotos/HDR/Retrato/zoom/SR; nenhuma mídia pessoal no Git. Só versionName/build test logging/fluxo de vídeo e fontes citadas nas seções abaixo.

## Validação física e evoluções pendentes

- Diagnóstico do dono confirma somente MP4 comum1080×1920/AVC/29.97fps médios nessa sessão; marca desenhada na textura não prova pixels do MP4. Não há novo MP4 recebido ou S24 conectado. CPU/PSS são amostras reais, potência indisponível carregando, sem A/B/autonomia/GPU medidos. 937ms de profundidade é caminho fotográfico, não medição completa de cinema.
- Seguir `VIDEO_EVOLUCAO_S24_VALIDACAO.md`: frio/fora do carregador para referência, tocar novamente para tracking após pausa, cinemáticoFHD30/EISoff explícito, máscara/contornos, marca de pé/frontal/espelho, áudio/PTS, fallback por calor e troca de modos/qualidade. Não aquecer de propósito. Não chamar recursos de premium ou estabilidade física validada só pelo CI.
- Rastreamento/segmentação/cinematográfico ainda experimentais. Pós atual é abertura leve de sombras opcional; **denoise temporal avançado/compensação de movimento continuam pendentes**, assim como refinamento de contornos/reidentificação/consumo com material sintético e testes reais. Não aumentar inferência pesada no aparelho já quente sem medições; preservar original e prioridade da captura.

## Texto para continuar

Continue Nova Camera em codex/video-estabilidade-0-20 pelo HEAD remoto atualizado. Leia este handoff inteiro e topo STATUS. App0.24.6 finalda1e33b42a65c71a41c91e7235cc8745bd42d15d aprovado no CI39074393867; APK66/1066 em geração no run39074742550, fonteafcf41f1667ccec3abbf4982c9c242acdb45bf26, gatilho já restaurado25c8445411a2c930ca63b201de5048d6a19947c1. Primeiro confirme artefato/certificado/modelos/testes e publicação skipped e entregue APK real, sem release. Mudanças: escolha explícita de comum no bloqueio térmico, vigia por sinal de sessão/geração e limitado, marca orientada por matriz e passes GL sob demanda. Validação física S24 permanece pendente; denoise temporal avançado ainda não implementado. Preserve chave permanente/main/fotos/Noturno/todas as qualidades e mídia pessoal fora do Git.

---

# 0.24.6 — marca orientada no compositor e passes de desfoque sob demanda (17/10/2026)

- A captura real mostra marca vertical. Compositor desenhava logo nas dimensões cruas da saída compartilhada PREVIEW|VIDEO_CAPTURE, ignorando rotação posterior. Agora usa TransformationInfo pública (recorte/rotação/espelho/sensor→entrada) e SurfaceOutput.sensorToBufferTransform para orientar marca junto da imagem; atualiza textura só quando muda geometria. Vídeo comum/OverlayEffect preservado, sem efeito adicional ou API interna.
- A espera de calibração mantinha cópia reduzida e quatro passes de blur mesmo com máscara suspensa pelo calor. Calibração agora copia apenas quando recebe amostra; blur só trabalha com máscara ou transição visível. Proteções térmicas e validade mantidas; não é medição de autonomia/GPU.
- Primeira validação0.24.6 (39073994403) falhou em1 teste gráfico de249 (5 dispensados), comparação de pixels da marca; não aprovada. Revisão rasteriza texto/sombra de pé antes de transformar bitmap, em vez de transformar Canvas de texto; mantém a tolerância original e identifica a geometria nas falhas. Gradle passa a imprimir asserções completas. Nova validação pendente.
- Testes gráficos nativos com pixels em quatro rotações, espelho, recorte e saída compartilhada/já orientada; testes de orçamento cobrem pausa, primeira calibração, chegada/perda da máscara e Retrato fotográfico. CI ainda pendente; pixels do MP4 e temperaturas reais continuam exigindo S24. Inclui correções0.24.4/0.24.5.
- Notas tardias do analisador agora são descartadas por geração após troca para vídeo comum; desativar/reativar a preferência permite ligar o cinematográfico novamente nesta sessão. Roteiro físico atualizado com calor, fallback explícito, vigia e orientação.
- Próximo: conferir Actions e corrigir falhas reais, entregar APK assinado de teste sem release automático. Denoise temporal avançado e contornos/rastreamento premium permanecem pendentes; não adicionar processamento pesado ao aparelho já severamente quente sem medições controladas.

---

# 0.24.5 — sinal de sessão e recuperação limitada (17/10/2026)

- Sinal de atividade passa a contar todos os CaptureResults antes de ISO/exposição opcionais; callback por geração ignora resultados antigos. Aviso enfileirado só recupera se não chegou novo resultado nesse intervalo. Cadência diagnóstica também conta resultados sem campos opcionais; não mede apresentação na tela.
- Vigia não usa ausência de callbacks em extensão OEM/alta velocidade como prova de travamento e não religa prévia isolada após combinação recusada. Fotos param o vigia antes de tratar a parada e verificam limite antes de retirar efeitos, evitando repetição sem fim; vídeo conserva espera por Finalize e orçamento existente. Log inclui assinatura do compositor e estado da máscara.
- Regressões cobrem resultados sem campos opcionais, callback antigo, recuperação espontânea enquanto UI estava ocupada e perfis sem observação válida. CI pendente. Essas causas verificadas no código não provam explicar todos os congelamentos do HAL/GL no S24; teste físico continua necessário.
- Inclui escolha térmica0.24.4; nenhuma queda silenciosa de resolução/FPS, assinatura Android ou motor fotográfico alterados. Próximo: confirmar CI, examinar geometria da marca compartilhada, gerar APK com fluxo assinado sem release.

---

# 0.24.4 — feedback térmico do S24 e escolha de vídeo comum (17/10/2026)

- Base remota 1f49b39bd9037f1a3a1a34e655dcf667fa50f680 conferida; baseline Actions39071730931 aprovado antes de editar. Feedback real 0.24.3: calor severo3, carregando,42.7–43.6°C; potência indisponível. Rastreamento e máscara suspensos; esperar máscara não resolveria o calor. Sem mídia pessoal no Git.
- O botão oferece escolha explícita de vídeo comum nesta sessão, conserva resolução/FPS/EIS e preferência cinematográfica; reconfiguração não começa arquivo, exige novo toque. Gesto rápido sob calor não fica aguardando máscara impossível. Proteções térmicas mantidas, sem inferência forçada. Diagnóstico distingue compositor ligado de máscara atual.
- Diagnóstico do dono confirma MP4 comum1080×1920/AVC/29.97fps médios; não prova cinematográfico/marca nos pixels. Crashes listados são da0.20.0, não novos. CPU/PSS/cadência são amostras reais sem A/B controlado; não há prova de autonomia melhor. Profundidade fotográfica937ms não é medição do cinema.
- Compilação, testes unitários e lint0.24.4 aprovados no Actions39073650405 (fontef33a7815e003c4ac28082627f9a29c55d2eb1aa1). Próximo: corrigir vigia que usa metadados opcionais como sinal de vida e monitora sessões sem callback/prévia isolada; limitar mensagens/recuperações, testar e entregar APK assinado sem release. Investigar orientação da marca do compositor em etapa específica. Denoise temporal/qualidade avançada permanecem pendentes.

---

# Entrega confirmada — Nova Camera 0.24.3 build65 (1065)

- Branch `codex/video-estabilidade-0-20`, fonte do APK `aa580a42033fcf0cd2f8dad6469f6714bf389758`. Buscar HEAD atualizado antes de continuar; commits posteriores a esta fonte são apenas workflow/documentação. Main e branch original preservadas.
- **APK assinado:** [ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39070952578/artifacts/12114374400), artefato12114374400, 132570598 bytes, sha256:b7eeecf1131dac7f192dd7b13331b44e6c48d55f3917142c4bba97e62f96a038 (digest do ZIP, não do APK isolado); expira 2026-11-16T21:34:56Z.
- [Actions39070952578](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39070952578), job117508361596, concluído com sucesso: **235 testes aprovados, 5 locais dispensados, 0 falhas**, lint, JPEG real sintético200MP em host, NAFNet/DepthAnything, assembleRelease, pesos no APK/profundidade=True, continuidade do certificado Android/versionCode e upload reais aprovados. Publicação de release skipped. CI debug completo da fonte também aprovado39070952531; após restaurar gatilho39071002477 aprovado.
- Gatilho original restaurado em `c3cb1da536e837931027760184e234f2cd80c0d3`; blob do workflow original a82bc113b2de207b7a3e9a9660cc19af520167ff. Nenhuma keystore criada ou assinatura alterada; nenhum release publicado. Builds63/39070326049 e64/39070544992 cancelados, não entregues; não reenviar.

## O que foi implementado nesta etapa

1. Feedback real era captura de tela da0.22.0 com leitura lenta da prévia. PreviewView.bitmap/SurfaceView aguardava cópia full-size; PreviewFrameReader usa PixelCopy assíncrono já reduzido (até320px), uma solicitação, sem thread novo por quadro/sem stream novo no rastreamento. TextureView alternativo copia direto em destino pequeno. Geração/fechamento/erro/prazo controlados. Não foi medida latência nova no S24.
2. Cinematográfico **experimental** reutiliza PortraitPreview/MaskAnalyzer/DepthEstimator/PersonSegmenter, alvos PREVIEW|VIDEO_CAPTURE e marca no mesmo renderer. Ativação explícita com FHD/30/EISoff; demais perfis bloqueados sem reduzir escolha. Toque mapeia visor→sensor→análise; máscara vence e profundidade tem orçamento, calor/atraso/movimento retiram máscara inadequada. Não é profundidade métrica, semântica ou prova de bordas premium.
3. Troca entre OverlayEffect comum e compositor aguarda cleanup real na fila GL, inclusive slot já vazio com liberação pendente. commonEffectPending só limpa depois da barreira. Falha GL durante gravação finaliza arquivo antes de religar; callbacks de geração antiga não alteram a nova.
4. QuickVideoWarmup aguarda câmera/máscara antes de consumir START do gesto Foto/Retrato, até8s, cancela ao soltar/restaurar/sair/trocar sessão. Testes de Handler/Looper cobrem começo único, sem começo tardio, troca e timeout. MP4 físico do gesto ainda pendente.
5. Primeiro acabamento local opcional: segurar miniatura, confirmar; atéFHD/60/SDR AVC/HEVC/2min. Amostra8quadros e aplica abertura muito suave de sombras constante no clipe, ou não gera cópia se não precisar. Media3 GPU/codecs sem fallback de resolução; original intocado e nova cópia na galeria, progresso/cancelamento e comparação de reprodução com par persistido. Nova captura, calor severo e pouca memória cancelam; depende de o processo continuar vivo.
6. Exportador lê MP4 efetivo e confere dimensão/orientação visual, MIME, todos os PTS/quantidade de quadros, bytes SHA de áudio/canais/taxa e sincronismo; divergência descarta cópia. Isso é uma proteção em código, não evidência de um MP4 exportado no S24 nesta sessão. Não há denoise temporal/refinamento de texturas avançado nesta etapa.
7. Diagnóstico separa grupos de CPU/PSS/energia por rastreamento/cinematográfico/acabamento. GPU não medida, potência do aparelho inteiro. Não há comparação física controlada nem ganho de autonomia demonstrado. Manifesto mesclado sem INTERNET verificado em teste; fotos/HDR/Noturno/zoom/SR e configurações anteriores preservados.

## Próximo passo exato e limites

- Instalar APK65 e seguir [VIDEO_EVOLUCAO_S24_VALIDACAO.md](VIDEO_EVOLUCAO_S24_VALIDACAO.md). Testar comum/rastreamento primeiro, depois cinematográfico e acabamento separadamente. Confirmar efeitos/marca/áudio/orientação em arquivos reais, lentes/frontal, 5x/4K comuns, gestos e interrupções; medir latência/cadência/temperatura em condições comparáveis. Não enviar mídia pessoal ao Git.
- A sessão só recebeu screenshot; nenhum S24 conectado ou MP4 novo inspecionado. Não anunciar recursos fisicamente validados com base no CI. Afinar máscara temporal/bordas, integração de tracking em cenas difíceis e consumo com evidência. O relatório global DepthEstimator é do caminho fotográfico; não confundir com métricas completas de inferência do cinema.
- Próxima evolução de pós: redução temporal de ruído com compensação de movimento e proteção de textura, depois outros perfis/exportação. Implementar/testar com material sintético primeiro, preservar original/áudio/PTS e validar visualmente no aparelho. Não criar controles de denoise sem processamento real.
- Fontes principais: camera/PreviewFrameReader.kt, SubjectTrackingSession.kt, PortraitPreview.kt, PortraitMaskAnalyzer.kt, EffectQueueBarrier.kt, NovaCamera.kt; core/QuickVideoWarmup.kt, CinematicPolicy.kt, VideoFinishPolicy.kt; processing/VideoFinishJob.kt; MainActivity/SettingsActivity/NovaSettings. Dependências novas só Media3 transformer/effect1.8.0; AndroidManifest e assinatura não mudaram.

## Texto para continuar

Continue Nova Camera em codex/video-estabilidade-0-20 pelo HEAD remoto atualizado. Leia NOVA_HANDOFF_CODEX.md inteiro, topo de STATUS e código citado; confira Actions antes de editar. A entrega atual é0.24.3/build65(1065), APK no artefato12114374400 do run39070952578, fonteaa580a42033fcf0cd2f8dad6469f6714bf389758, 235 testes aprovados/5 dispensados/lint/compilação/modelos/certificado verificados. Feedback S24 ainda pendente para rastreamento, cinematográfico e acabamento. Preserve key permanente, fotos/HDR/Retrato/Noturno/zoom/SR, vídeo comum/qualidade/assinatura, main e originais. Use VIDEO_EVOLUCAO_S24_VALIDACAO.md e trate meu próximo feedback. Depois avance refinamento temporal de máscaras e denoise temporal real, sem prometer qualidade/energia sem prova, com commits pequenos em português, versionName/STATUS e APK de teste sem release automático.

---

## Histórico preservado — estados de preparação abaixo foram superados pela entrega acima

# Revisão final 0.24.3 — aguardar máscara na gravação rápida

- Corrige integração importante: bind do Vídeo pode terminar antes de existir máscara; o fluxo rápido antigo chamava START imediatamente, era recusado e voltava ao Retrato. QuickVideoWarmup aguarda prontidão sem consumir START, por até8s; guarda geração/gesto e cancela ao soltar/restaurar/sair. Testes Handler/Looper cobrem início único, liberação antes da máscara, troca de sessão e timeout.
- Build64/0.24.2 substituído por esta revisão; não entregar como versão final. Novo push autorizado gera build substituto com testes/lint antes do APK e guard de publicação skipped. Restaurar gatilho original após identificar nova execução; confirmar modelos/certificado/artefato real e registrar link.
- Mantém todas as etapas anteriores (PixelCopy reduzido assíncrono, cinematográfico FHD/30/EISoff explícito, primeiro acabamento tonal opcional atéFHD/60/SDR/2min, original intacto e conferência de áudio/PTS). Denoise temporal avançado ainda pendente. S24/MP4/energia sem validação física nesta sessão.

---

# Revisão 0.24.2 — build anterior não é entrega final

- Detectado caso adicional de troca GL: slot pode estar vazio mas cleanup anterior ainda estar na fila. commonEffectPending permanece marcado desde signatureEffect até a barreira confirmar drenagem; não confundir slot nulo com EGL já liberado. Teste cobre liberação já solicitada; perfis bloqueados não criam assinatura desnecessária.
- Fonte anterior do build63 3b8dcbe8651b810beb2c137fcfec8fc46dfe2dec/0.24.1 não deve ser anunciada como entrega final. Novo push do workflow de teste na mesma branch inicia build substituto e a concurrency cancela o anterior se ainda ativo; nunca publicar release.
- Próximo passo: identificar nova execução assinada, restaurar gatilho original assim que aparecer, verificar testes/lint/modelos/certificado/artefato e registrar APK0.24.2 real. Mantém cinematográfico experimental e primeiro acabamento, não denoise temporal. Nada foi validado fisicamente no S24 nesta sessão.

---

# Revisão aprovada antes de gerar APK 0.24.1

- Núcleo final c4d4e8a09df5d0a70d401e4f68223f65f63e4428 aprovado em assembleDebug/testDebugUnitTest/lintDebug no Actions39069941647. Ajuste adicional somente classifica grupos de energia/CPU/PSS com cinematográfico e acabamento; não é alegação de economia. Novo roteiro VIDEO_EVOLUCAO_S24_VALIDACAO.md.
- Próximo passo: execução assinada existente deve repetir testes/lint antes do APK. Preservar guard que pula publicação de release para push em codex/video-estabilidade-0-20; restaurar gatilho original após disparo único. Conferir artefato/modelos/certificado de verdade antes de entregar.

---

# 0.24.1 — revisão final e próximo APK

- Branch codex/video-estabilidade-0-20. 0.24.0 passou no CI39069611401, app97ce062cb37539b4975fd9bcae6806514f80f7c8; inclui as etapas 0.22.1/0.23.0 descritas abaixo. Nenhum APK novo gerado ainda nesta etapa.
- 0.24.1 acrescenta EffectQueueBarrier e teste: fechar OverlayEffect enfileira limpeza, portanto a fábrica do cinematográfico só executa depois de marcador na fila GL antiga. Fecha a corrida de troca de efeitos; não apenas captura exceção. Baixa memória cancela acabamento opcional. CI desta revisão pendente.
- **Próximo passo imediato:** conferir CI0.24.1, corrigir falhas reais, gerar APK de teste pelo workflow existente, sem release (guard de push nesta branch), restaurar gatilho original assim que execução aparecer, confirmar testes/modelos/certificado/artefato. Atualizar STATUS/handoff com fonte, build e link real.
- Orientar dono: testar rastreamento primeiro, depois ativar cinematográfico com FHD/30/EISoff escolhidos manualmente; aguardar máscara. Pressão longa na miniatura de vídeo oferece acabamento leve de sombras e comparação, original preservado; atéFHD/60/2min SDR. Pipeline depende de o processo permanecer vivo. Não há denoise temporal avançado nem prova de melhoria energética.
- Validação S24: PixelCopy deve reduzir latência mas ainda não foi medido no aparelho; efeitos devem ser confirmados na prévia E nos pixels do MP4 (incluindo marca, áudio, rotação/espelho, atrás/frente, gravação rápida do Retrato e fallback). Máscaras/contornos/temperatura precisam de feedback real. Não declarar aprovação física com testes JVM.

---

# Etapa 0.24.0 — acabamento opcional em validação

- Branch codex/video-estabilidade-0-20; não tocar main/assinatura. 0.22.1 (PixelCopy reduzido assíncrono) aprovada no CI39068644575; 0.23.0 cinematográfico aprovada no CI39069180455, app677b94522383a05b5c41b192ead61d7958540c00.
- 0.24.0 acrescenta VideoFinishJob/VideoFinishPolicy, Media3 transformer/effect1.8.0 e menu na pressão longa da miniatura de vídeo. Primeiro acabamento suave global de sombras, não denoise temporal. Original intocado, saída separada, progresso/cancelamento, comparação e par persistido. Elegível até FHD/60/SDR/2min; rejeita o restante sem reduzir qualidade.
- Exportação confere metadados e todas as amostras reais: tamanho/orientação equivalente, codec, quadros/PTS, hash dos bytes de áudio/canais/taxa e sincronismo; só então publica cópia. Tarefas limitadas, térmica independente da Activity, cancela por calor severo/captura, profundidade suspensa durante o acabamento; depende de o processo continuar vivo.
- 0.23.0 exige ativar cinematográfico e selecionar FHD/30/EISoff explicitamente. Desfoque ajustável e seleção por toque; caminho de prévia+vídeo implementado, sem prova física de pixels/qualidade. Modelos existentes reutilizados. Não prometer cabelos/folhas perfeitos, tracking semântico, energia melhor ou 4K cinemático.
- **Próximo passo exato:** conferir CI desta etapa, corrigir falhas reais, gerar APK pelo fluxo assinado existente (push temporário com publicação skipped), restaurar gatilho após iniciar, confirmar modelos/certificado/upload. Entregar link e roteiro do novo APK; registrar cada resultado. Redução temporal de ruído/compensação de movimento permanecem próxima evolução e não devem ser rotuladas prontas.
- Feedback atual é uma captura de tela de leitura lenta da prévia; nenhum MP4 pessoal recebido e nada pessoal enviado ao Git. Separar execução Actions de validação S24. Fontes novas: camera/PreviewFrameReader.kt; camera/PortraitPreview.kt/PortraitMaskAnalyzer.kt/NovaCamera.kt; core/CinematicPolicy.kt; processing/VideoFinishJob.kt; core/VideoFinishPolicy.kt; MainActivity/SettingsActivity/settings.

---

# Em implementação — 0.23.0 cinematográfico experimental

- Rastreamento/PixelCopy 0.22.1: CI 39068644575 aprovado, app 451576d90d0de11f75fd208269558a11d6cac1bd. Corrige a cópia full-size bloqueante mostrada no feedback do S24; nova latência ainda não medida fisicamente.
- 0.23.0: PortraitPreviewEffect.cinematic usa PREVIEW|VIDEO_CAPTURE; assinatura composta na saída de vídeo, máscara com validade, seleção sensor→análise via ImageInfo e reaproveitamento de profundidade/segmentação. Perfil opcional exige FHD/30 e EIS off explicitamente. Vídeo comum, fotos e chave preservados. Testes/CI desta etapa ainda pendentes.
- Atenção técnica: validar máscara/GL/assinatura nos MP4 reais antes de declarar funcional no S24. Perda/calor volta à composição sem desfoque com aviso, sem reduzir qualidade. Semântica/reidentificação e qualidade premium de bordas não comprovadas.
- Próximo passo: concluir CI desta etapa, implementar primeiro acabamento local opcional com original preservado e metadados/áudio verificados, depois gerar APK assinado final. Redução temporal de ruído e refinamentos avançados devem ser progressivos e declarados conforme evidência; não simular controles.

---

# Nova etapa autorizada — feedback de leitura lenta e pendências

- HEAD inicial conferido 725195ae9ab74d845b9a4096387298837769e949 na branch codex/video-estabilidade-0-20; baseline Actions 39067748469 aprovado antes de editar.
- Usuário anexou captura de tela com rastreamento encerrado por leitura lenta da prévia e autorizou fazer também cinematográfico/pós-processamento. Não pedir novamente autorização com base na antiga espera por validação; não confundir captura de tela com teste de MP4.
- 0.22.1 troca PreviewView.bitmap síncrono/full-size por PreviewFrameReader: PixelCopy assíncrono para bitmap pequeno, transformação de display/Views, no máximo uma solicitação, geração e prazo de resposta; sem stream CameraX novo. Testes de fila única, redução antes da cópia, callback após fechar, erros e geometria. CI ainda pendente nesta entrada.
- Próximo: validar regressões Actions, seguir implementação cinematográfica real e pós-processamento em etapas isoladas, preservando captura comum. Manter chave permanente, versões anteriores e mídia pessoal fora do Git; entregar APK de teste assinado sem release automático.

---

# Entrega confirmada — 0.22.0 build 62 (1062)

- **APK assinado disponível:** [ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39067165320/artifacts/12113024414), expira em 2026-11-16T20:39:45Z. Artefato 12113024414, 131002791 bytes, sha256:4e5ed0f072cd4ff0b36cf268c19ae134b80ee7b9bb8584f3f1cd5687ddc5e7aa (digest do ZIP).
- [Actions 39067165320](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39067165320), job 117497114240: concluído com sucesso. **219 testes aprovados, 5 locais dispensados, 0 falhas**; lint; JPEG sintético 200 MP; modelos NAFNet/Depth Anything; assembleRelease; pesos no APK (profundidade=True); continuidade de certificado/versionCode e upload aprovados. Publicação de release skipped.
- Fonte do APK `ae2257698a67e4272234df23f758527026893bbd`; app `b4a97588ed4bff162b6a093e0d7d6aa40bffbbf0` aprovado no CI 39066797447 antes da geração. Gatilho original restaurado em `e869bdb182f478f41bc47a9ee15b130ccaffcc4e`; documentação posterior não muda o app.
- Branch `codex/video-estabilidade-0-20`; buscar HEAD atualizado antes de continuar. Main, branch original, fotos/Noturno/HDR/Retrato/zoom/SR e certificado permanente preservados; nenhuma INTERNET, modelo novo pesado em vídeo ou alteração no pipeline de assinatura/gravação.
- Entrega inclui rastreamento visual experimental e métricas CPU/PSS/cópia/análise, **não inclui cinematográfico nem pós-processamento de vídeo**. Toque em contorno com textura em Vídeo/Vídeo Pro; indicador encerra e volta ao automático. AF contínuo/AE respeitam capacidades/manuais; perda e enquadramento/lente/calor/memória encerram de forma controlada. Sem reconhecimento semântico/reidentificação após oclusão.
- **Próximo passo exato:** testar APK no S24 conforme RASTREAMENTO_S24_VALIDACAO.md, confirmar movimento/AF/AE reais e MP4s, medir controle vs ativo nas mesmas condições. Nenhum S24/MP4 novo ou comparação controlada disponível nesta sessão. GPU/autonomia não medidos; não afirmar ganho energético. Depois avançar na etapa de profundidade descrita abaixo, inicialmente FHD/30 com escolha explícita, prévia e pixels MP4, mantendo vídeo comum.
- Texto para nova sessão: “Continue Nova Camera pela branch codex/video-estabilidade-0-20 no HEAD remoto atualizado. Leia NOVA_HANDOFF_CODEX.md inteiro e topo de STATUS; verifique Actions antes de editar. O APK 0.22.0 build62 está no artefato 12113024414 do run39067165320; tracking experimental entregue, validação S24 pendente. Preserve a chave permanente e todos os recursos. Após feedback físico, prossiga com profundidade cinematográfica real na prévia e MP4, depois pós-processamento opcional com original preservado; medir energia, testar/compilar/lint via Actions e entregar APK assinado sem release automático.”

---

## Histórico da preparação (as pendências de geração abaixo foram concluídas)

# Acompanhamento da entrega 0.22.0 — build 62 em execução

- HEAD de trabalho após restaurar gatilho: `e869bdb182f478f41bc47a9ee15b130ccaffcc4e` (esta documentação pode avançá-lo). Código do app `b4a97588ed4bff162b6a093e0d7d6aa40bffbbf0` aprovado em assembleDebug/testDebugUnitTest/lintDebug no [39066797447](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39066797447).
- APK fonte `ae2257698a67e4272234df23f758527026893bbd`; build assinado 62 (versionCode esperado 1062) em execução [39067165320](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39067165320), job 117497114240. Ainda sem artefato confirmado. Publicação de release bloqueada para push nesta branch. Gatilho adicionado só para esta execução e já restaurado; não disparar outro build sem necessidade.
- Próximo passo imediato: aguardar build 62 e conferir testes/lint, modelos NAFNet/Depth Anything, assembleRelease, assinatura/versionCode e upload real; verificar publicação skipped. Registrar link real do artefato e entregar ao dono. Se falhar, ler erro concreto, corrigir e repetir; nunca anunciar APK indisponível.
- Duas primeiras execuções de rastreamento falharam: 39066321775 por tipos Kotlin (corrigidos); 39066492614 por 2 asserções inteiras de geometria. Testes finais usam backend gráfico nativo e tolerância explícita do roundOut (1 pixel centro/2 extensão); transformação de produção preservada. Execução final acima passou.

## Próxima etapa técnica, após teste físico do rastreamento

- Examinar `camera/PortraitPreview.kt`, `camera/PortraitMaskAnalyzer.kt`, `processing/DepthEstimator.kt`, `core/imaging/DepthPortrait` e negociação SessionConfig/efeitos/assinatura em NovaCamera. PortraitPreviewEffect atualmente declara somente PREVIEW: não afirmar bokeh no MP4.
- Reutilizar processamento existente num perfil cinematográfico explicitamente escolhido, inicialmente FHD/30. Validar superfície de saída de vídeo, EGL recordable, timestamps/apresentação, transformações e composição da marca antes de expor controle. Uma mesma composição deve chegar à prévia e ao arquivo; não basta máscara na tela.
- Conectar escolha por toque à seleção na profundidade; tratar máscara temporal com movimento e idade/confiança, não aplicar mapa antigo indefinidamente. Pessoa frontal e objetos traseiros têm limitações distintas; testar bordas/oclusão/transições no aparelho.
- Qualquer stream adicional/análise precisa passar pela capacidade real da sessão completa. Fallback preserva vídeo comum e qualidade escolhida, com aviso; não converter 4K60 silenciosamente para FHD30. Pós-processamento só depois, com original preservado e conferência de áudio/sincronia/metadados.

---

# Continuidade — 0.22.0 em validação (17/10/2026)

- Branch única de trabalho: `codex/video-estabilidade-0-20`; main e branch original preservadas. Novo pedido do dono autoriza rastreamento → profundidade → pós-processamento com medições e APK de teste após etapa estável.
- Base remota 0.21.5 `6503e0388420ff48800a0a24d18017200c1e1c9e` aprovada no CI 39059803047 antes de alterações. Métricas 0.21.6 `19fd4f32e5868e3a761aefbb03567f5b7c8a0ea1` aprovadas no CI 39060797636.
- 0.22.0 implementa etapa experimental de rastreamento visual de aparência, não semântico: `SubjectTracker`, `SubjectTrackingSession`, `TrackingMeterGate`, `TrackingMetering`, `TrackingLog`; integração MainActivity/NovaCamera/ViewfinderOverlay/settings. Não toca pipeline de gravação/assinatura/EGL, foto/HDR/Noturno/modelos.
- Seleção somente em Vídeo/Vídeo Pro, inclusive gravação rápida após mudar para Vídeo. Uma análise em voo, snapshot PreviewView orientado/espelhado, lado maior 320 px, correlação normalizada, confiança/ambiguidade e perda limitada. Af contínuo com região e AE compatível respeitam manual. AF/AE em voo limitado; resultado antigo não alcança câmera nova (epoch/generation).
- Suspende análise por ausência de assunto, transformação indisponível, lente/enquadramento alterado, pausa, leitura lenta, memória e calor severo; vídeo continua na qualidade escolhida. Buffer limitado e executor fechado. Captura pública da prévia tem custo de cópia/alocação medido, sem alegar economia comprovada. GPU não medida.
- Testes automatizados novos: movimento/iluminação/oclusão/atrasos/textura/cadência, transformação espelho/rotação/recorte, cancelamento/fechamento/sessão inválida. Nesta entrada CI/APK 0.22.0 ainda pendentes; registrar resultados reais ao terminar.
- Não há S24 conectado, vídeo novo, baseline controlado ou comparação física de consumo. Roteiro [RASTREAMENTO_S24_VALIDACAO.md](RASTREAMENTO_S24_VALIDACAO.md). Feedback anterior “Deu certo” não prova matriz completa.
- Próximo passo exato: concluir CI completo, corrigir falhas reais, gerar APK assinado pelo workflow existente com publicação pulada nesta branch, restaurar gatilho após disparo único, confirmar modelos/certificado/artefato, registrar build/link. Depois validar no S24 antes de avançar na profundidade.
- Cinematográfico (prévia e pixels MP4) e pós-processamento temporal/original preservado continuam pendentes; não expor controles simulados. Aproveitar arquitetura de profundidade existente, iniciar FHD/30 somente com escolha explícita, preservar vídeo comum.

## Texto para continuar

Continue Nova Camera em codex/video-estabilidade-0-20 pelo HEAD remoto atualizado. Leia este handoff inteiro, topo de STATUS e código citado antes de editar; confirme a validação Actions do HEAD. Preserve assinatura Android, fotos/HDR/Retrato/Noturno/zoom/SR, gravação/qualidade/assinatura de vídeo e main. Conclua primeiro a entrega e validação física do rastreamento 0.22.0. Em seguida implemente profundidade cinematográfica real na prévia e MP4, depois pós-processamento local opcional preservando original/áudio/metadados. Use medições reais, commits pequenos em português, teste/lint/compilação Actions e APK de teste assinado sem release definitivo automático. Não prometa validação S24, GPU ou autonomia sem evidência.

---

## Próxima evolução autorizada — medições e rastreamento

- Branch atual `codex/video-estabilidade-0-20`, HEAD conferido `6503e0388420ff48800a0a24d18017200c1e1c9e`; base Actions 39059803047 aprovada. Não alterar main.
- Usuário relatou “Deu certo” e autorizou nova etapa: rastreamento → cinematográfico → pós-processamento. Não transformar esse retorno genérico em prova específica de 5×/4K60/assinatura. A autorização atual permite avançar sem aguardar a antiga pendência de matriz física completa.
- 0.21.6 adiciona medições de CPU/PSS/cadência e custo da coleta no diagnóstico. Ainda não há referência A/B medida no S24; não afirmar economia. Próximo: implementar acompanhamento visual leve com confiança/perda e AF/AE contínuos, fila limitada e análise adaptativa; testes e APK assinado após etapa estável.
- Sem stream extra de câmera/modelo pesado no vídeo nesta primeira etapa. Não alterar gravação/assinatura/EGL, resolução ou FPS para acomodar rastreamento. Medir custo da leitura da prévia, CPU e memória; GPU indisponível deve permanecer não medida.
- Roteiro de referência e teste: `docs/pendente/RASTREAMENTO_S24_VALIDACAO.md`. Preservar tudo que já funciona; entregar um APK de teste com a chave existente e sem release automático.

# Nova Camera — handoff após APK 0.21.5 (build 61)

## Estado atual e download

- Repositório: `chrystianmax73-dev/nova-camera`; branch: `codex/video-estabilidade-0-20`. Não partir da main nem sobrescrever a branch original `ccr-7443eaac-tqygyg`.
- **APK novo: 0.21.5, build 61, versionCode 1061.** [Baixar ZIP com app-release.apk assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39059245337/artifacts/12110872722). Artefato do Actions, disponível até 16/11/2026; acesso ao GitHub pode ser necessário.
- [Execução assinada 39059245337](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39059245337), código do app `9d4257cfa3a1c587c979e503a295d3e8aceed7fb`; fonte do APK `195add2ceb22c3fd2733f5f5614434f4627c1c50` (apenas workflow/docs além do código aprovado).
- SHA-256 **do ZIP do artefato**, não do APK isolado: `5d3a9a940942a793f5747db17d7feb28c081ba4b9387dc8f0dc5c982afb8e8f7`; 130980931 bytes.
- Mesmo pacote e certificado Android. Não foi criada keystore, não houve mudança de signingConfig, permissões ou tom do Noturno. Configurações e histórico existentes preservados pelo mesmo pacote/assinatura.
- **Nenhum release foi criado nesta entrega.** Publicação foi pulada no build 61 por condição do workflow; os gatilhos originais foram restaurados em `9942432d542cfc3f0cf9abd112779d6f8398e962`. A configuração de execução manual foi preservada. Sem geração automática nessa branch a cada commit.
- O build 60/0.21.1, abaixo no histórico, é a versão anterior testada pelo usuário; não reenviar como novidade.

## Evidências reais

- Baseline 0.21.1 antes de alterações do aplicativo: [39056484866](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39056484866), assembleDebug + testDebugUnitTest + lintDebug aprovados.
- 0.21.3 com sincronização do teste corrigida: [39058541277](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058541277), compile/testes/lint aprovados.
- 0.21.4: [39058739596](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058739596), compile/testes/lint aprovados.
- 0.21.5 final: [39058891633](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058891633), assembleDebug + testDebugUnitTest + lintDebug aprovados.
- Build assinado 61 repetiu testes/lint: **206 aprovados, 5 locais dispensados, 0 falhas**. JPEG sintético 200 MP em host aprovado; conversões NAFNet/Depth Anything aprovadas; ambos os modelos conferidos no APK; assembleRelease e comparação de certificado/versionCode aprovados; upload real do APK aprovado; publicação de release pulada.
- A primeira execução da 0.21.3 [39058243287](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058243287) teve 1 falha no teste de diálogo: a preferência era lida antes de o callback assíncrono executar. Foi corrigido o teste para drenar Looper após clique; nenhuma exigência foi removida.
- A execução isolada inicial da 0.21.2 [39056971458](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39056971458) estava em execução prolongada no último acompanhamento; não afirmar que ela passou. A suíte final da 0.21.5 inclui seu código e passou. Se ainda ativa, pode exigir cancelamento manual; conector desta sessão não expunha cancelamento.
- Não houve compilação local: ambiente sem Gradle utilizável e com bloqueio de proxy no CLI. As provas acima são execuções reais do GitHub Actions.

## Correções entregues em código

1. Consulta FPS da **SessionConfig CameraX completa**, com prévia, gravação e efeitos, e verifica encoder. Duração genérica de saída MediaRecorder deixa de vetar isoladamente UHD/60. Consulta indisponível é diferente de sessão recusada. Faixa variável não promete FPS fixos.
2. EIS da gravação substitui solicitação de estabilização da prévia (modo 2). CameraX coordena OIS/AE em Vídeo/Vídeo Pro; sem forçar cena de prioridade a rostos/OIS nesses dois modos. Fotos, Noturno, timelapse e câmera lenta mantêm o tratamento anterior.
3. Recuo limitado sem EIS conserva resolução/FPS/assinatura. Falha de sensor não desliga marca indiscriminadamente. Arquivo ativo finaliza antes de religar câmera.
4. Combinação recusada ou orçamento de recuperação esgotado liga **apenas prévia**, bloqueia o obturador e oferece alternativa na mesma resolução, quando anunciada, mediante escolha explícita. Se até a prévia falhar, não reinicia em ciclo. Seleção de FPS funciona mesmo quando resta apenas uma opção.
5. Assinatura compensa recorte, rotação e espelho, inclusive na frontal espelhada. Falha é isolada no perfil afetado; erro antigo de outra geração não desliga efeito novo. Mantida liberação serial na fila GL antes de construir o próximo efeito.
6. Textura estática da marca só redesenha quando muda geometria/buffer. Testes gráficos nativos Skia verificam pixels nas quatro rotações, com/sem espelho, e reutilização de textura. Testes de interface conferem recusa/aceite de 30 fps preservando UHD.
7. Vigia descarta avisos de sessão antiga e tolera início lento. Diagnóstico local informa configuração, EIS, lente física observada, assinatura solicitada/ativa/desenho em textura e metadados reais do MP4 (dimensões/MIME/FPS pelos timestamps).

Commits pequenos principais:
- `78d7e4ceaf17ddaecc5dd0365d1fc2c8d9c3a6f3` — 0.21.2, negociação/recuperação.
- `4bd28076420f1cfad1b1852e84380a14a50165ed` — 0.21.3, assinatura.
- `4a0c1ec6779f9421b41f4575f5446ca21faba364` — sincronização do teste de diálogo.
- `d699cc53d7a08c4b91bed4316aaf978bafec5e3d` — 0.21.4, prévia após STOP.
- `9d4257cfa3a1c587c979e503a295d3e8aceed7fb` — 0.21.5, prévia após bind recusado.

## O que continua sem validação física

- **Não afirmar que 5×/4K60 funciona**: CameraX consulta a câmera lógica; a tele física pode ter outra restrição após o zoom. Não foi recebido MP4 novo nem há S24 conectado nesta sessão.
- Não afirmar que a marca está nos pixels do MP4 só porque foi desenhada na textura. Conferir reprodução real, início/meio/fim, espelho, orientação e áudio.
- FPS/dimensões do **novo** arquivo só serão confirmados após gravação no aparelho. Diagnóstico anterior 0.21.1 provou Full HD 1080×1920, AVC e 58,90 fps médios; não provou 4K.
- Estabilidade da prévia, troca 1×/5×, frontal/traseira, Vídeo/Vídeo Pro, interrupções e temperatura exigem retorno físico. Moderado 2 isolado não aciona limitVideo; política térmica preservada.
- Roteiro completo: [VIDEO_S24_VALIDACAO.md](VIDEO_S24_VALIDACAO.md).

## Pendências em ordem e próximo passo exato

1. Usuário instalar APK 0.21.5/build 61 e testar o roteiro. Obter resultado/diagnóstico desta versão antes de afirmar estabilidade ou avançar processamento pesado. Se continuar recusando UHD/60, usar os novos registros de sessão/EIS/efeito/MP4 para investigar a combinação concreta; manter alternativa UHD/30 explícita.
2. Pós-processamento local **opcional**, em etapa/versão separada: preservar original e saída derivada/comparação; progresso/cancelamento, execução assíncrona e controle térmico; preservar áudio/PTS/sincronismo, orientação/duração/resolução/FPS. Material sintético para ruído/movimento/ghosting/flicker. Não substituir a captura Full HD/60 elogiada por filtro agressivo nem inventar detalhe.
3. Rastreamento por toque: reutilizar AE/AF, FocusPolicy, DepthTap e máscaras/análise existentes; acompanhar com confiança, estabilidade e retorno ao automático em perda/oclusão. Distinguir AF, bloqueio e assunto acompanhado. **Ainda não implementado.**
4. Profundidade cinematográfica inicialmente Full HD/30: reutilizar modelos offline/segmentação, análise adaptativa, estabilidade temporal e bordas finas; mesmo resultado na prévia e MP4, frente/trás/espelho e gravação rápida do Retrato. Manter FHD/60 e 4K comuns. Sem profundidade pesada desnecessária. **Ainda não implementada.**
5. Refinamentos de qualidade/energia somente com medição. Cada etapa: ler handoff inteiro + topo STATUS + código, baseline real, commits pequenos em português, nova seção STATUS e versionName para mudanças de app; APK assinado existente, sem release automático.

## Texto para continuar em outra sessão

> Continue chrystianmax73-dev/nova-camera na branch codex/video-estabilidade-0-20, verificando o HEAD atualizado. A entrega atual é 0.21.5/build 61 (1061), APK assinado em https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39059245337/artifacts/12110872722; fonte do app 9d4257cfa3a1c587c979e503a295d3e8aceed7fb e fonte do build 195add2ceb22c3fd2733f5f5614434f4627c1c50. Leia este handoff inteiro, topo de docs/STATUS.md e código citado. CI final 39058891633 e build assinado 39059245337 passaram; 206 testes aprovados, 5 locais dispensados. Primeiro analisar meu retorno físico desta versão para 5×/4K60, prévia e marca no MP4; só depois iniciar pós-processamento opcional, rastreamento e cinematográfico FHD/30 em etapas. Não trocar assinatura/keystore, pedir/expor segredos, adicionar INTERNET, enviar fotos pessoais ao repo, copiar código proprietário, mudar Noturno ou reescrever o que funciona. Preservar original/configurações/histórico. Sem publicar releases automaticamente. Não chamar de validado o que não foi medido no aparelho.

---

## Histórico anterior preservado

O conteúdo abaixo registra o estado anterior à entrega atual; pendências e links antigos são contexto histórico. O estado vigente está acima.

# Nova Camera — continuidade após estabilização de vídeo

## Feedback real da 0.21.1 (build 60) — próxima correção

O dono testou no S24 Ultra/API 37: Full HD grava com boa qualidade; diagnóstico do MP4 registra 1080×1920, AVC, 58,90 fps médios. 5×/4K60 não inicia e pede outra combinação. Assinatura ativada nas preferências não aparece; sessões registram assinatura=false. Congelamentos persistem a 1× e 5×, com duas tentativas e STOP; os crashes listados ainda são da 0.20.0. Isso não comprova 4K60 na API nem restauração da assinatura.

Ordem atual: corrigir 4K/FPS, congelamentos e assinatura, entregar outro APK assinado para teste; depois implementar aprimoramento local opcional de vídeo (original preservado, áudio/PTS/resolução, progresso/cancelamento/controle térmico), rastreamento e profundidade em etapas independentes. Cinematográfico deve começar conservadoramente em FHD/30 sem remover FHD/60 ou 4K. Não processar agressivamente o vídeo que já tem boa qualidade. O usuário autorizou gerar a atualização para validação; manter assinatura permanente, sem INTERNET ou mídia pessoal no Git. Diagnósticos não substituem inspeção de um MP4 pessoal que não foi anexado.

Esta atualização de documentação inicia uma nova validação da base sem mudar o aplicativo; consultar Actions antes do primeiro commit de código.

## APK entregue para teste — 0.21.1, build 60

- [Download do ZIP com app-release.apk assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39054257114/artifacts/12108213568), disponível até 16/11/2026. Requer acesso ao GitHub para baixar o artefato.
- [Execução do build 60](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39054257114), commit `67c1e2eb843b85656f736229b8baec5e0d78107a`; código do app idêntico ao commit `33c0224`.
- Testes/lint, compilação release, modelos offline, conferência da assinatura permanente e versionCode passaram. A etapa de publicação do release falhou com `HTTP 403: Resource not accessible by integration`. O workflow aparece com falha por esse motivo; não confundir com falha da compilação ou com ausência do APK preservado.
- Build 59 também compilou e verificou assinatura, mas recebeu o mesmo 403 sem preservar artefato. Por isso foi necessário o build 60.
- Foi acrescentado upload-artifact depois da conferência da assinatura e antes de criar release. O gatilho temporário de push nesta branch foi restaurado ao estado original; futuras publicações continuam manuais. Assinatura, implementação das etapas de assinatura e código do aplicativo não foram alterados para esta entrega.
- O último release publicado continua sendo nova-test-58; não fornecer link fictício de nova-test-60. O aviso automático de novo APK foi desligado ao entregar o arquivo nesta conversa.
- Atualização pode ser instalada sobre a 0.20.0 pela mesma assinatura. Validar agora no aparelho; rastreamento e profundidade cinematográfica em vídeo continuam pendentes.

## Estado e autorização atuais

- Repositório: https://github.com/chrystianmax73-dev/nova-camera
- Branch de trabalho: `codex/video-estabilidade-0-20`.
- Base preservada: HEAD `867499c68086a64df467f738e600c463b58f80d8` de `ccr-7443eaac-tqygyg`.
- Release preservado: [nova-test-58, 0.20.0](https://github.com/chrystianmax73-dev/nova-camera/releases/tag/nova-test-58), commit `328c7cf29aff23faf1b7beaaa81c0d5adf82bab3`.
- Último commit do aplicativo: `33c02249d65838ed8283d335ff32ab1979d17c4f`, versão **0.21.1**. Consulte o HEAD antes de retomar; um commit posterior pode apenas atualizar este handoff/STATUS.
- O dono autorizou explicitamente **GitHub Actions como ambiente de validação**. Não exigir Gradle offline local: o workspace tem proxy inacessível e não tem Gradle/SDK. O conector GitHub funciona para ler/gravar Git e consultar Actions.
- O dono autorizou gerar e entregar o APK para teste. Builds assinados 59 e 60 foram executados; o APK do build 60 está disponível no link abaixo. Não disparar outros releases sem nova instrução.
- O handoff anterior foi preservado integralmente no [commit ad9adb6](https://github.com/chrystianmax73-dev/nova-camera/blob/ad9adb650c345865083c10723bc2cc4b7b2e3186/docs/pendente/NOVA_HANDOFF_CODEX.md). Ele contém a investigação original e o handoff completo da 0.20.0. A exigência histórica de testes offline locais e a ordem antiga de pendências foram substituídas pelas instruções atuais do dono.

## Regras permanentes

Não partir da main, não reescrever o projeto/motor fotográfico, não alterar assinatura Android nem criar keystore, não pedir/expor segredos, não adicionar INTERNET ou dependência que exija rede no app, não enviar mídia pessoal ao Git, não copiar código proprietário. Preservar o tom do Noturno, keepOriginals desligado por padrão, fotos, SR, RAW, foco, retrato e configurações existentes. Commits pequenos em português, versionName e nova seção no topo do STATUS por versão. Testes/Compilação/Aparelho devem continuar separados. O build de release e a assinatura permanente não foram alterados.

## Validação por Actions

Em `.github/workflows/android-check.yml`, a única mudança foi:

```yaml
push:
  branches: [main, codex/video-estabilidade-0-20]
```

O gatilho pull_request para main, Java 17, SDK 36, NDK 27.0.12077973, CMake 3.22.1, Gradle 8.13 e comando `gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` foram preservados. Push nesta branch inicia a validação; não é necessário workflow_dispatch. A publicação de releases continua separada e não foi acionada.

| Etapa | Commit | Execução | Resultado |
|---|---|---|---|
| Base 0.20.0 antes de alterar o app | e1fc1a0 | https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39049540137 | Sucesso: compilação, testes e lint |
| GL/recuperação 0.20.1 | cae03f2 | https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39051779855 | Sucesso: compilação, testes e lint |
| Qualidade/MP4 0.20.2 | 415cd78 | https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39052167400 | Sucesso: compilação, testes e lint |
| Gravação rápida 0.21.0 | da103ad | https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39052668646 | Sucesso: compilação, testes e lint |
| Finalize após destruir UI, 0.21.1 | 33c0224 | https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39053015203 | Sucesso: compilação, testes e lint |

**Aparelho:** nenhuma alteração desta branch foi testada no S24 nesta sessão. CI não exercita HAL Samsung, EGL real, codificação real no aparelho nem qualidade de máscaras. Não foi inspecionado o MP4 pessoal do dono. Não declarar crash resolvido fisicamente, 4K/60 real comprovado ou gesto fluido no aparelho apenas com esses testes.

## Diagnóstico original do dono e alterações

S24 Ultra SM-S928B, Android 17/API 37, Nova 0.20.0 (1058): crashes repetidos na thread `nova-video-signature`, `IllegalStateException: Unable to update texture contents`, em `SurfaceTexture.updateTexImage` → CameraX `SurfaceProcessorImpl.blockAndPostOverlay/createBufferAndOverlay/onInputSurface`. Prévia de Vídeo parada a 5×, câmera lógica 0, registros a cada três segundos. Seleção 4K/60, arquivo relatado com 1080×1920.

### 0.20.1 — GL e recuperação

- `NovaCamera.bind()` solta os use cases e fecha o efeito anterior **antes** de construir o novo. Antes, init novo e release antigo eram enfileirados nessa ordem na mesma thread GL. A liberação antiga podia invalidar o contexto da textura nova. `SerialEffectSlot` torna explícita a ordem close→factory.
- `VideoEffectHandler` é a fronteira das tarefas GL do OverlayEffect: encaminha RuntimeException ao controlador e mantém o Looper apto a processar a liberação. CameraX encaminha ProcessingException ao error listener, mas o erro real informado era IllegalStateException. Não foi instalado handler global de exceções nem reiniciada uma thread morta.
- Falha de assinatura desliga o efeito nesta sessão sem alterar a preferência. Se gravando, pede Stop e espera Finalize antes do bind sem efeito. Avisa que este vídeo está sendo finalizado e que a próxima gravação ficará sem assinatura.
- `VideoRecovery`: duas tentativas por sessão; bind não renova orçamento; ignora repetição enquanto aguarda Finalize; ao esgotar, para o vigia. Não reduz FPS/resolução. Nova ação explícita pode renovar o orçamento.
- `release()` pede finalização; liberação de provider/executores fica para depois de Finalize quando necessário.
- Diagnóstico agora inclui assinatura do vídeo. O antigo `efeito=false` media apenas portraitEffect e não excluía OverlayEffect.

### 0.20.2 — Qualidade e arquivo final

- UHD/FHD/HD explícitos usam QualitySelector sem fallback de resolução. AUTO mantém seleção automática. Bloqueio por incompatibilidade/temperatura não altera a preferência nem grava em outra qualidade.
- `VideoPreflight` cruza faixas de FPS, duração mínima anunciada para MediaRecorder e capacidade Surface AVC/HEVC. É conservador; anúncio não garante todas as combinações com efeito, estabilização ou tele física escolhida pelo HAL.
- O bind real ainda pode recusar a combinação. Normal/Vídeo Pro não recuam silenciosamente para 30 fps ou Foto; mantêm erro e controles para nova escolha. Crop da sessão é conferido e novamente checado antes de gravar.
- `VideoMetadata` usa MediaExtractor após Finalize, numa fila própria, sem decodificar imagens: tamanho, rotação, MIME do codec, PTS de todas as amostras. `VideoContract` calcula FPS médio pelos extremos dos PTS e número de amostras, inclusive com quadros B fora de ordem.
- O visor informa FPS alvo/faixa automática. FPS do arquivo é reportado depois de medir; divergência produz aviso e fica em `VideoLog` no Diagnóstico. O arquivo é preservado. Falha de leitura é explicitamente não medida.
- FPS médio não prova taxa constante. Não confundir triagem de capacidades ou metadados nominais com a medição das amostras.

### 0.21.0 — Gravação rápida

- `ShutterButton.QuickListener` e `QuickRecording`: toque fotografa; pressão longa prepara vídeo; arraste lateral de 64 dp bloqueia; soltar sem bloqueio finaliza. Cancelamento, segundo dedo e saída do app interrompem também o vídeo bloqueado.
- `MainActivity` muda temporariamente Foto/Retrato para Vídeo, usa o controlador e verificações existentes e retorna após Finalize. `setMode(..., remember=false)` evita persistir o modo temporário. Nenhum stream adicional foi colocado no caminho fotográfico.
- Soltar durante a preparação cancela a intenção e não inicia gravação tardia. Microfone não autorizado: pede permissão e exige novo gesto, sem começar automaticamente ao voltar do diálogo.
- UI mostra gravação/tempo e bloqueio. Latência de bind e comportamento do toque exigem aparelho. **Vídeo iniciado a partir de Retrato ainda é vídeo comum, sem profundidade.**

### 0.21.1 — Callback tardio

- `MainActivity.onRecording/onRecordingFinished` não usam som, views ou executor de miniaturas depois de destruir/encerrar a Activity. Finalização e inspeção do MP4 continuam no controlador.
- Teste com Activity real em Robolectric destruída antes de receber o callback com URI.

## Arquivos principais e regressões

- Controlador: `camera/NovaCamera.kt`; tipos da UI: `camera/CameraTypes.kt`.
- Novos: `camera/VideoEffectHandler.kt`, `camera/VideoPreflight.kt`, `camera/VideoMetadata.kt`; `core/SerialEffectSlot.kt`, `VideoRecovery.kt`, `VideoContract.kt`, `VideoLog.kt`, `QuickRecording.kt`.
- UI: `MainActivity.kt`, `ui/ShutterButton.kt`; relatório: `CameraDiagnostics.kt`.
- Testes: `VideoRecoveryTest`, `VideoEffectHandlerTest`, `VideoContractTest`, `QuickRecordingTest`, `QuickShutterTest`, `VideoLifecycleTest`.
- app/build.gradle.kts: apenas versionName mudou, além de whitespace final; assinatura/dependências preservadas. Manifesto e motor fotográfico não foram alterados.

## Validação física pendente

Testar frontal/traseira, Vídeo/Vídeo Pro, 1080p/4K × 30/60, assinatura e estabilização ligadas/desligadas, zoom 1×→5×→10×, troca rápida de qualidade, Stop/pausa/retomada, Home/bloqueio/chamada. Conferir MP4 final e relatório do Diagnóstico: dimensões, codec, FPS médio, mensagens de bloqueio e recuperação. Guardar arquivos pessoais fora do Git.

Testar gesto em Foto/Retrato: toque simples, hold→soltar, hold→arrastar→soltar→tocar para parar, soltar antes de abrir vídeo, permissão de microfone, cancelamento, interrupção e retorno ao modo anterior. Revalidar fotos/SR/Noturno sem alterar seu tom.

## Próxima implementação — foco, rastreamento e profundidade (ainda não feitos)

1. Conferir HEAD e últimas execuções da branch. Ler topo do STATUS e este handoff inteiro. Manter validação pelo Actions após cada incremento; não exigir restauração do ambiente local nem publicar release sem nova instrução.
2. Implementar acompanhamento visual do assunto, aproveitando foco por toque/AE/AF existentes. Distinguir autofocus, rastreamento e bloqueio. Adicionar confiança/perda do alvo, retorno ao automático, suavização e limite de frequência de novos comandos AF; parar/resetar ao mudar modo/lente/zoom e com foco manual/bloqueado. Testar movimento, oclusão, regiões sem textura e câmera frontal, sem assumir que um único ponto de foco equivale a rastreamento.
3. Para vídeo com profundidade, examinar `camera/PortraitPreview.kt`, `camera/PortraitMaskAnalyzer.kt`, `processing/DepthEstimator.kt` e os modelos já presentes. O efeito atual é **PREVIEW apenas**. Não adicionar um controle dizendo que o MP4 recebe desfoque antes de implementar uma saída VIDEO_CAPTURE real.
4. Planejar composição dos efeitos: efeito combinado PREVIEW|VIDEO_CAPTURE não pode simplesmente disputar o alvo VIDEO_CAPTURE com OverlayEffect de assinatura. Resolver a composição e a posse das superfícies antes de ligar ambos. O pipeline existente usa EGL e timestamps nas saídas; precisa de validação de rotação/espelho, inclusive frontal.
5. Reaproveitar o que PortraitMaskAnalyzer já faz: máscara a 10 Hz (3 Hz em cena parada ou aquecimento moderado), profundidade a 900 ms (2,5 s em cena parada, pausada em moderado), média temporal 40% anterior/60% atual, pausa do desfoque em aquecimento severo. Não refazer esses mecanismos. Ampliar para movimento de sujeito/câmera e bordas finas sem rastros, com orçamento de FPS e consumo no vídeo. DepthEstimator.Session já seleciona Vulkan quando disponível e registra o backend. Depth Anything/NAFNet são opcionais no APK de release; falta de modelo deve ser informada honestamente. Não manter inferência pesada sem necessidade.
6. Só depois afirmar profundidade no MP4, ou alta qualidade de bordas/foco, com testes sintéticos e arquivos reais do aparelho. Não copiar algoritmos proprietários nem criar controles sem funcionamento.

## Texto para continuar em outra sessão

Continue Nova Camera na branch `codex/video-estabilidade-0-20`, preservando a origem `ccr-7443eaac-tqygyg` e o release `nova-test-58`. Confira o HEAD, leia `docs/pendente/NOVA_HANDOFF_CODEX.md` inteiro e o topo de `docs/STATUS.md`. O código está em 0.21.1; o último commit do app é `33c02249d65838ed8283d335ff32ab1979d17c4f`. GitHub Actions foi explicitamente autorizado como validação porque o ambiente local não tem Gradle e o proxy falha. O workflow android-check.yml roda em push nesta branch; não dispare releases. Já há correções de ordem GL/fallback da assinatura, recuperação limitada, qualidade explícita sem fallback silencioso, leitura real do MP4 e gravação rápida por gesto. Consulte os resultados de CI no handoff e não confunda com validação física. Próximo incremento: foco com rastreamento visual real; depois profundidade na prévia e no MP4, frontal/traseira, resolvendo a composição com a assinatura. Preserve assinatura Android, offline, configurações e tom do Noturno. Commits pequenos em português, versionName/STATUS por versão e CI após cada etapa.
