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
