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
