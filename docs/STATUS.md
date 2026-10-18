# 0.26.2 — foco honesto e proteção limitada da rajada

- Foco por toque só fica marcado como confirmado se CameraX retornar sucesso AF. Antes, bastava o callback existir ou solicitar FLAG_AF. Razões distintas para AF dispensado (manual/bloqueio/ponto confirmado/OEM/contínuo) e pedido não suportado/rejeitado. Mantém foco automático e não inventa foco no ponto.
- Vigia não reabre a câmera durante pausa legítima da aquisição: janela limitada por pedido (10s ou exposição longa+3s) e total (30s ou orçamento da rajada, máximo180s). Pedidos não renovam o prazo total; liberação após aquisição/pause/release/bind. Não desliga a recuperação de falha real. Testes de oito entregas, ausência de entrega, limite total e exposição longa.
- Callbacks/timeoutAF antigos não alteram captura nova: identidade do PhotoTrace protege vigia, restauração de foco/EV/zoom e estado do obturador. Troca intencional de modo não é bloqueada pelo vigia.
- Diagnóstico de STILL_CAPTURE via callback de sessão registra lógico, físico anunciado/solicitado, focal, AF e timestamp quando HAL entrega; EXIF da referência independente, sem confundir com óptica da prévia. Não obriga lente não suportada. Congelamentos fora da rajada ainda requerem registro físico.
- Alta qualidade com estado severo é adiada explicitamente antes da rajada; se aquecer durante aquisição, preserva original em vez de processar só1 dos8 e reduzir qualidade. Noturno fora deste perfil mantém algoritmo/tom. Listas JPEG da captura liberadas após transferência à fila/coleta. Interface avisa “Foto capturada · processando” ao terminar aquisição.
- Fonte finalbeb201f aprovada em assembleDebug/testDebugUnitTest/lintDebug no Actions39089312391; etapas39089067201/39089230751 também aprovadas. APK de teste ainda não confirmado.
- Não alterado agendamento CameraX para oito pedidos simultâneos: sem evidência de suporte/benefício e risco HAL/memória. Os7,4–9,1s ainda precisam dos intervalos pedido/início/entrega do diagnóstico completo. Não afirmar aceleração da aquisição nem solução geral de congelamentos. CI e teste S24 pendentes.

---

# 0.26.1 — memória da super-resolução e diagnóstico do acabamento

- SR deixa de reter sete mapas de confiabilidade que não consulta: a confiabilidade continua calculada para selecionar quadros, campos subpixel e fusão não mudam. Em 4080×3060/fator2, ~83,3 MiB deixam de permanecer retidos durante SR. Isto é contagem de buffers, não PSS medido no aparelho. Outros caminhos mantêm seus mapas.
- Sai a troca automática 2×→1,5× por memória livre instantânea. Sob pressão recolhe temporários uma vez; se não houver folga para a escala selecionada, preserva o original e informa que a versão de alta resolução não foi produzida. OOM continua protegido. Não prometer 49 MP em toda situação de recursos insuficientes.
- Tempos individuais de detalhe, tons, pouca luz, crominância e desfoque no diagnóstico, além do total. Limite de eventos por foto128, registros8. Regressão compara pixels SR/seleção de oito quadros com/sem retenção; CI pendente. Qualidade real, latência, PSS e temperatura exigem S24.
- 0.26.0 corrigida em3bf8835 aprovada no Actions39088782041 (compilação/testes/lint; equivalência JNI/Kotlin executada). Benchmark real do servidor e protocolo emP0_ETAPA2_VALIDACAO.md; não é ganho S24. CI39088522688 falhou em C++ com exceções desabilitadas; corrigido apenas no novo fonte. Busca SSD e suavização SR também portadas com testes de equivalência. Nenhum ganho físico declarado.

---

# 0.26.0 — P0 etapa2: cálculos nativos equivalentes

- Align.search, SuperRes.mergeTile eDetail.applyBand ganham laçosC++ na biblioteca já existente, Kotlin mantido como fallback/referência. Mesmos oitoquadros, posições/pesos/núcleo/rejeição de movimento/cores/pele/limites; semfast-math/FMA, sem modelo novo ou GPU presumida. SR cacheia mínimo/máximo3x3 da referência em vez de repetir por amostra; buffers limitados ao bloco. JNIsearch copia apenas região+busca, não plano inteiro por célula.
- Regressão real compila JNIhost no teste e compara pixels/decisões com Kotlin para cores, ruído, movimento, margens e intensidades. Benchmarkpareado3x com aquecimento de ambos, tempos impressos, sem limiar de performance instável e sem chamar host deS24. CI pendente. Nenhuma mudança de assinatura/dependências/permissões/tomNoturno.
- Baseline físico e escopo emP0_ETAPA2.md. Próximos incrementos: memória, aquisição, foco/prévia. Não considerarP0 concluída sem melhora mensurável noS24.

---

# 0.25.4 — P0 etapa2: escala energética incerta

- Baseline físico0.25.3 do dono registrado antes de editar emP0_ETAPA2.md; basee14795c/CI39087054435 aprovada. Retrato total79.17s/65.80s,Foto60.44s,8quadros7.41–9.10s,SR27.74–31.41s,memória962.5MiB,saídas49/28MP. Fotos/diagnóstico completo ainda não anexados nesta etapa.
- Escala do firmware não confirmada: potência deixa de entrar nas médias confiáveis. Valor sob hipóteseµA continua explícito e não confiável; nenhuma multiplicação1000. RegistraCURRENT_NOW/AVERAGE, contadores de carga/energia e deltas com unidadesAPI como hipótese. BatteryStats requerADB externo; não adicionada permissão de sistema. Teste reproduz−1442/4.02V sem chamar5.8mW de consumo validado.
- CI desta revisão pendente. Próximo: acelerar cálculos equivalentes, diminuir memória/decodificações, medir aquisição e foco, preservar8quadros/qualidade/escala e entregarAPK assinado após etapa estável. Sem conclusão de economia ou ganhoS24 ainda.

---

# 0.25.3 — P0: APK68 entregue, instrumentação e eficiência inicial

- **APK de teste assinado disponível:0.25.3/build68 (1068).** [ZIP com app-release.apk](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39086391072/artifacts/12120593095), artefato12120593095,132595692bytes, digest doZIP sha256:f4862631cc0244863fdbcaeea8699c9f505cab7e089f29c0c7abe051136940ca. Expira2026-11-17T01:32:18Z (16/11 à noite em Brasília). Mesmo certificado permanente, nenhum release publicado.
- [Build39086391072](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39086391072), job117553756728 aprovado: **267 testes aprovados,5 dispensados,0falhas**, lint, JPEG sintético200MP, NAFNet/DepthAnything, assembleRelease, pesos offline/profundidade=True, certificado/versionCode/upload. Publicação skipped. Fonteapp3634fe000a70eb63127b5b6fba50dd39eb1805bf (CI39085985023 aprovado); fonteAPK11294a4d579e33f39066d75356f4ddebe295fb0b. Gatilhooriginal restauradod918218de608bdc4f66c97cda16eb6be20888a8e/bloba82bc113b2de207b7a3e9a9660cc19af520167ff. CIs fonteassinada39086391077/restauração39086398000/handoff39086417350 aprovados.
- **Primeira etapa P0, sem benchmark físico antes/depois.** Ainda não comprovadas redução de consumo/temperatura, aceleração significativa da rajada, qualidade/SR real ou custoGPU. Fonte de linha de base é código/CI, não mediçãoS24. Roteiro/controlados e pendências específicas emP0_DESEMPENHO_RESULTADOS.md; obter diagnósticos por etapa eJPEGs reais localmente. Estados pendentes antigos abaixo são histórico.
- Etapas0.25.1/0a2d1bb e0.25.2/b814212 aprovadas em assembleDebug/testDebugUnitTest/lintDebug nos Actions39085319391 e39085617083. Primeira0.25.1 falhou em expectativa legada de mA, corrigida conforme unidade da API, sem remover testes. Base, auditoria, fontes e limites em P0_DESEMPENHO_BASELINE.md e P0_DESEMPENHO_RESULTADOS.md.
- Prévia MediaPipe extrai só pessoa/fundo: elimina três planos Kotlin descartados (fundo intermediário/cabelo/rosto). Mesma aritmética/pixels de pessoa; foto final mantém caminho completo, rede/delegate/entrada e refino. Carga/entrada/chamada/extração e profundidade/cópias agora separadas no diagnóstico; não muda backend sem benchmarkS24.
- GL recebe só a última máscara pendente; prazo cinematográfico750ms conta desde análise, sem renovar ao chegar à textura. Entrada nova invalida máscara/temporal/calibração antigas. Limpeza substitui upload pendente. Calibração e desfoque válidos preservados.
- Fotografia registra callback/timeout/sucessoAF e intervalos dos timestamps dos oito buffers, separados do atraso de entrega. Marca inicial usa mesmo relógio do ShotTimer. Protege segundo disparo durante AF/rajada; libera após aquisição como antes, não espera terminar o processamento. Alta resolução8quadros/outScale2 e algoritmos preservados.
- **Fonte3634fe0 aprovada em assembleDebug/testDebugUnitTest/lintDebug no Actions39085985023. APK68 assinado confirmado acima. Sem comparação física de latência, detalhe, taxaSR, GPU ou energia nesta sessão.** Não afirmar aceleração significativa/economia comprovada. Roteiro A/B inclui repetição/controle térmico, qualidade real e origem dos tempos. Nenhuma assinaturaAndroid, permissão, modelo/dependência ou mídia pessoal alterada.

---

# 0.25.2 — custo do compositor e trabalho GL redundante

- Etapa anterior0.25.1 corrigida aprovada em assembleDebug/testDebugUnitTest/lintDebug no Actions39085319391 (0a2d1bb). Falha histórica39084985949 preservada abaixo.

- Estende para o Retrato fotográfico a supressão de blur/cópia sem máscara ou contribuição visível. Cinematográfico já fazia parte disso desde0.24.6; calibração pendente continua com cópia, transição válida continua com blur. Shaders, resolução1/4 do blur, raios/intensidade, FPS e arquivo não mudam.
- Cache de localizações por programa/contexto (inclusive ausentes); glTexSubImage2D para atualizar máscara de mesma geometria, glTexImage2D só ao alocar/mudar tamanho. Conteúdo/formatos iguais. FBOs já eram reutilizados e logo já era cacheada, sem duplicar correções.
- Diagnóstico GL por sessão: preparação EGL, render/update/preparo/cópia/blur/composição/swap/upload, contagens, média/máximo, cadência de render, realocações de máscara/FBO e bytes de upload/readback conhecidos. Agregação sem texto por quadro, publicação no máximo1Hz; não usa glFinish nem força sincronização para medir. Wall não éGPU; cadência de render não éFPSMP4. Alocações internas do driver não medidas.
- CI desta revisão pendente. Benefício físico, bordas/qualidade e temperaturas ainda dependem do S24; sem afirmação de economia comprovada. Base e protocolo em P0_DESEMPENHO_BASELINE.md.

---

# 0.25.1 — investigação P0: energia e etapas da captura

- CI39084985949: compilação concluída; uma falha em teste legado que exigia a reescala indevida850→850000µA (264 totais/5 dispensados). Expectativa atualizada conforme BatteryManager; nova execução necessária. Pausa de análise limpa máscara/cache para não manter cena antiga; leitura bruta preserva sentinelas, potência continua indisponível para elas.
- Baseline antes das alterações: P0_DESEMPENHO_BASELINE.md, base c56ee20/CI39083686479 aprovado. Feedback positivo da0.25.0 preservado; sem benchmark físico controlado.
- CURRENT_NOW respeita µA da API, sem inferência de mA por magnitude. Diagnóstico expõe bruto/sinal/tensão/unidade/potência/conexão/temperatura e n válido por grupo. Não explica sozinho14W anteriores nem prova economia.
- Retrato mantém oito quadros/saída2× e algoritmos. JPEG fecha ImageProxy antes de EXIF/análises; prévia suspende novas inferências enquanto fotografia captura/processa, sem rebind. Operação nativa já em voo termina. Fotos continuam na fila existente e obturador é liberado após aquisição.
- Tempos correlacionados por disparo: AF, pedidos/inícios/entregas/cópias de cada quadro, rajada, fila, decode/orientação, alinhamento, SR/quadros usados/saída, segmentação/profundidade/refino, render e codificação/gravação. Tempos wall, nãoGPU. CI desta etapa pendente; latência/qualidade/autonomia antes/depois exigemS24.

---

# 0.25.0 — Vídeo comum separado do cinematográfico do Retrato (17/10/2026)

- **APK de teste disponível:0.25.0, build67 (1067).** [Baixar ZIP com app-release.apk assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39083102925/artifacts/12119587294), disponível até16/11/2026 à noite (Brasília). [Build39083102925 aprovado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39083102925): **254 aprovados,5 dispensados,0 falhas**, lint, JPEG sintético200MP, modelos NAFNet/DepthAnything, assembleRelease, pesos offline/profundidade=True, certificado/versionCode/upload conferidos. Publicação skipped; nenhum release criado.
- Artefato12119587294,132578292 bytes,digest do ZIP sha256:3c3d12390e73e712c5b8f5a880afea68bbdca7096b2efa0126c0a0f27cb5bb5e,expira2026-11-17T00:39:48Z. FonteAPKe8ea4f2a220fda25272f6704bf01a1c4ef8a9261,app503ded4df96674375121873f1f9ce387688feb18,CI39082782966 aprovado. Gatilho original restaurado7e10473755fd98f5bbec15199897d73999d9b4f0. A fonte aprovada não mudou; handoff contém continuidade completa.
- **Uso:** Vídeo comum mantém preferências e não inicia profundidade. NoRetrato, ativar opção cinematográfica, confirmar perfil separadoFHD30fixo/EISoff e fazer novo gesto; toque/foto,segurar/vídeo,direita/iniciar+lock,vertical/zoom. Trackingpor toque noRetratoNova; limites de câmera/OEM/mudança de sessão respeitados.
- **Validação física pendente:** qualidade de máscara/foco eMP4,frontal/traseira/tele/interrupções, consumo/temperatura antes/depois. Medidas de eficiência estão implementadas, mas não há prova de autonomia melhor. Denoise temporal e refinamentos avançados permanecem pendentes. Estados pendentes de geração/CI abaixo são histórico superado por esta entrega.


- Fonte final503ded4df96674375121873f1f9ce387688feb18, **CI39082782966 aprovado** (compile/testes/lint). APK67/1067 em geração no39083102925, fontee8ea4f2a220fda25272f6704bf01a1c4ef8a9261; artefato ainda não confirmado. Gatilho original restaurado7e10473755fd98f5bbec15199897d73999d9b4f0, publicação deve ser skipped. Etapas anteriores recursos/escopo/gestos/métricas/prontidão aprovadas39082038819/39082206619/39082254109/39082341619/39082511102. Conferir validação final antes de gerar e anunciar APK.

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

# 0.24.6 — marca orientada no compositor e passes de desfoque sob demanda (17/10/2026)

- **APK de teste disponível: 0.24.6, build66 (1066).** [Baixar ZIP com app-release.apk assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39074742550/artifacts/12116148402), até16/11/2026. [Build39074742550 aprovado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39074742550): **244 aprovados, 5 dispensados, 0 falhas**, lint, JPEG200MP, modelos offline, assembleRelease e certificado/versionCode/upload verificados. Publicação skipped; nenhum release criado.
- Artefato12116148402, 132573776 bytes, digest do ZIP sha256:ddbbae52c218a8c72713bb10487bb89229c6e7588b8b61965fb2bc07de70b793; expira 2026-11-16T22:28:41Z. Fonte APKafcf41f1667ccec3abbf4982c9c242acdb45bf26, appda1e33b42a65c71a41c91e7235cc8745bd42d15d. Gatilho restaurado25c8445411a2c930ca63b201de5048d6a19947c1; aplicativo não mudou após a fonte aprovada. Links/limites completos no handoff.
- **Ainda requer S24:** máscara, foco/rastreamento e marca nos pixels MP4/frente/trás/espelho, interrupções e temperatura. Teste frio/fora do carregador; alternativa comum é explícita e não reduz qualidade. Denoise temporal avançado segue pendente; não há autonomia melhor comprovada. Os estados de CI/APK pendentes abaixo são histórico superado por esta entrega.


- **Revisão final aprovada**: assembleDebug/testDebugUnitTest/lintDebug no [Actions39074393867](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39074393867), fonte da1e33b42a65c71a41c91e7235cc8745bd42d15d. APK66/1066 em geração no [39074742550](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39074742550), fonteafcf41f1667ccec3abbf4982c9c242acdb45bf26; artefato ainda não confirmado. Gatilho original já restaurado25c8445411a2c930ca63b201de5048d6a19947c1, sem release automático.

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
- Regressões cobrem resultados sem campos opcionais, callback antigo, recuperação espontânea enquanto UI estava ocupada e perfis sem observação válida. Compilação/testes/lint aprovados no Actions39073924133. Essas causas verificadas no código não provam explicar todos os congelamentos do HAL/GL no S24; teste físico continua necessário.
- Inclui escolha térmica0.24.4; nenhuma queda silenciosa de resolução/FPS, assinatura Android ou motor fotográfico alterados. Próximo: confirmar CI, examinar geometria da marca compartilhada, gerar APK com fluxo assinado sem release.

---

# 0.24.4 — feedback térmico do S24 e escolha de vídeo comum (17/10/2026)

- Base remota 1f49b39bd9037f1a3a1a34e655dcf667fa50f680 conferida; baseline Actions39071730931 aprovado antes de editar. Feedback real 0.24.3: calor severo3, carregando,42.7–43.6°C; potência indisponível. Rastreamento e máscara suspensos; esperar máscara não resolveria o calor. Sem mídia pessoal no Git.
- O botão oferece escolha explícita de vídeo comum nesta sessão, conserva resolução/FPS/EIS e preferência cinematográfica; reconfiguração não começa arquivo, exige novo toque. Gesto rápido sob calor não fica aguardando máscara impossível. Proteções térmicas mantidas, sem inferência forçada. Diagnóstico distingue compositor ligado de máscara atual.
- Diagnóstico do dono confirma MP4 comum1080×1920/AVC/29.97fps médios; não prova cinematográfico/marca nos pixels. Crashes listados são da0.20.0, não novos. CPU/PSS/cadência são amostras reais sem A/B controlado; não há prova de autonomia melhor. Profundidade fotográfica937ms não é medição do cinema.
- Compilação, testes unitários e lint0.24.4 aprovados no Actions39073650405 (fontef33a7815e003c4ac28082627f9a29c55d2eb1aa1). Próximo: corrigir vigia que usa metadados opcionais como sinal de vida e monitora sessões sem callback/prévia isolada; limitar mensagens/recuperações, testar e entregar APK assinado sem release. Investigar orientação da marca do compositor em etapa específica. Denoise temporal/qualidade avançada permanecem pendentes.

---

# 0.24.3 — preparação segura do vídeo rápido com profundidade (17/10/2026)

- **APK de teste disponível: 0.24.3, build65 (1065).** [Baixar ZIP com app-release.apk assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39070952578/artifacts/12114374400), até 16/11/2026. Fonte `aa580a42033fcf0cd2f8dad6469f6714bf389758`; mesmo certificado permanente, modelos NAFNet e Depth Anything incluídos. Nenhum release publicado.
- [Build65 aprovado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39070952578): **235 testes aprovados, 5 locais dispensados, 0 falhas**, lint, JPEG sintético 200 MP, conversões dos modelos, assembleRelease, pesos offline (profundidade=True), assinatura/versionCode e upload conferidos. CI debug completo também aprovado em [39070952531](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39070952531) e [39071002477](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39071002477).
- Artefato 12114374400, 132570598 bytes, digest **do ZIP** sha256:b7eeecf1131dac7f192dd7b13331b44e6c48d55f3917142c4bba97e62f96a038; expira 2026-11-16T21:34:56Z. Gatilho original restaurado em `c3cb1da536e837931027760184e234f2cd80c0d3`. Builds63/64 cancelados e não entregues; não usar como novidade.
- **Uso:** rastreamento por toque continua opcional. Cinematográfico experimental exige ativar nas configurações e escolher FHD/30/EISoff manualmente; aguardar máscara e ajustar desfoque no visor. Pressão longa da miniatura oferece primeiro acabamento suave de sombras e comparação; atéFHD/60/SDR/2min, original preservado. Denoise temporal avançado não está incluído.
- **S24 pendente:** confirmar efeito e marca nos pixels do MP4, foco/rastreamento, máscaras/contornos, frente/trás/rotação/espelho, gesto do Retrato, áudio, latência, memória e temperatura/consumo. Não há MP4 pessoal novo ou teste físico desta entrega. Sem prova de autonomia melhor. Roteiro [VIDEO_EVOLUCAO_S24_VALIDACAO.md](pendente/VIDEO_EVOLUCAO_S24_VALIDACAO.md).

- Gravação rápida iniciada em Foto/Retrato agora aguarda câmera e máscara atuais antes de consumir a intenção de START. Espera limitada a 8 s, com indicação de preparação; soltar, mudar sessão, sair do app ou restaurar modo cancela, sem gravação tardia. Vídeo comum continua começando assim que pronto.
- Novos testes reais de fila principal simulam máscara atrasada, início único, soltar durante preparação, troca de sessão e prazo esgotado sem arquivo. Mantém a correção do rastreamento, troca GL serial, cinematográfico opcional e acabamento de sombras das etapas anteriores.
- Build64/0.24.2 substituído por esta revisão; não entregue. Novo APK assinado somente após testes/lint da fonte final. Validação física de MP4, bordas, foco/áudio/temperatura e denoise temporal avançado continuam pendentes.

# 0.24.2 — drena também liberação GL já solicitada (17/10/2026)

- Revisão final cobre efeito já retirado do slot cujo cleanup ainda está enfileirado: marca de aposentadoria acompanha a fila, não apenas a referência atual. Novo teste cobre slot vazio com trabalho GL pendente; troca para cinematográfico continua aguardando a limpeza real. Perfis bloqueados deixam de criar efeito de assinatura que não será utilizado.
- Inclui todas as etapas 0.22.1/0.23.0/0.24.0/0.24.1 abaixo, com grupos de energia separados por função. Núcleo anterior c4d4e8a aprovado em CI39069941647; execução assinada63 não será a entrega final, substituída por esta revisão com testes completos antes de gerar APK.
- Próximo build de teste assinado com publicação skipped; ainda sem APK confirmado. Validação S24/MP4 e consumo permanecem pendentes, assim como denoise temporal avançado. Original, assinatura permanente e main preservados.

# 0.24.1 — troca serial dos efeitos e proteção por memória (17/10/2026)

- Revisão c4d4e8a09df5d0a70d401e4f68223f65f63e4428 aprovada em compilação/testes/lint no [Actions 39069941647](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39069941647). Rótulos de diagnóstico agora distinguem cinematográfico/acabamento para comparações de consumo; nenhum ganho demonstrado. Roteiro: [VIDEO_EVOLUCAO_S24_VALIDACAO.md](pendente/VIDEO_EVOLUCAO_S24_VALIDACAO.md).

- 0.24.0 aprovada em compilação, testes e lint no [Actions 39069611401](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39069611401), app 97ce062cb37539b4975fd9bcae6806514f80f7c8.
- Troca da assinatura comum para o cinematográfico aguarda também a limpeza GL que o close() do CameraX enfileirou. Barreira assíncrona na fila antiga antecede criação do contexto novo; sem bloquear UI ou sobrepor limpeza/criação. Teste ordena cleanup→callback→próximo contexto. Retorno ao comum já aguardava liberação do renderizador cinematográfico.
- Pressão de memória encerra acabamento opcional preservando original, além das proteções de calor/captura existentes. Não altera qualidade da gravação para acomodar os recursos.
- Inclui PixelCopy reduzido assíncrono, cinematográfico experimental opcional FHD/30/EISoff e primeiro acabamento de sombras opcional até FHD/60/2min. Recursos físicos, marcas no MP4, máscaras e consumo ainda exigem validação S24; denoise temporal avançado não implementado.
- CI final e APK assinado desta versão pendentes nesta entrada. Assinatura Android, main, modelos e versão anterior preservados.

# 0.24.0 — primeiro acabamento local opcional do vídeo (17/10/2026)

- Base cinematográfica 0.23.0 aprovada em assembleDebug, testes unitários e lint no [Actions 39069180455](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39069180455), commit 677b94522383a05b5c41b192ead61d7958540c00. Primeira tentativa 39068951806 falhou no callback antigo de PortraitMaskAnalyzer após adicionar parâmetros opcionais; callback nomeado corrigiu a compatibilidade.
- Pressão longa na miniatura de vídeo abre confirmação de acabamento. Nesta primeira etapa, até Full HD/60, SDR AVC/HEVC e 2 minutos. Analisa oito quadros pequenos e, se necessário, abre sombras muito levemente com matriz RGB limitada e constante ao longo do clipe. Sem sharpening, geração de detalhes ou mudança de balanço de branco. Se não indicar ajuste, mantém original sem gerar cópia.
- Exportação local assíncrona Media3 1.8.0, encoder sem fallback de resolução, bitrate solicitado pelo original (mínimo conservador), original intacto e nova cópia em Movies/Nova Camera/Acabamento. Progresso/cancelamento e comparação por reprodução Original/Nova, com último par persistido; não reaplica cumulativamente sobre a versão derivada quando o original está conhecido.
- Antes de disponibilizar, lê MP4 exportado de verdade e exige resolução/orientação visual equivalentes, mesmo MIME, quantidade e sequência normalizada de PTS de vídeo (tolerância 1 ms), áudio com mesmos bytes SHA-256, MIME/canais/taxa, cadência e sincronismo relativo (2 ms). Divergência descarta a cópia, nunca o original. FPS nominal da API não substitui essa leitura.
- Uma tarefa e uma vaga de fila; análise/IO em worker de baixa prioridade; exportação via GPU/codecs do dispositivo. Calor severo ou nova captura cancela com aviso. Profundidade pesada pausa durante acabamento. Funciona enquanto o processo existir; não promete sobreviver ao encerramento do app. Nenhuma nova permissão, serviço ou INTERNET; teste verifica manifesto mesclado após a dependência.
- **Limite explícito:** esta é a infraestrutura e o primeiro ajuste tonal; redução temporal de ruído, compensação de movimento e refinamento de texturas em vídeo ainda não implementados. Nenhum ganho de bateria ou qualidade demonstrado no S24. Perfil cinematográfico e marca no MP4 também exigem teste físico.
- Novos testes verificam limites de sombras/altas luzes, quadros perdidos/duplicados, PTS reordenado, sincronismo e ausência de INTERNET. CI 0.24.0 e APK assinado pendentes nesta entrada.

# 0.23.0 — cinematográfico experimental em prévia e saída de vídeo (17/10/2026)

- Correção 0.22.1 aprovada no [Actions 39068644575](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39068644575), commit 451576d90d0de11f75fd208269558a11d6cac1bd, antes desta etapa. Primeira tentativa 39068369838 compilou o app mas falhou ao compilar um teste (tipo desnecessário em RuntimeEnvironment.getApplication), corrigido.
- Efeito existente do Retrato passa a ter fábrica cinematográfica com alvos PREVIEW|VIDEO_CAPTURE. Composição e marca compartilham um renderizador, sem dois efeitos disputando VIDEO_CAPTURE; assinatura comum preservada fora do cinematográfico.
- Ativação opcional nas configurações; exige Full HD/30 e EIS desligada escolhidos explicitamente. Outros perfis ficam bloqueados com explicação, sem reduzir resolução/FPS ou alterar preferências. Vídeo comum mantém suas opções ao desativar. EIS foi restringida nesta etapa para evitar máscara de análise não estabilizada sobre vídeo estabilizado.
- Reutiliza PersonSegmenter, DepthEstimator e DepthPortrait.plan com seleção por toque; coordenadas visor→sensor→análise/rotação. Análise KEEP_ONLY_LATEST reduzida, profundidade com orçamento próprio. Movimento/seleção descartam histórico inadequado, sem contornar o intervalo de inferência. Máscara vence em 750 ms; análise atrasada/calor severo suspende desfoque e informa vídeo comum.
- Desfoque ajustável ao vivo; gravação rápida de Retrato/Foto usa o perfil Vídeo e pode utilizar o efeito se o usuário o ativar. Início aguarda máscara atual; indisponibilidade é informada. Falha GL finaliza gravação antes de religar; erro antigo de outra geração não afeta sessão nova. Encerramento aguarda liberação anterior antes de criar o próximo contexto.
- **Ainda não validado no S24:** correspondência de máscara, cabelos/folhas, transições, marca/orientação/espelho nos pixels MP4, desempenho e temperatura. Segmentação de pessoa sem profundidade é identificada como tal; não há profundidade métrica nem prova de qualidade premium. Não afirmar que alterar targets prova o resultado físico.
- Novos testes cobrem perfil sem redução silenciosa, validade temporal e descarte por movimento/toque. CI desta versão pendente; pós-processamento local é a etapa seguinte.

# 0.22.1 — leitura assíncrona reduzida para rastreamento (17/10/2026)

- Feedback físico 0.22.0: captura de tela mostra encerramento por leitura lenta da prévia, Vídeo/4K/faixa 15–30 alvo. A imagem não comprova FPS/dimensões do arquivo nem foco/consumo; mídia pessoal não enviada ao Git.
- Causa no caminho de código: PreviewView.bitmap/SurfaceView copia buffer completo e aguarda PixelCopy com semáforo antes de reduzir. Substituído por PixelCopy assíncrono para destino de até 320 px, sem espera na UI, sem executor novo por quadro e sem stream extra de câmera.
- Uma cópia em voo, rejeição de erro/bitmap indisponível, geração/fechamento e prazo de resposta limitados. Transformação de escala/recorte/display mantém o espaço do visor. Removido o encerramento por três latências >60 ms do caminho bloqueante; prazo de validade e erros continuam sendo tratados. Custo passa a ser latência da cópia assíncrona, não ocupação da UI/GPU.
- Base HEAD 725195ae9ab74d845b9a4096387298837769e949 aprovada no Actions 39067748469 antes desta alteração. Compilação/testes/lint 0.22.1 pendentes; transformação frontal/paisagem e melhora real de latência ainda exigem S24.
- Usuário autorizou avançar também em profundidade cinematográfica e pós-processamento. Etapas serão independentes; não anunciar efeito no MP4 ou melhoria de qualidade sem implementação e teste real.

# 0.22.0 — rastreamento visual por toque, etapa experimental (17/10/2026)

- **APK disponível: 0.22.0, build 62 (1062).** [Baixar ZIP com app-release.apk assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39067165320/artifacts/12113024414), válido até 16/11/2026. Atualização de teste; mesmo certificado Android permanente, modelos NAFNet e Depth Anything incluídos. Nenhum release publicado.
- [Build assinado 62 aprovado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39067165320): **219 testes aprovados, 5 locais dispensados, 0 falhas**, lint, JPEG sintético 200 MP, conversões dos modelos, assembleRelease, pesos offline, certificado/versionCode e upload reais aprovados. Publicação pulada. Artefato 12113024414, 131002791 bytes; digest do ZIP sha256:4e5ed0f072cd4ff0b36cf268c19ae134b80ee7b9bb8584f3f1cd5687ddc5e7aa.
- Fonte do APK `ae2257698a67e4272234df23f758527026893bbd`, código do app `b4a97588ed4bff162b6a093e0d7d6aa40bffbbf0`. Gatilho temporário restaurado em `e869bdb182f478f41bc47a9ee15b130ccaffcc4e`. Main e versões anteriores preservadas.
- **Uso:** no Vídeo/Vídeo Pro, toque em contorno com textura; toque no indicador para voltar ao automático. Pressão longa mantém bloqueio AF/AE. Desative “Acompanhar assunto no vídeo” para AF comum e controle de consumo.
- **Validação física pendente:** precisão e nitidez do alvo frontal/traseiro, perdas/oclusão, gravação rápida a partir de Foto/Retrato, combinações 4K/FPS/zoom/marca, qualidade real dos MP4s, latência e temperatura/consumo. Não há S24 conectado nem prova de melhoria de autonomia. Cinematográfico e pós-processamento ainda não implementados.

- **Compilação, testes unitários e lint aprovados** no [Actions 39066797447](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39066797447), código `b4a97588ed4bff162b6a093e0d7d6aa40bffbbf0`. APK assinado build 62 aprovado no [39067165320](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39067165320), fonte `ae2257698a67e4272234df23f758527026893bbd`; artefato confirmado acima. Gatilho original restaurado em `e869bdb182f478f41bc47a9ee15b130ccaffcc4e`, publicação pulada nesta branch.

- Em Vídeo/Vídeo Pro, toque seleciona uma região com textura; correlação de luminância acompanha pequenos movimentos e atualiza regiões AF contínuo/AE compatíveis. Funciona sobre a prévia pública já orientada/espelhada, com mapeamento inverso para o sensor; aplicação real frontal/traseira exige teste físico.
- Retículo separado do foco comum, estado incerto e botão para automático. Oclusão/ambiguidade prolongada, quadro atrasado, troca de lente/enquadramento, pausa, foco manual, memória insuficiente e calor severo encerram o acompanhamento. Pressão longa mantém o bloqueio anterior. Preferência permite desativar e comparar com o AF comum.
- Sem classificação semântica, identificação individual, recuperação após oclusão ou máscara de profundidade. Não prometer rastreamento de qualquer objeto em qualquer movimento; retocar para selecionar novamente. O retículo é indicador na tela, não efeito gravado.
- Análise somente após seleção, uma tarefa em voo, buffers pequenos reutilizados, 320 px no lado maior; ~5 Hz em movimento e menor frequência em repouso/calor/economia. Snapshot público ainda aloca uma imagem da prévia; custo é medido e leituras lentas encerram o recurso. Sem novo stream CameraX, superfície GL ou mudanças nos FPS/resolução do MP4.
- Diagnóstico mede CPU/tempo da análise, custo da cópia, dimensões, perdas de confiança, CPU/PSS do processo e energia/temperatura existentes. GPU não medida; nenhuma melhoria de autonomia demonstrada. Protocolo: [RASTREAMENTO_S24_VALIDACAO.md](pendente/RASTREAMENTO_S24_VALIDACAO.md).
- Primeira execução [39066321775](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39066321775) falhou na compilação: extensão `orEmpty` indisponível para IntArray e inferência recursiva do Runnable. Corrigidos com array vazio explícito e tipo declarado; nova execução pendente.
- Segunda execução [39066492614](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39066492614): assembleDebug aprovado, 224 testes executados, 217 aprovados, 5 dispensados e 2 falhas nas asserções inteiras da geometria. Testes de movimento/perda/cancelamento passaram. Geometria será repetida com Skia nativo e tolerância explícita de até 1 pixel no centro/2 na extensão após roundOut; sem mudar a transformação de produção. Lint não aprovado nesta execução.
- Base 0.21.6 aprovada antes desta etapa. Novos testes cobrem movimento real, iluminação, oclusão, quadros atrasados, limites de cadência, espelho/rotação/recorte e cancelamento de sessão. Compilação/testes/lint aprovados na execução citada no topo; APK assinado disponível no link acima.
- Próximas etapas: validar o rastreamento no S24, depois profundidade cinematográfica real na prévia/MP4 (iniciar em FHD/30) e pós-processamento opcional preservando o original. Nenhum desses dois efeitos está implementado nesta versão.

# 0.21.6 — referência de desempenho antes do rastreamento (17/10/2026)

- Base atual `6503e0388420ff48800a0a24d18017200c1e1c9e`/0.21.5 aprovada em compilação, testes e lint no [Actions 39059803047](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39059803047), antes de mudar o aplicativo.
- Diagnóstico passa a medir CPU acumulada do processo, PSS, cadência de resultados de captura/perdas estimadas e custo da coleta, a cada ~10 s na fila de IO. Intervalos de atividades diferentes, pausas e contadores reiniciados não são misturados.
- Energia/temperatura existentes preservadas; identificação de resolução/FPS alvo/EIS/frente-trás para sessões comparáveis. Potência é do aparelho inteiro; GPU não é medida e latência de cópia não será apresentada como ocupação de GPU.
- Ainda não há comparação física controlada na 0.21.5/0.21.6, nem prova de melhoria de autonomia. O feedback “Deu certo” foi recebido, sem matriz detalhada de lente/FPS/MP4. Nova instrução do dono autoriza avançar nas próximas etapas.
- Compilação, testes unitários e lint aprovados no [Actions 39060797636](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39060797636). Próxima prioridade autorizada: rastreamento real, depois profundidade e pós-processamento, com eficiência integrada; preservar APK estável 0.21.5/build 61 e assinatura permanente.

# 0.21.5 — prévia após sessão recusada pelo CameraX (17/10/2026)

- **APK disponível: 0.21.5, build 61 (1061).** [Baixar ZIP com app-release.apk assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39059245337/artifacts/12110872722), até 16/11/2026. Mesmo certificado Android e modelos offline existentes; pode atualizar a instalação anterior.
- [Build assinado 61 aprovado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39059245337): 206 testes aprovados, 5 locais dispensados, 0 falhas; lint, JPEG sintético 200 MP, modelos NAFNet/Depth Anything, assembleRelease, inclusão dos pesos e continuidade de certificado/versionCode conferidos. Upload do APK concluído; publicação de release pulada.
- Fonte do APK `195add2ceb22c3fd2733f5f5614434f4627c1c50`, código do app `9d4257cfa3a1c587c979e503a295d3e8aceed7fb`. Gatilhos originais restaurados em `9942432d542cfc3f0cf9abd112779d6f8398e962`; nenhum release criado.

- Estende a prévia isolada da 0.21.4 à recusa de bind: mantém a escolha de qualidade, impede gravação em outra resolução e permite escolher uma combinação alternativa. Se até a prévia falhar, não reinicia em ciclo.
- Nova regressão verifica que recusa de bind encerra recuperações automáticas até uma escolha explícita do usuário.
- Base das correções 0.21.3 com sincronização do teste aprovada em compilação debug, testes completos e lint: [39058541277](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058541277). A primeira execução teve 1 falha de sincronização do teste, corrigida antes desta aprovação; não foi escondida.
- Compilação debug, testes completos e lint aprovados na 0.21.5: [39058891633](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058891633), código `9d4257cfa3a1c587c979e503a295d3e8aceed7fb`. Inclui todas as correções das etapas anteriores. 0.21.4 também aprovada em [39058739596](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058739596).
- **Validação física pendente:** 5×/4K60, marca nos pixels do MP4, áudio/reprodução, estabilidade e temperatura devem ser testados no S24 Ultra com o APK novo. Desenho na textura e capacidades anunciadas não substituem essa prova. Pós-processamento, rastreamento e profundidade continuam pendentes.

# 0.21.4 — prévia segura após esgotar a recuperação (17/10/2026)

- Depois de finalizar o arquivo e esgotar o orçamento de recuperação, liga somente a prévia; não deixa ativa a sessão de gravação que parou. Obturador continua bloqueado até nova escolha explícita.
- Esta prévia não reinicia o orçamento nem dispara ciclos de reabertura. Alternativa de FPS inferior na mesma resolução continua exigindo escolha do usuário.
- Mantém as correções de negociação, EIS, assinatura e metadados da 0.21.2/0.21.3. Política térmica preservada: o estado moderado 2 do diagnóstico não bloqueia 4K isoladamente; o limite de vídeo exige estado severo ou economia ativa.
- Testes da máquina de recuperação e da interface serão repetidos junto de compilação e lint. CI pendente; nenhuma validação física desta versão e nenhum APK novo disponível ainda.
- Roteiro físico: [VIDEO_S24_VALIDACAO.md](pendente/VIDEO_S24_VALIDACAO.md). Rastreamento, pós-processamento opcional e profundidade permanecem próximos passos após estabilizar captura no aparelho.

# 0.21.3 — assinatura de vídeo e espelho (17/10/2026)

- Assinatura deixa de ser removida por uma parada genérica da prévia. Falhas GL/combinações recusadas ficam isoladas no perfil afetado, preservando a preferência e outras resoluções/câmeras.
- Assinatura no vídeo frontal espelhado: composição inversa de recorte, rotação e espelho, com texto legível no canto escolhido; removido o descarte de quadros de assinatura por `isMirroring`.
- Textura da assinatura estática só é redesenhada quando muda a geometria/buffer, evitando bloqueio de Canvas a cada quadro de 60 fps.
- Cada handler GL identifica sua geração; erros atrasados de uma ligação anterior não desligam o efeito da nova. Mantida liberação serial antes de criar o efeito seguinte e finalização antes de religar durante gravação.
- Diagnóstico distingue assinatura solicitada, ativa e desenho na textura, além dos metadados reais do MP4. Desenho na textura não é prova de pixels presentes no MP4; confirmar visualmente no aparelho.
- Consulta também verifica a faixa automática de FPS; se a sessão só aceitar 30 fixos, informa o recuo sem reduzir resolução. Alternativa de 30 fps fica disponível após recusa/fim das tentativas de recuperação.
- Novos testes de pixels cobrem 0/90/180/270 graus e espelho, persistência de textura e mudanças de geometria. Teste da interface verifica que aceitar 30 fps exige confirmação e mantém UHD.
- Primeira execução da 0.21.3 ([39058243287](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39058243287)): compilação debug aprovada; 210 testes, 204 aprovados, 5 locais dispensados e 1 falha no teste da confirmação de FPS. Testes gráficos passaram. O teste precisava drenar a fila principal após o clique assíncrono do AlertDialog; sincronização corrigida e nova execução pendente. Lint não declarado aprovado nesta execução.
- CI final desta etapa pendente. Sem validação física da 0.21.3; UHD/60 em 5× e assinatura no MP4 final continuam exigindo teste no S24 Ultra. Não foi recebido MP4 pessoal nem enviado conteúdo pessoal ao repositório.
- Pós-processamento opcional, rastreamento e profundidade cinematográfica ainda não implementados; aguardam estabilização física da captura para a próxima etapa.

# 0.21.2 — negociação de vídeo e recuperação (17/10/2026)

- Base 0.21.1 preservada; [compilação, testes unitários e lint da base aprovados](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39056484866), sem alterar o app antes dessa execução.
- Feedback físico 0.21.1/build 60: Full HD salvo em 1080×1920, AVC e 58,90 fps médios; UHD/60 recusado, prévia parada em 1×/5×, assinatura ausente. Crashes apresentados são da 0.20.0, não desta versão.
- Consulta dos FPS da sessão CameraX com prévia, qualidade e efeitos; duração genérica de saída MediaRecorder deixa de vetar isoladamente uma combinação. Capacidade do encoder continua conferida.
- EIS de gravação substitui a solicitação de estabilização da prévia; CameraX coordena OIS e controle AE do vídeo. Recuo limitado sem EIS mantém resolução, FPS e assinatura; o arquivo ativo é finalizado antes de religar.
- Combinação recusada mantém apenas prévia e bloqueia gravação. Interface apresenta a restrição e oferece FPS inferior na mesma resolução somente com escolha explícita. Não há redução silenciosa de qualidade.
- Vigia ignora avisos de ligações antigas e dá margem inicial para a sessão começar a entregar quadros.
- Verificação desta alteração: CI ainda pendente; 4K/60 em 5×, estabilização, arquivo MP4 e fluidez exigem novo teste físico no S24 Ultra. Nenhuma alegação de suporte real a essa combinação.
- Próxima etapa crítica: assinatura segura no MP4. Pós-processamento opcional, rastreamento e vídeo cinematográfico continuam pendentes, em etapas independentes.

# Nova Camera — estado real dos recursos

Legenda da coluna **Validação**:
- **Testes**: coberto por teste unitário (lógica pura) ou pelo teste de fumaça da interface (Robolectric).
- **Compilação**: compila, passa no lint e é usado pelo app, mas o comportamento depende da câmera física.
- **Aparelho**: só pode ser confirmado no S24 Ultra / S25 Ultra. **Nada nesta versão foi testado em aparelho.**


## 0.21.1 — Finalização de vídeo após fechar a tela

**APK para teste físico:** 0.21.1, build 60 (versionCode 1060), [baixar ZIP com o APK assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39054257114/artifacts/12108213568). Extraia e instale `app-release.apk`. Testes/lint, compilação release, modelos offline e continuidade da assinatura passaram no [Actions](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39054257114). A etapa de criar release recebeu HTTP 403; o arquivo foi preservado como artefato por 30 dias (até 16/11/2026). Não existe tag nova-test-60 publicada por esta execução. A assinatura permanente foi mantida; nenhum teste físico foi realizado nesta sessão.

O controlador mantém a finalização do MP4 após solicitar parada. Se o callback chegar depois de
destruir/encerrar a Activity, a interface não tenta reproduzir som já liberado nem carregar miniatura
num executor encerrado. A medição do MP4 e a liberação do controlador continuam independentes da UI.

**Testes:** regressão Robolectric com a Activity real destruída antes de receber Finalize, junto
com a suíte completa: aprovados no Actions
https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39053015203 .
**Compilação:** assembleDebug e lintDebug aprovados nessa execução. **Aparelho:** não validado.

## 0.21.0 — Gravação rápida em Foto e Retrato

- Toque continua fotografando. Pressão longa prepara vídeo usando o mesmo controlador e as mesmas
  verificações de qualidade; soltura finaliza, arraste lateral de 64 dp bloqueia até tocar para parar.
- Preparação e intenção do gesto são separadas: soltar antes de terminar o bind não inicia vídeo
  depois. Home, cancelamento do toque e remoção da tela finalizam também a gravação bloqueada.
- Após Finalize retorna ao modo Foto/Retrato anterior. Não vincula streams adicionais aos modos de foto.
- Microfone ainda não autorizado: solicita permissão e exige um novo gesto, evitando gravação tardia
  ao voltar do diálogo. O toque simples e a gravação normal continuam disponíveis.
- O tempo de preparação depende da câmera; não há garantia de transição instantânea no S24.

**Testes:** máquina de estados de preparo, soltura, bloqueio e interrupções, mais gestos no botão
real em Robolectric: aprovados junto com a suíte no Actions
https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39052668646 .
**Compilação:** assembleDebug e lintDebug aprovados nessa execução. **Aparelho:** não validado; testar
Foto/Retrato, frontal/traseira, soltura rápida, bloqueio e interrupção antes de considerar pronto.
**Próximas etapas:** acompanhamento visual do assunto e vídeo com profundidade ainda não implementados.

## 0.20.2 — Qualidade explícita sem redução silenciosa e medição do MP4

- UHD/FHD/HD explícitos usam seletor sem fallback de resolução. Combinações recusadas ficam
  bloqueadas para gravação; o usuário escolhe outra configuração. O modo AUTO mantém a escolha
  automática de resolução. Temperatura/economia bloqueiam pedidos excessivos sem alterar preferências.
- Opções de FPS cruzam faixas da câmera, duração mínima anunciada da resolução e capacidade de
  entrada Surface de codificadores AVC/HEVC. Isso é uma triagem; o bind real ainda pode recusar
  combinações com estabilização/efeitos/lentes. Não é validação física de todas as combinações.
- A resolução vinculada (crop) é conferida e o rótulo usa a sessão vinculada. Nova conferência
  antes do disparo impede iniciar com uma resolução diferente da selecionada.
- Após Finalize, MediaExtractor lê dimensões, rotação, MIME do codec e timestamps de todas as
  amostras de vídeo. A leitura não decodifica imagens e roda numa fila própria. O relatório mostra
  FPS médio observado, inclusive com pouca luz; não afirma taxa constante. Divergências geram aviso
  e ficam no Diagnóstico, preservando o arquivo. Falha de leitura é informada como não medida.
- O visor indica FPS **alvo** (ou faixa automática), não FPS real ainda não medido.

**Testes:** adicionados casos 4K selecionado × FHD gravado, orientação retrato, 60 × 30 fps,
taxa variável, quadros B e arquivo curto sem taxa mensurável: aprovados junto com a suíte no Actions
https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39052167400 .
**Compilação:** assembleDebug e lintDebug aprovados nessa execução. **Aparelho:** não validado; não foi inspecionado um
MP4 real do S24 nesta sessão. O efeito GL e a qualidade precisam de teste frontal/traseiro e
Vídeo/Vídeo Pro no aparelho, em 1080p/4K e 30/60, com assinatura e estabilização ligadas/desligadas.

## 0.20.1 — Ciclo GL da assinatura e recuperação limitada do vídeo

Base 0.20.0 recompilada, com testes e lint aprovados no Actions:
https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39049540137 .

- O efeito anterior é fechado antes de criar o próximo na mesma fila GL. Antes, ao trocar
  resolução/FPS, o init novo podia preceder o release antigo, desfazendo o contexto da textura.
- RuntimeException das tarefas do OverlayEffect é encaminhada ao controlador sem matar o Looper
  necessário à liberação. O efeito falho é desligado nesta sessão; durante gravação, solicita Stop
  e só religa após Finalize. Não é um tratamento global de crashes.
- Recuperação do vídeo: duas tentativas por sessão, sem renovar o orçamento no bind e sem reduzir
  FPS/resolução. Ao esgotar, o vigia para; uma ação explícita permite nova tentativa.
- Diagnóstico distingue assinatura de vídeo de efeito de retrato. Registra falhas do efeito localmente.
- Encerramento pede finalização do arquivo antes de liberar o provider e os executores do app.

**Testes:** regressões de ordem GL, assinatura desligada/religada, mudança de configuração,
orçamento de recuperação e finalização passaram junto com a suíte completa no Actions:
https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39051779855 .
**Compilação:** assembleDebug e lintDebug aprovados nessa execução. **Aparelho:** não validado; o diagnóstico original
comprova a falha na 0.20.0, não a resolução no S24. Qualidade/FPS e leitura do MP4 são a etapa seguinte.

## 0.20.0 — Foco por toque confiável na tele e nas rajadas

O toque já focava e media no ponto (anel, confirmação por cor, toque longo = AE/AF bloqueado), mas
três falhas tiravam o foco do lugar na hora da foto:
1. **Toque antigo:** 4 s depois do toque o AF volta ao contínuo; quem enquadra a tele e dispara
   depois disso tinha a foto focada onde o AF contínuo quisesse. Agora, antes do disparo, o foco é
   refeito no ponto tocado (até 0,7 s; no Noturno até 1,2 s).
2. **Rajadas com AF contínuo:** Noturno já travava o foco no centro; super-resolução (10×) e alta
   resolução/multiquadro da tele (≥ 2,5×) não — o AF podia "caçar" entre os quadros e borrar a fusão.
   Agora travam antes (no recorte do 10×, antes de abrir a lente para a rajada). O retorno ao
   contínuo nunca acontece no meio de uma rajada.
3. **Zoom depois do toque:** a região medida fica fixa no sensor; ao mudar o zoom ≥ 15% ela não cobre
   mais o mesmo objeto (no 10× pode até sair do quadro). Agora o ponto é descartado e o anel some,
   como na câmera nativa. Toque longo (bloqueio) continua valendo.

Regras em `core/FocusPolicy.kt` (testes `FocusPolicyTest`). Validação: **Testes** para as regras;
**Aparelho** para o efeito real (tempo de foco na tele, AF durante a troca de zoom da SR).

## 0.19.1 — Alta resolução: 2× de fato e descrição correta no Retrato

Caso real (0.19.0): planta no Retrato a 3× saiu com 4590×6120 (28 MP, 1,5×) — a guarda de memória
exigia 2,2× o tamanho da foto livre e reduziu a escala; e a nota do Retrato apagava a da alta
resolução (a redução não aparecia na descrição). Agora: folga de 1,6× (a saída já é escrita
orientada e o resto trabalha por blocos) e notas somadas ("Alta resolução: … · Sem pessoa: …").
Detalhe comparado com a 0.18 na mesma planta: bordas das folhas limpas, sem o contorno claro de
nitidez exagerada.

## 0.19.0 — Alta resolução na tele (3× e 5×, Foto e Retrato)

Opção **Ajustes › Foto Nova › Alta resolução na tele** (ligada). No zoom óptico da tele (≥ 2,5×
equivalente, sem recorte digital), Foto ou Retrato: rajada de 8 quadros e foto final **2× maior**
(~48 MP a partir de 12 MP), montada pela mesma super-resolução da 0.18 (amostras reais na posição
exata; nada inventado).

- Memória: a foto de 48 MP é escrita já na orientação final, bloco a bloco (sem a cópia girada, que
  dobraria a memória). Antes, mede a memória livre: sem folga, cai para 1,5× ou tamanho normal e avisa
  na descrição. Com um quadro só (aquecimento), tamanho normal.
- Velocidade: núcleo por tabela (exp por amostra levaria ~15 s numa foto de 48 MP).
- IA de ruído (Retrato) não roda em fotos acima de 16,5 MP.
- Validação: testes (giro/espelho direto na saída idênticos ao `orient` pixel a pixel; tamanho 2×;
  ganho da SR mantido). Tempo e memória no aparelho: **não medidos**.

## 0.18.0 — Super-resolução multiquadro no zoom digital da tele (pinça)

Antes: 10× na lente 5× = recorte do centro de **um** quadro ampliado 2× por interpolação (granulado e
falso padrão na pinça). Agora (Foto, tele física, zoom digital ≥ 1,4× sobre a lente, ajuste
"Super-resolução no zoom da tele", ligado):

1. A rajada de **8 quadros** é feita na lente **sem recorte** (a prévia mostra a lente sem recorte por
   um instante); o zoom volta ao fim da rajada.
2. Alinhamento do motor (global, regiões, subpixel) + medição fina por bloco em resolução cheia, na
   luminância suavizada (o detalhe serrilhado enviesava a medida).
3. Cada amostra é colocada na posição exata numa grade `fator`× mais fina (núcleo gaussiano σ = 0,28·fator);
   robustez pela faixa local da referência (o detalhe real é aceito; o que se mexeu, rejeitado); onde
   nenhuma amostra chega, vale a referência interpolada.
4. Ideia publicada (Wronski et al., 2019), implementação própria; nada de IA gerando detalhe.

| Validação | Resultado |
|---|---|
| Núcleo, 8 quadros, deslocamentos verdadeiros | +2,1 dB sobre o zoom de um quadro |
| Núcleo, 6 quadros com ruído | +2,4 dB |
| Caminho completo (alinhamento do app + montagem) | +2,1 dB; visualmente, o zoom de um quadro mostra falso padrão (serrilhado) e a SR mostra o padrão verdadeiro |
| Objeto que se mexe entre quadros | rejeitado (sem fantasma) |
| No aparelho (JPEG processado pela Samsung, tremido real) | **não medido**; o ganho tende a ser menor que no sintético |

## 0.17.4 — Tele no escuro: quadros iguais no lugar do HDR e clareamento contido

Caso real (0.17.3): frasco a 5×, quarto escuro. Nova: tele real (115 mm, f/3,4), ISO 2056, 1/24 s,
"Nova HDR · 2 de 2 exposições" — escura, mas com veio da madeira e etiqueta nítidos. Samsung: 23 mm
(principal recortada, f/1,7 — 4× mais luz), clara mas com "aquarela".

| Item | O que faz | Validação |
|---|---|---|
| HDR forçado ("Automático (Nova)") com pouca luz | ISO ≥ 1600, ou sem faixa dinâmica a recuperar com ISO ≥ 200: 4 quadros iguais somados (tele ou principal) em vez de exposições diferentes | Testes |
| Clareamento em pouca luz (Foto, tele, HDR, Retrato) | Meios-tons sobem até a mediana 0,28 (Noturno: 0,42), até +1 EV, limitado pela folga das altas luzes; a partir do ISO 800, inteiro com 1600 | Testes; na foto real: +0,6 EV, branco do frasco sem estourar |
| Sombras sem cor falsa | Faixa reduzida às sombras profundas (até 0,12; antes 0,25 acinzentava a madeira) | Testes; simulado na foto real |

## 0.17.3 — Retrato sem "retângulo" nas mudanças de desfoque

Causa: o mapa de desfoque era alargado com janela **quadrada**; as regiões com níveis diferentes de
desfoque ganhavam cantos retos e bordas bruscas (retângulo quase nítido em volta da fita do frasco
5×). Agora a janela é um **disco** e a transição entre níveis é suavizada duas vezes. Validação:
teste do disco (sem canto reto); efeito na foto: Aparelho.

## 0.17.2 — Contraluz automática

Relato: apontando para qualquer coisa em contraluz, a prévia e a foto ficavam escuras. O fotômetro
da câmera mede a média do quadro; janela/céu/lâmpada estourados puxam tudo para baixo.

| Item | O que faz | Validação |
|---|---|---|
| Contraluz automática (Ajustes › Foco e exposição, ligada) | A prévia exibida é medida ~1,5×/s (64 px de largura, sem fluxo extra da câmera). Com ≥ 2% do quadro estourado, o centro (60% × 60%, sem os estourados) é levado à mediana 0,30 com compensação de exposição, até +1,5 EV, em passos de 1/3 EV. Sai sozinha sem área estourada, ao mexer na exposição, tocar para medir, travar AE, no Pro, no Noturno, gravando vídeo e durante rajadas | Testes (medição, alvo, passos, estabilidade); comportamento real: Aparelho |

## 0.17.1 — Foto escura no escuro (tele) e manchas roxas nas sombras

Caso real: frasco a 5× no Retrato, quarto escuro, 0.17.0: ISO 3069, 1/30 s, lente 3× com recorte (69 mm,
f/2,4), sombras roxas e "aquarela" no fundo desfocado; Pro e prévia também escuros.

| Item | O que faz | Validação |
|---|---|---|
| Exposição mais longa na prévia/foto (Foto, Retrato, Pro) | Faixa de 15–30 fps em vez de 30 fixos: no escuro o automático pode ir a 1/15 s (até +1 EV de luz real, ou o mesmo brilho com ISO menor). Com luz boa continua a 30 fps. O Noturno, que neste aparelho ficava sem faixa estendida (não há faixa abaixo de 15 fps), também passa a usar 15–30 | Compilação; Aparelho |
| Sombras sem cor falsa | ISO ≥ 800: a saturação das sombras profundas cai em proporção ao ISO (máx. 80% a partir de ISO 3200); luminância intacta. Roda antes do desfoque do retrato (que transformava as manchas em "aquarela") | Testes; simulado na foto real: roxo some do escuro |
| Retângulo nítido em volta de objeto próximo no Retrato (fita, mesma foto) | Corrigido na 0.17.3 | ver 0.17.3 |

## 0.17.0 — Vários quadros com luz média (estilo HDR+) e perfil Contraste

| Item | O que faz | Validação |
|---|---|---|
| Foto 1× com luz média (ISO ≥ 200: interior, fim de tarde, selfie dentro de casa) | 4 quadros iguais alinhados e somados, como o HDR+ do Pixel: menos ruído (≈ √4) sem suavizar textura. Movimento: rejeição por pixel ou volta ao quadro de referência. Luz boa (ISO < 200) continua com um quadro | Testes (escolha do caminho); ganho real: Aparelho |
| Perfil **Contraste** (Ajustes › Foto Nova, desligado) | Preto firme e contraste local depois do tom automático, na direção medida em vivo X300 Pro, iPhone 18 Pro e Pixel 10 Pro. Em cena escura o preto firme não entra. Não vale para Noturno nem Food | Testes; medido em 3 fotos reais do dossiê: preto p2 7,4→4,3 (quarto), 19,1→12,4 (perna), 16,8→10,3 (planta), sem aumento de estouro. Contraste local subiu pouco (+0,4 a +0,9) |

**Ainda não feito:** tratamento separado de céu e pele (item 3) e super-resolução da tele (item 4: aguardando as rajadas coletadas na 0.16.2).

## 0.16.2 — Coleta de rajadas para a super-resolução da tele

Opção **Ajustes › Diagnóstico e privacidade › Guardar quadros das rajadas (testes)**, desligada por padrão:
- Cada rajada (tele, Noturno, HDR) salva também os quadros originais em `Pictures/Nova Camera/Rajadas/<tipo>_<data>/`.
- Com a opção ligada, a tele (3× ou mais) sempre faz rajada de **8 quadros**, mesmo com luz boa.
- No **Pro com RAW**, cada disparo vira uma rajada de **6 pares DNG + JPEG**.

Objetivo: medir fora do aparelho quanto a super-resolução ganha sobre JPEG e sobre RAW antes de levar ao app.
Nada sai do aparelho: os arquivos ficam na galeria e só são compartilhados manualmente.

## 0.16.1 — Causa dos fechamentos no Diagnóstico; vídeo sai do ciclo de "prévia parada"

| Item | O que faz | Validação |
|---|---|---|
| Erros que fecharam o app | O Android só registra "crash", sem a causa. Agora a pilha do erro fica guardada no aparelho e aparece no Diagnóstico ("Erros que fecharam o app") | Testes (formato) |
| Vídeo com prévia parada | Segunda parada em 1 min: 60 fps sai nesta sessão (30 fps); se ainda parar, a estabilização sai. Antes reabria em ciclo a cada 3 s | Compilação; Aparelho |
| Auxílios do visor | Falha no histograma/estouro/realce/nível não fecha o app: o auxílio some e o erro vai para o Diagnóstico | Compilação |
| Vídeo Pro fechando o app | **Causa ainda não identificada** (sem pilha na 0.16.0). A 0.16.1 registra o erro na próxima ocorrência | — |

## 0.16.0 — Vídeo da tele no escuro, Noturno apoiado e auxílios de câmera profissional

| Item | O que faz | Validação |
|---|---|---|
| Quadros automáticos com pouca luz (vídeo) | A 30 fps, o vídeo usa a faixa 15–30 fps quando a câmera oferece: no escuro o quadro fica mais longo em vez de escurecer (tele 5× e 10× escura). Com luz boa continua a 30 fps. Ajuste em Vídeo; se a câmera recusar, volta a 30 fps fixos e avisa | Testes (escolha da faixa); resultado: Aparelho |
| Noturno apoiado | Com o giroscópio quase parado: quadros de até 1 s (antes 0,5 s) e até 4 s no total (antes 2 s). Na mão, nada muda | Testes |
| Nível do horizonte | Linha no centro; verde quando reto, em retrato ou paisagem. Desligado por padrão (Ajustes) | Testes (cálculo); desenho: Aparelho |
| Histograma e alerta de estouro no Pro/Vídeo Pro | Medidos na prévia exibida, 2 vezes por segundo, sem fluxo extra da câmera | Testes (análise); desempenho: Aparelho |
| Realce de foco | Contorno verde do que está nítido, só com foco manual no Pro | Testes (análise) |

**Não feito nesta versão:** RAW (aguardando exemplo: o DNG ou o JPEG que fica granulado); RAW em vídeo não existe no app.

## 0.15.1 — Correções a partir das fotos reais (planta 3× HDR, frasco 3×/5×, AirPods)

| Item | Antes | Agora | Validação |
|---|---|---|---|
| HDR: grade/fios brancos estourados | Pixels com a referência estourada eram decididos pelo mapa de confiança em meia resolução; onde ele zerava, entrava a previsão a partir da própria referência estourada → branco chapado com degraus | Referência estourada (ou preta): decide o próprio pixel do quadro de outra exposição — aceito se ele também está no limite; fantasma (algo escuro que passou) continua rejeitado | Testes (`clippedReferenceTrustsDarkFrameThatIsAlsoBright`); efeito visual: Aparelho |
| Tele multiquadro | Só a partir de 4,5× (frasco 3× com ISO 2218 saiu num quadro) | A partir de 2,5× (3×, 5×, 10×), ISO ≥ 250 | Testes |
| Tele com HDR desligado | Foto comum num quadro (frasco 5× ISO 2056) | Com ajuste automático ligado, a tele junta 3 quadros de mesma exposição; nunca HDR nem Noturno com HDR desligado | Testes |
| Retrato: brilho em volta de objeto claro em fundo escuro | A borda suave da máscara (α < 1) entrava no desfoque do fundo | O desfoque do fundo usa a máscara alargada ~3 px; a composição continua com a original | Testes (`brightObjectSoftEdgeDoesNotGlowIntoDarkBackground`); só na foto, a prévia ao vivo não muda |
| HDR com ISO alto e movimento (pano, ISO 3008) | Recua para o quadro de referência, sem fusão | **Sem mudança**: os quadros do bracket têm exposições diferentes; somá-los com movimento é o que gera fantasma | — |

## 0.15.0 — Retrato: o toque escolhe o assunto; detalhe na tele 3× com pele protegida

Levantamento do pedido (o que já existia × o que faltava):
- Já existiam: toque = foco + medição no ponto; tom adaptativo com proteção contra piora (por
  foto, com metadados); HDR/Noturno/tele multiquadro automáticos; Retrato com pessoa + profundidade;
  desfoque ao vivo; processamento pesado depois do disparo; modelos no APK; controle térmico/bateria.
- Faltava e entrou: (1) no Retrato, o toque também define o **assunto do desfoque** — fora da pessoa,
  vale o objeto tocado (faixa de profundidade na mediana 5×5 do ponto, conectada a ele); na
  pessoa, a pessoa. Só com o aparelho em pé (mesma orientação da prévia); frontal sem espelho
  inverte x. (2) Detalhe fino também na 3× (0,3) e realce reduzido a 30% em tons de pele (poros não
  viram lixa; barba/cabelo/olhos recebem o realce inteiro).
- Testado e **não ativado**: detalhe na principal (1×) — selfie e planta 12 MP: 1,7% dos pixels
  mudam, diferença quase invisível por ~1 s de processamento.
- Não feito: classificação de cena (pessoa/planta/comida/paisagem) com parâmetros por categoria — o
  tom já é decidido por foto pelos números da própria cena; categorias mudariam o "gosto" sem
  referência pareada para calibrar.
- Detalhe da tele também no HDR e no Retrato (cacto 5× real saiu pelo HDR, sem o realce).
- Validação: planta com toque no centro (igual à 0.14) e no canto (parede/piso nítidos, planta
  desfocada). Testes: `DepthTapTest`, `DetailTest` (pele).

## 0.14.5 — ZSL desligado; vigia sem ciclo

- Diagnóstico 0.14.3 (S24 Ultra, Android 17): 10 paradas da prévia na Foto — frontal ("lente 1")
  e no zoom 3×/5× — todas com ZSL + efeito da prévia; o vigia reabria a cada 3 s (ciclo). Foto da
  selfie em pouca luz (ISO 2090, 1/24 s, "um quadro"): pele encerada = redução de ruído rápida do
  quadro ZSL + tremido.
- ZSL desligado em todos os modos: "Disparo instantâneo" passa a usar latência mínima (o aparelho
  processa a foto como foto, não como quadro da prévia).
- Vigia: 2ª parada em 1 min desliga Tom/Temperatura ao vivo nesta sessão (sem efeito na prévia);
  3ª parada não reabre mais (pede para fechar e abrir).

## 0.14.4 — Noturno contra a luz e assinatura nos modos do fabricante

- Foto real (frasco, 3×, ISO 2194, contra a luz): parede esbranquiçada e armário escuro magenta
  (R 35, G 20, B 34). Causas: (1) o clareamento de meios-tons da 0.12 subia a cena mesmo com as
  partes claras perto do branco; (2) o preto firme subtraía o mesmo valor de R, G e B, o que
  aumenta a saturação das sombras.
- Clareamento de meios-tons limitado pela folga das altas luzes (teto log2(0,9/p95)): contra a luz
  quase não clareia; a cena escura do quarto (p95 0,55) continua igual à 0.12.
- Preto firme do Noturno agora pela luminância (ganho igual nos 3 canais): matiz e saturação das
  sombras preservadas. O véu da Foto continua como era.
- Retrato/Noturno do fabricante (Samsung): a foto recebe a assinatura Nova Camera (sem ajuste de
  tom nem estilo; a imagem do aparelho fica como veio).
- Testes: contra a luz não clareia; preto firme mantém R/G.

## 0.14.3 — causa do travamento do Retrato: ZSL + análise ao vivo

- Diagnóstico real (0.14.1, SM-S928B, Android 17): sessão RETRATO na câmera 0, lente física 5
  (1×), **58 quadros e parou** — antes de qualquer zoom. O travamento é do Retrato, não do 3×.
- No Retrato estavam ligados ao mesmo tempo: prévia (com efeito), foto, análise RGBA do desfoque ao
  vivo e o fluxo de reprocessamento do ZSL. Correção: sem ZSL quando a análise ao vivo está ligada
  (o Retrato usa o disparo normal, como antes da 0.9.2). O vigia da 0.14.2 continua como rede.

## 0.14.2 — Retrato em 3× ainda travava: vigia da prévia e registro

- A 0.14.1 (câmera lógica no Retrato) não resolveu: a causa não é a lente física fixa.
- Vigia em thread própria: câmera aberta sem resultados de captura por 3 s (ou 3× a exposição,
  em exposições longas) → registra modo, lente, zoom, efeito e análise ativos
  (`StallLog`, guardado entre aberturas) e reabre a câmera no mesmo zoom. No Retrato, reabre sem a
  análise do desfoque ao vivo nesta sessão (a foto continua com desfoque).
- Diagnóstico: "Prévia parada" e "Últimos encerramentos do app" (API 30+; em travamento/ANR, as
  primeiras linhas da pilha da thread principal).

## 0.14.1 — Retrato em 3× congelava

- Relato: no Retrato, ao tocar em 3×, a prévia trava. Causa provável: com luz suficiente a Nova
  fixava a lente física da tele; no Retrato são 3 fluxos (prévia com efeito, foto e análise do
  desfoque) e, numa lente física, o firmware pode não montar a sessão sem acusar erro.
- Retrato passa a usar a câmera lógica (como o Vídeo): o aparelho troca para a tele no mesmo zoom.
- Vigia geral: lente física fixa que abre mas para de entregar quadros por 3 s vira rota com falha
  e a câmera reabre na lente automática, no mesmo zoom, com aviso.

## 0.14.0 — foco no escuro, detalhe na tele, prévia do Retrato econômica

- Noturno: antes da rajada, foco real no centro (AF disparado, espera até 1,2 s) quando não há
  ponto escolhido; todos os quadros com o mesmo foco; AF volta ao contínuo depois.
- Tele 5×/10× na Foto (`Detail`): realce da luminância na escala fina (σ ≈ 1 px), com portão de
  ruído (abaixo de ~2σ não realça) e preso ao mínimo/máximo 3×3 (sem halo). Antes do tom.
  Validado nas fotos 10× do dossiê (aspirador, cimento, parede): letras e texturas mais nítidas,
  sem halo nem grão a mais; ~0,3–0,4 s por foto de 1,7 MP no host.
- Prévia do Retrato: com a cena parada (máscara igual por 10 quadros), segmentação 10 → 3 Hz e
  profundidade 0,9 → 2,5 s; qualquer movimento volta ao normal no quadro seguinte.
- Testado e **descartado** (piorou fotos reais da planta): alfa por cor local na borda (serrilhou o
  contorno) e abertura de frestas por cor (furou folhas claras). O Retrato segue como na 0.13.
- Não feito nesta versão: NPU (LiteRT + acelerador Qualcomm exige converter NAFNet/profundidade
  para LiteRT e validar a saída no aparelho) e trava de zoom no vídeo (o CameraX aceita um efeito
  por saída de vídeo, já usado pela assinatura; a estabilização do sistema já está no modo mais
  forte).
- Testes: `DetailTest`.

## 0.13.1 — Noturno da 0.12 de volta

- Pedido do usuário: manter o Noturno como na 0.12 (clareamento até mediana ~0,42 e +0,5 EV na
  captura). A reversão da 0.13.0 foi desfeita; o restante da 0.13 (tele multiquadro, EnergyLog,
  IA com bateria baixa) continua.

## 0.13.0 — Noturno natural, tele multiquadro, energia medida

Auditoria completa em `docs/AUDITORIA-0.13.md`.

- Noturno: retirado o clareamento da 0.12 (pedido: sem aspecto esbranquiçado). Volta o tom
  calibrado da 0.11; ficam os quadros longos com ISO baixo **com a mesma luz do automático**
  (`BOOST_EV = 0`) e o preto firme pelo canal escuro.
- Foto na tele (≥ 4,5×) com ISO ≥ 250: 3 quadros alinhados, referência = o mais nítido, fusão
  robusta (`JobKind.ZOOM`), tom da Foto. Calor/economia: um quadro, como antes.
- `EnergyLog`: a cada 10 s com a câmera aberta, corrente, tensão, temperatura da bateria, estado
  e folga térmica por atividade (modo, gravando, processando). Diagnóstico mostra mW médio,
  %/h na bateria e temperatura.
- IA opcional (NAFNet) espera com bateria ≤ 15% fora do carregador.
- Testes: `EfficiencyTest`, `NightExposureTest` (mesma luz do automático).

## 0.12.0 — Noturno: quadros longos com ISO baixo, foto mais clara e preto firme

Motivo: foto real do quarto no Noturno 0.11 saiu com ISO 6539 a 1/24 s (o automático prioriza
tempo curto), mediana 65/255, ruído ~6× o de uma área lisa diurna e preto em ~23. O Noturno da
Samsung entrega a mesma cena bem mais clara.

- `NightExposure`: a Nova escolhe a exposição de cada quadro do Noturno. Mesma luz do automático
  +0,5 EV, redistribuída em tempo longo e ISO baixo, até o limite de tremido estimado pelo
  giroscópio (`MotionMeter`, RMS dos últimos 0,5 s), pela ampliação e pela OIS. Na mão (~0,02 rad/s)
  ≈ 1/6 s; apoiado até 0,5 s. No caso real: 1/24 s ISO 6539 → ~1/6 s ISO ~2500 (≈2,6× menos
  ganho por quadro, antes da fusão). Cena clara (ISO < 400) ou automático já perto do limite: segue
  como antes. Aquecimento continua limitando os quadros. Aviso "Segure firme · X s" quando a rajada
  passa de 0,6 s. Só no modo Noturno (o noturno automático da Foto não muda).
- Tom do Noturno: alvo da mediana 0,42 (antes 0,28) por clareamento dos meios-tons que zera nas
  altas luzes e não toca pretos profundos; sombras recebem só o que faltar; preto ancorado pelo
  canal escuro. Na foto real: mediana 65 → 95, p1 23 → 27, p99 inalterado.
- EXIF "Software" passa a trazer a versão real (estava fixo em "Nova Camera 0.9").
- Testes: `NightExposureTest`; `ImagingCoreTest` atualizado para o novo alvo.
- No aparelho: Noturno na mão e apoiado (o aviso de tempo muda), cena com janela clara (altas luzes
  não podem estourar mais que antes), tele 3×/5× no escuro (tempo menor), comparação com o Samsung.

## 0.11.1 — zoom e régua sem disputar espaço

- No 4:3/1:1 a barra de zoom fica na faixa entre o visor e os controles (não cobre mais a imagem).
  A régua dos controles ao vivo abre nessa mesma faixa; enquanto aberta, a barra de zoom some e a
  pinça continua funcionando. Em 16:9 e tela cheia o zoom segue dentro do visor e sobe junto com o
  painel quando a régua abre.
- Faixa de chips (Desfoque/Exposição/Tom/Temperatura) rola na horizontal quando não cabe (fonte
  grande do sistema); centralizada quando cabe.
- Validação: render da interface em 1080×2340 com densidade 420 dpi e barras do sistema simuladas
  (Foto/Retrato/Tele, régua aberta e fechada). No aparelho: abrir e fechar a régua em 4:3 e 16:9,
  fonte do sistema no máximo.

## 0.11.0 — controles criativos ao vivo (prévia = foto)

| Item | O que foi feito | Validação | No aparelho |
|---|---|---|---|
| Faixa de controles ao vivo | Acima dos modos: **Desfoque** (Retrato), **Exposição**, **Tom**, **Temperatura**. Toque abre a régua, outro toque fecha, toque longo volta ao neutro; o chip mostra o valor. Vale em qualquer lente (0,6x a 10x) nos modos Foto, Retrato, Noturno e Food do motor Nova | Compilação/lint | Fluidez e leitura dos chips |
| Tom e temperatura | `PhotoStyle`: a **mesma conta** no shader da prévia e em toda foto gravada (Foto comum, alta resolução em faixas, Noturno, HDR, Retrato, Food, reedição e refino da IA), sempre depois do restante do processamento — a mesma ordem da prévia. Tom: curva monótona na luminância (matiz preservado); temperatura: ganhos de balanço com brilho mantido. A descrição da foto registra o estilo | `PhotoStyleTest` (5): neutro = identidade, curva monótona, cinza continua cinza, temperatura mantém o brilho | Prévia × foto lado a lado |
| Extensões do fabricante | Não aceitam efeitos de prévia: nelas a faixa mostra só Exposição e a foto não recebe estilo (nada de prévia diferente da foto) | Compilação | — |
| Zoom no vídeo | Suavização do zoom que segue o dedo cresce com a ampliação (110 ms em 1x, ~200 ms em 10x): o tremor do dedo não vira salto de enquadramento na tele | `ZoomFollowSmoothingTest` | Gravar com zoom em 5x/10x |
| Textos | Mensagens técnicas reescritas (alta resolução recusada, lente direta, códigos de erro), "Em desenvolvimento" removido, licenças completas (Depth Anything V2 Small, libjpeg-turbo) | — | — |

Já existentes e mantidos: estabilização da prévia/vídeo pela API pública (`setPreviewStabilizationEnabled` — mesmo EIS na tela e no arquivo — ou do vídeo) e OIS explícita; transição de lente/câmera com imagem congelada até o primeiro quadro real.

Noturno, HDR e selfie **comparados às referências**: as 10 referências são de outras cenas e aparelhos; comparação útil exige pares Nova × Samsung da mesma cena. Sem esses pares, mudar a curva do Noturno ou da pele seria ajuste no escuro.

## 0.10.0 — desfoque ao vivo no Retrato, assinatura com dados da captura, zoom confiável

| Item | O que foi feito | Validação | No aparelho |
|---|---|---|---|
| Desfoque ao vivo no Retrato | Efeito OpenGL ES 2.0 na pré-visualização (CameraX `CameraEffect`): quadro → cópia 1/4 → desfoque gaussiano separável → composição com a máscara. Máscara dos **mesmos modelos** da foto final (MediaPipe multiclasse + Depth Anything V2 Small + `DepthPortrait.plan`) a partir de uma análise 640×480: pessoa até 10 Hz, profundidade ~1 Hz. A orientação textura × análise é **medida** na abertura (correlação nas quatro orientações), não presumida | `PreviewMaskCalibrationTest` (4), compilação, lint. **O efeito GL não roda no ambiente de testes** | Se a prévia desfoca o fundo (e não o assunto); fluidez; calor; diferença prévia × foto |
| Régua de intensidade | Retrato: "Sem desfoque" … "Desfoque 100%" acima dos modos; muda a prévia na hora e a foto usa o mesmo valor (mesma escala de raio) | Compilação | Coerência prévia × foto |
| Proteções | Erro de GL ⇒ efeito desligado e prévia normal (sem tela preta), aviso único; aquecimento moderado reduz a taxa e desliga a profundidade ao vivo; severo pausa o desfoque ao vivo com aviso; profundidade ao vivo pausa enquanto uma foto é processada; opção "Prévia do desfoque no Retrato" | Compilação | — |
| Zoom ao reabrir | O zoom pedido logo após vincular podia ser recusado (câmera ainda fechada) — a barra mostrava 10x com a imagem em 5x. Agora é reaplicado quando a câmera abre; se o efetivo divergir, a barra mostra o efetivo e o diagnóstico registra | Compilação | Reabrir o app em 10x/5x/0,6x |
| Assinatura com dados da captura | Opcional: distância focal equivalente, abertura, obturador e ISO lidos do EXIF da própria foto; campo ausente não aparece; nunca atribui a foto a outro aparelho | `CaptureInfoTest` (3) | Conferir os valores com o EXIF na galeria |
| Retrato: borda das folhas | Borda firme 1–2 px para fora (sem o fio claro do fundo desfocado na beira das folhas) | `PortraitEdgeTest`: franja 0,23%, pessoa perdida 0,12%; foto da planta (local) | — |
| Custo por foto | Diagnóstico: duração mediana/máxima por modo, variação de temperatura da bateria e estado térmico máximo | `CostLogTest` | Enviar a linha "Custo do processamento" depois de usar |

Limites desta versão: a prévia ao vivo é uma aproximação em resolução reduzida (borda mais macia que a foto; o mapa de profundidade pode estar até ~1 s atrás quando a câmera se move). Desfoque computacional não é abertura óptica: a régua não muda a abertura da lente.

**Fica para as próximas fases** (precisam de medição no aparelho antes de mexer): estabilização de vídeo com zoom, transições entre lentes e câmera frontal, Noturno/HDR/selfie comparados às referências, polimento de interface/tipografia, controles ao vivo nas teles (3x/5x/10x) além de EV e Pro já existentes.

## 0.9.6 — Retrato: o assunto é o objeto inteiro

Teste do usuário (planta, sem pessoa) na 0.9.5: só uma fatia da planta ficou nítida, o resto da própria planta desfocou com corte duro no meio das folhas, e o desfoque máximo estava forte. Reproduzido localmente com o **mesmo modelo do APK** (extraído da release nova-test-28; sha256 do APK confere) sobre a foto do usuário: a profundidade estava correta (planta clara, separada de piso e parede); o erro era a regra do assunto — uma faixa fina em torno de um ponto de foco.

| Mudança | Antes | Agora | Validação |
|---|---|---|---|
| Assunto sem pessoa | Faixa de ±4–12% da profundidade em torno do ponto mais perto do centro | Objeto inteiro: perto × longe (Otsu); se o lado "perto" ligado ao centro passar de 45% do quadro (chão/parede junto), divide em três faixas e fica com a do centro; só regiões **conectadas** ao centro | Foto da planta: planta inteira nítida, parede, piso e portão desfocados (local). `objectIsWholeAndFloorIsNotSubject` |
| Assunto com pessoa | Mediana da pessoa ± 4% | Faixa da pessoa (percentis 5–95 ± 4%) conectada a ela: o que ela segura na mesma faixa entra na máscara (borda firme pela cor) | Foto do controle: sem regressão (local). `personKeepsHeldObjectAtSameDepth` |
| Desfoque | Dentro de uma faixa fina, nítido; fora, crescia rápido | Zero no assunto; o resto, no mínimo 30% e crescendo com a distância até a faixa do assunto; transições suavizadas | Local |
| Intensidade máxima | raio 6 + 24×força | raio 5 + 18×força (≈ 25% menor no padrão) | — |

## 0.9.5 — Retrato com profundidade estimada (Depth Anything V2 Small)

| Item | O que foi feito | Validação | No aparelho |
|---|---|---|---|
| Modelo | Depth Anything V2 **Small** (Apache-2.0) convertido para ncnn no CI (`tools/depth/export_ncnn.py`): entrada 392×392, ~48 MB em fp16, ~58 GFLOPs por foto (uma inferência). O CI confere a licença no model card, compara ncnn × PyTorch (correlação ≥ 0,995, erro ≤ 3% da faixa) e testa sanidade (piso em perspectiva: baixo mais perto que cima). Falhou ⇒ APK sem o modelo, Retrato como na 0.9.4 | Pesos oficiais no CI (build 28, sha256 3152477c…, fixado no workflow): ncnn × PyTorch correlação 0,99998, erro 1,66% da faixa; piso em perspectiva coerente (baixo 6,89 × cima 0,03). Caminho de conversão também validado localmente com pesos aleatórios | Tempo (linha "Profundidade" no diagnóstico), memória, temperatura |
| Desfoque pela profundidade | Foco = mediana da profundidade dentro da pessoa. Desfoque cresce com a distância ao plano de foco (zona nítida de 4% da faixa da cena, máximo a 39%); o que está à frente também desfoca; o que está no foco fica nítido mesmo fora da máscara (objeto na mão, chão sob os pés). A borda continua definida pela máscara por cor; o fundo colado no contorno herda o desfoque do vizinho (sem franja) | `DepthPortraitTest` (4), `depthKeepsInFocusObjectSharpOutsidePersonMask` | Qualidade da profundidade em cenas reais (a validação local não tem os pesos reais) |
| Retrato de objetos | Sem pessoa detectada, o assunto é o que está mais perto no centro do quadro; a máscara sai do plano de foco, refinada pela cor | `DepthPortraitTest` | Objetos, animais, comida |

Limites: profundidade relativa (sem escala métrica) e de baixa resolução (392 px); erros do modelo aparecem como diferenças de desfoque, não como recortes, porque a borda nítida vem da máscara. Superfícies espelhadas/vidro e céu podem ter profundidade errada.

## 0.9.4 — Retrato: borda justa e fundo com o grão da foto

Teste do usuário (controle remoto na mão sobre tapete bege) mostrou franja de tapete nítido em volta do braço e brilho na borda do polegar. Reproduzido localmente com o mesmo modelo de segmentação sobre a foto: a máscara passava 20–40 px além da borda real.

| Problema | Causa | Correção | Medição |
|---|---|---|---|
| Franja de fundo nítido em volta de braço, mão e objetos | Refinamento da máscara guiado só pela luminância (pele e tapete bege têm o mesmo brilho) e transição puxada para a pessoa em todo o contorno | Filtro guiado **em cor** (coeficientes em 1/4, aplicação em 1/2 da resolução), em duas passadas; transição firme fora do cabelo; larga só onde o modelo vê cabelo | Cena sintética de mesmo brilho: franja 9,8% → 0,05% da faixa de 40 px em volta; pessoa perdida 0,2% (`PortraitEdgeTest`). Foto do usuário: transição colada na borda (comparação local) |
| Objetos na mão (controle) | O modelo já marca como "outros"; antes só entravam pela proteção larga | Tudo o que não é fundo fica nítido com borda firme | Foto do usuário: controle inteiro na máscara |
| Fundo liso demais ("recorte colado") | O desfoque apaga o ruído do sensor | O ruído medido na própria foto (laplaciano, blocos mais lisos) volta ao fundo desfocado; foto limpa = sem grão | `PortraitGrainTest` |
| Cabelo e barba | — | Sem regressão na selfie do dossiê (comparação local) | — |

A suavidade no topo do controle na foto do usuário não vem da máscara (o controle inteiro estava nítido na máscara): é desfoque óptico (topo inclinado, mais longe do foco) e/ou redução de ruído. Limite que continua: o modelo de segmentação é de pessoas; objetos sozinhos (sem a mão) ou animais não são separados — o próximo passo é profundidade estimada (Depth Anything V2 Small, Apache-2.0).

## 0.9.3 — disparo instantâneo medido e fotos Ultra HDR

| Item | O que foi feito | Validação | No aparelho |
|---|---|---|---|
| Atraso do disparo medido | Diagnóstico registra, por modo de captura: toque→início da captura, toque→exposição do sensor (carimbo do quadro; negativo = quadro anterior ao toque) e toque→imagem, mediana das últimas 20 fotos | `ShutterLogTest` | Números reais com e sem o disparo instantâneo |
| Disparo instantâneo (ZSL) | Foto de um quadro usa o modo ZSL do CameraX quando a câmera anuncia suporte (`isZslSupported`); rajadas (Noturno/HDR), RAW e extensões do fabricante ficam como estavam. Opção "Disparo instantâneo" (ligada) para comparar | Compilação/lint. O CameraX cai para o modo normal se o ZSL não puder ser usado (ex.: flash) | Se o S24 anuncia ZSL; atraso medido; nitidez e ruído comparados com o modo "qualidade máxima"; nos brackets de HDR com ZSL, se cada quadro tem a exposição pedida (o HDR mede o ganho real de cada quadro e não depende disso) |
| Ultra HDR próprio | No HDR (incluindo o HDR escolhido pelo modo Adaptativo), o quadro mais escuro do bracket mede o realce que estourou na referência. A foto final continua SDR e ganha um gainmap (Android 14+): até 2 stops acima do branco, só onde a cena passou do branco; meios-tons e sombras sem ganho; um objeto que se moveu nunca escurece a cena (só aumenta). Vale também na reedição | `HdrGainTest` (6), `UltraHdrAttachTest` (2), `hdrFusionReportsSceneAboveSdrWhite`, `UltraHdrExifTest` (o EXIF gravado depois preserva XMP, índice MPF e o gainmap) | **A codificação Ultra HDR em si não roda no ambiente de testes**: confirmar na galeria do S24 que a foto HDR mostra realces mais claros na tela HDR, e que telas/apps SDR mostram a mesma foto de antes |
| Ultra HDR do fabricante | Só diagnóstico: se a câmera anuncia JPEG_R a apps. Não foi ligado: não há como confirmar aqui se combina com ZSL sem impedir a câmera de abrir | Compilação | Linha "Ultra HDR do aparelho" no diagnóstico |

Limites: a curva de tom da Samsung é aproximada pela sRGB ao medir a cena; Noturno, Retrato e FOTO de um quadro não têm faixa extra medida e não recebem gainmap (não se inventa brilho).

## 0.9.2 — IA de redução de ruído em segundo plano

Antes, a IA (NAFNet) rodava antes de salvar, com limite de 6 s. Uma foto de 12 MP tem 192 blocos e o custo estimado na GPU é de dezenas de ms por bloco, então a IA era quase sempre descartada (o log do aparelho mostrou descarte logo no início).

| Item | O que foi feito | Validação | No aparelho |
|---|---|---|---|
| IA depois da foto pronta | Noturno/Retrato salvam a foto Nova na hora. Com a fila de fotos vazia, a base é limpa pela rede (limite 60 s), recebe o mesmo acabamento (tom, desfoque, assinatura) e **substitui a mesma entrada da galeria** — continua uma foto só | Compilação, lint, teste do orçamento (`backgroundBudgetFitsFullPhotoThatForegroundCannot`). O fluxo completo **não** roda no ambiente de testes (sem GPU/ncnn) | Tempo real por foto, temperatura, se a troca aparece na galeria |
| Prioridade da câmera | Uma foto nova cancela a IA em andamento; ela recomeça depois. Aquecimento (≥ moderado) ou economia de bateria adiam | Compilação | Disparos seguidos durante a IA |
| Segurança da troca | A foto é comprimida antes de abrir a entrada da galeria (janela de arquivo vazio mínima); se a rede já rodou e foi interrompida, só o acabamento é refeito, sem aplicar a IA duas vezes | Compilação | — |
| Sessões | Fotos esperando a IA não são apagadas pela limpeza das quatro sessões de revisão (teto de oito) | Compilação | — |

Limites: a IA continua **desligada por padrão** (Configurações › Avançado) até haver medição de tempo e temperatura no aparelho. Se o app for fechado à força, as fotos pendentes ficam sem IA (a versão Nova já está salva).

## 0.9.1 — revisão do trabalho 0.8.1–0.9 sobre fotos reais

O ciclo 0.8.1–0.9 (captura nativa em alta resolução, lente física, imagem adaptativa) foi validado só com cenas sintéticas. Rodando a foto real HDR da cadeira (dossiê, só no ambiente local) apareceram dois retrocessos na fusão, corrigidos aqui:

| Problema | Causa | Correção | Medição |
|---|---|---|---|
| Quadrados de tom diferente nos blocos da borda | Bloco com qualquer pixel fora do quadro era descartado inteiro (só a referência), enquanto os vizinhos recebiam a fusão | Só os pixels sem dado (fora do quadro) usam a referência; o resto do bloco continua fundido, sem deslocar o bloco | Salto na divisa dos blocos ÷ salto vizinho: 0.8 1,21 · 0.9 2,19 · 0.9.1 1,20. Teste `borderTilesDoNotLeaveToneSquares` (falha na 0.9) |
| Buraco de tom no formato do objeto rejeitado | Pixel rejeitado só perdia o peso, deixando a referência sozinha | Volta a substituição da 0.7 pela previsão da referência na exposição do quadro | Cena sintética: 2,7 → 1,8 níveis (de 255) |

"Guardar originais" segue desligado por padrão (uma única imagem final na galeria, por decisão do usuário); o original fica só nas quatro últimas sessões privadas.

A captura nativa, a lente física e a imagem adaptativa da 0.9 continuam **sem teste no aparelho**.

## 0.8 — tecnologias open-source integradas

| Item | O que foi feito | Validação | No aparelho |
|---|---|---|---|
| Retrato com cabelo separado | MediaPipe Tasks + modelo `selfie_multiclass_256x256` (Apache-2.0) no lugar do ML Kit. Cabelo e acessórios (óculos) entram na máscara protegida, refinada nas bordas reais da foto | Compilação. **O modelo não roda na JVM**: qualidade de contorno só no aparelho | Selfie com cabelo solto, barba e óculos; traseira 1×/3× |
| Alinhamento subpixel | Fração de pixel medida por bloco em resolução cheia (parábola sobre erro quadrático) e interpolação Catmull-Rom | Rajada real do dossiê (aspirador, mão livre): erro nas bordas **4,28 → 4,00**, erro total 3,44 → 3,14. Teste: cada quadro alinhado fica mais perto da cena (≥ 10%). **Ressalva**: numa cena sintética de bordas duras e serrilhadas o resultado final empatou/perdeu — a interpolação suaviza levemente cada quadro | Noturno parado, comparar nitidez com a 0.7 |
| Assinatura no vídeo | `OverlayEffect` do CameraX desenha a assinatura em cada quadro gravado (não na prévia), já na orientação de quem vê | Teste da geometria (vídeo girado 90° e recorte) | Gravar em pé e deitado; conferir posição. **Não aplicada no vídeo frontal espelhado** (o texto poderia sair invertido) |
| Redução de ruído por IA (experimental, desligada) | ncnn 20240820 (BSD-3) com Vulkan via JNI; NAFNet-SIDD-width32 (MIT) em blocos 288×288 com margem; mistura 75% com o original; Noturno e Retrato | Conversão NAFNet → ncnn validada localmente com pesos aleatórios: diferença ncnn × PyTorch **0,00005**; o runtime 20240820 do app carrega e executa o modelo. **Pesos oficiais** (Google Drive) baixados e convertidos no CI; verificação no build `nova-test-10`: ncnn × PyTorch **80,9 dB**, ganho de redução de ruído numa cena de teste **+3,35 dB (ncnn) × +3,34 dB (PyTorch)** — a conversão não perde qualidade. A qualidade em fotos reais e o tempo no aparelho continuam **sem medição** | Custo ≈ 42 GFLOPs por bloco ⇒ ~7,6 TFLOPs numa foto de 12 MP: **vários segundos na GPU, muito mais na CPU**. Medir tempo, temperatura e se o resultado plastifica |

Tamanho do APK: + ~16 MB (modelo de segmentação) + ~10 MB (ncnn) + ~58 MB se o modelo NAFNet for incluído.

## 0.7 — correções dos defeitos comprovados no pacote de testes da 0.6

Material analisado localmente (não versionado): 16 fotos (JPEG/HEIC, reduzidas e sem EXIF) e 2 vídeos. **Nada foi testado em aparelho.** A validação usou as próprias fotos do pacote e testes de regressão que reproduzem cada falha e **falham na 0.6**.

| Defeito (arquivo) | Causa medida | Correção | Evidência |
|---|---|---|---|
| Vassoura/rejunte duplicados (133987), cadeira com transparência (133771) | Cena parada com rejunte duplicado na imagem inteira ⇒ **falha de alinhamento**, não movimento. O antifantasma da 0.6 trabalhava numa grade ~5× reduzida (não via linhas finas) e comparava exposições por um ganho único (errado depois da curva de tom do JPEG). Onde rejeitava, a mistura perdia aquela exposição e ficava uma "sombra" no formato do objeto | Curva de exposição por casamento de histogramas; alinhamento em grade 12×16 (absorve rotação leve); **mapa de confiabilidade por pixel** em meia resolução + verificação em resolução cheia; pixel rejeitado é **substituído pela previsão da referência na exposição daquele quadro** (sem buraco de tom); quadro com > 22% de área inconsistente é descartado; sem quadro seguro ⇒ **a foto é o quadro de referência** | Fotos reais (133770) com exposições −2/+1 EV simuladas, deslocamento e rotação de mão e um objeto em movimento: 0.6 duplica o objeto; 0.7 não. Teste `hdrFusionNeverCreatesGhosts` falha na 0.6 (96 px do objeto duplicado) e passa na 0.7; `unsafeFusionFallsBackToReference` |
| Pretos acinzentados, imagem "chapada" (carvão 133765, cadeira 133771, aspirador 133772/3) | Canal escuro (p0,5 do menor RGB) 0,14–0,17 na Nova contra 0,05–0,10 na Samsung: a elevação de sombras da 0.6 levantava o próprio preto; a fusão HDR somava isso | Elevação de sombras protege pretos profundos e é bem menor; véu corrigido pelo canal escuro (o pano verde, 0,035, não é tocado); compressão de altas luzes menor | Carvão: p05 0,227 → **0,151** (Samsung 0,183), saturação 0,185 → 0,234. Aspirador 10×: p05 0,165 → 0,100, ruído medido 7,84 → 7,31. Cadeira: p05 0,211 → 0,157 |
| "Algumas fotos ficam piores" | Correção forte e uniforme | **Plano adaptativo com proteção contra piora**: o ajuste é testado numa prévia e só é aceito se não reduzir contraste nem textura, não levantar o preto e não estourar luzes; senão cai para 65%/35% ou a foto fica como capturada | Pano verde (3 versões), selfie e fotos Samsung: **sem ajuste**. Testes `AdaptiveToneTest` |
| Zoom 5×/10× com véu | — | Contraste local de média escala (forma, não textura fina) só com zoom ≥ 2,5× ou véu; sem nitidez artificial, sem reconstruir letras | Recorte 10× sem halo e sem ruído amplificado |
| Selfie sem acabamento (133782) | O Retrato da 0.6 ainda clareava sombras | Retrato: sem contraste local, elevação mínima; transições semitransparentes (cabelo, barba, armação) pendem para a pessoa (não desfocam); desfoque gradual mantido. **A diferença principal para a Samsung (nitidez/microtextura da frontal) vem do ISP da câmera, não do pós-processamento** — a foto do pacote é de outra cena, sem par direto | Teste: Retrato nunca recebe contraste local |
| UX confusa (133980) | Muitas opções; comparação aberta ao tocar a miniatura | FOTO: **processamento automático** (padrão) — analisa e, só se melhorar, substitui a foto da galeria pela pronta (original byte a byte em "Originais"); sem selo de progresso na FOTO; comparação Original × Nova **opcional: tocar e segurar a miniatura**; ajustes avançados em "Avançado" | Compilação + testes de interface |
| Multiquadro indiscriminado | — | Noturno só faz rajada com pouca luz real (ISO ≥ 500 ou exposição ≥ 1/25 s); HDR só captura o quadro escuro se a referência tem altas luzes estouradas e o claro se tem sombras profundas | `CaptureDecisionTest` |
| Vídeos 133776/133777 (1×→10×) | Ambos a ~60 fps: o brilho cai de ~0,52 para ~0,17 ao aproximar; em 133776 um degrau de 0,50 → 0,23 em 0,1 s (troca de lente). A 60 fps a exposição fica presa em 1/60 s e as teles têm abertura menor. **Não foi possível atribuir qual arquivo é de qual app** | Aviso no vídeo a 60 fps quando o sensor está no limite (ISO ≥ 1600 e exposição no teto): sugere 30 fps | Compilação |

**Assinatura Nova Camera** (nova): "NOVA CAMERA · Shot on Galaxy S24 Ultra" (nome comercial a partir do modelo real), desligada por padrão; posição, tamanho e transparência configuráveis; aplicada só na foto final, nunca no original. EXIF: fabricante, modelo, lente e exposição reais copiados do JPEG da câmera; `Software = Nova Camera 0.7`. Vídeo: em desenvolvimento.

**Open-source:** avaliação e licenças em [OPEN_SOURCE.md](OPEN_SOURCE.md). Nenhum código de terceiros novo nesta versão; GPL (PhotonCamera original, MotionCam) só como referência de algoritmo.

**Limitações que continuam:** alinhamento de pixel inteiro (sem subpixel); fusão sobre JPEG (não RAW); segmentação de uma classe (cabelo não separado); nenhuma medição de latência, memória, consumo ou temperatura no aparelho — itens 6–9 do roteiro abaixo.

### Roteiro de teste físico — 0.7 (Original × Nova × Samsung, mesma cena e luz)

1. **HDR Automático**: repetir a vassoura e a cadeira do pacote, segurando o aparelho na mão. Não pode haver duplicação. Se a revisão disser "usado o quadro de referência", a fusão foi recusada por segurança (esperado com muito movimento).
2. **FOTO automática**: carvão, aspirador 5×/10× e pano verde. Conferir que o pano verde **não muda** e que o carvão/aspirador ganham preto mais firme sem perder textura. Tocar e segurar a miniatura para comparar.
3. **Selfie/Retrato**: cabelo, barba, óculos; comparar com a Samsung na **mesma cena**.
4. **Noturno** com luz baixa e com luz normal (com luz normal deve dizer "um quadro, sem fusão").
5. **Vídeo 1×→10×** a 30 fps e a 60 fps em ambiente interno; observar o aviso a 60 fps.
6. Tempo do salvamento ao "pronto" em FOTO, HDR, Noturno e Retrato.
7. Memória: abrir Diagnóstico após 10 fotos seguidas de Noturno (o app não deve fechar).
8. Temperatura/consumo: 15 min de uso misto, com e sem carregador.
9. Assinatura: ligar, testar as 3 posições e conferir que o original em "Originais" não tem assinatura.

## 0.6 — motor próprio de processamento (dossiê consolidado)

Todo processamento roda **no aparelho**, sobre os JPEGs que a câmera entrega (o processamento proprietário da Samsung continua inacessível a apps de terceiros). Nada é inventado: cada pixel final é média ponderada de valores medidos ou o original com ganho de tom.

### Motor (app/src/main/java/com/novacamera/s24/core/imaging e processing)

| Componente | Como funciona | Validação |
|---|---|---|
| Alinhamento | Busca hierárquica global + campo por regiões (6×8), quadros de exposições diferentes normalizados pelo ganho. **Precisão de pixel inteiro** (sem subpixel) | Testes sintéticos + quadros reais do dossiê (vídeo 133608, mão livre): deslocamentos de até 18 px recuperados |
| Fusão robusta (Noturno) | Média ponderada por pixel: peso total até 2σ do ruído, cai suavemente até 4,5σ; o que se moveu fica com o quadro de referência (sem fantasma) | Testes + dossiê: 5 quadros reais com ruído de sensor adicionado → ruído **4,93 → 2,55**, erro vs. quadro limpo **6,16 → 3,59**, sem fantasma visível. Leve perda de nitidez pela precisão de pixel inteiro |
| Tom local | Base em log-luminância (filtro guiado); ganho igual em R, G, B ⇒ **matiz e saturação preservados**; correção de véu só quando a cena é "lavada" | Testes + dossiê (0.6): pano verde **inalterado**; aspirador 10× véu p05 0,43 → 0,29 (Samsung 0,31); cena noturna sombras 38,9% → 12,5% (Samsung 19,9%) |
| HDR (fusão de exposições) | Bracketing 0 / ≈−2 / ≈+1 EV pela compensação de EV; pesos de boa exposição e contraste refinados por filtro guiado (sem halo); **antifantasma** compara cada quadro com o previsto pela referência | Testes (halo, antifantasma). **Sem validação em fotos reais com bracketing** |
| Retrato Nova | Segmentação de pessoa ML Kit (modelo embutido, offline) → máscara refinada nas bordas reais (filtro guiado) → fundo desfocado em linear (gama 2,2), pré-multiplicado (sem halo), **mais forte longe da pessoa**. A pessoa é copiada **sem alteração** (sem suavizar pele/barba/cabelo) | Testes (pessoa intacta, sem halo, render da revisão). **Qualidade de borda (cabelo, óculos) só no aparelho**: o ML Kit não roda na JVM |
| Food | Vivacidade só em cores pouco saturadas + leve calor + tom FOOD | Testes |
| Execução | JPEGs mantidos em memória e decodificados **em faixas** (`BitmapRegionDecoder`): 5 quadros de 12 MP não ocupam 5 × 48 MB. Processamento em blocos de 256 px numa fila única de baixa prioridade | Teste Android (Robolectric): faixa = decodificação inteira; pipeline completo; cancelamento |

### O que mudou para quem usa

| Recurso | Comportamento | Validação | No aparelho |
|---|---|---|---|
| **Noturno Nova** (padrão) | 5 quadros em rajada (3 com economia de energia/aquecimento moderado; 1 com aquecimento severo) + exposição estendida quando o sensor permite; fusão + tom NIGHT. Funciona em **qualquer lente** (inclusive 3×/5×) e na frontal | Testes + dossiê | Cena escura parada e com pessoa se mexendo; comparar com a Noite Samsung |
| **HDR Nova** | Botão HDR na FOTO: Desligado → Nova → Fabricante (se houver) | Testes | Janela clara + interior; pessoa andando (fantasmas?) |
| **Retrato Nova** (padrão) | Desfoque ajustável depois da captura. Sem pessoa detectada (cobertura < 3%), salva sem desfoque e avisa. O Retrato da Samsung continua disponível em Configurações, avisando que suaviza a pele | Testes | Selfie com barba/cabelo solto/óculos; traseira 1× e 3× a 1–2 m |
| **Food** | Disponível (antes "em breve") | Testes | Prato sob luz quente |
| **Timelapse** | Quadros da análise (~1080p), 1 a cada 0,5/1/2/5 s (botão ⏱), H.264 a 30 fps, sem áudio, duração máxima configurável. Precisa do app aberto | Testes (regras) + Compilação. **Codificador não testado (não roda na JVM)** | 1 min a 1 s: vídeo de ~2 s, orientação correta |
| **Original preservado** | O original é gravado **antes** do processamento ("Pictures/Nova Camera/Originais", opção ligada por padrão). Cancelar, falta de memória ou aquecimento crítico nunca perdem a foto | Compilação | Cancelar no meio (✕ no selo de progresso) |
| **Progresso e cancelamento** | Selo "Noturno · Reduzindo ruído 46% ✕" sem bloquear o obturador (fila de até 3) | Teste de render | — |
| **Revisão** | Tocar na miniatura após uma foto processada: Original × Nova com divisor arrastável, intensidade e desfoque reaplicados a partir da base salva, "Salvar ajuste" regrava em resolução cheia | Teste de render com sessão sintética | Fluidez dos controles |
| **Aprimorar fotos (leve)** | Opcional (desligado): na FOTO grava uma cópia _aprimorada; o original fica intacto | Compilação | — |
| **Resolução por lente** | Botão de MP sempre visível: percorre as resoluções **que a câmera anuncia** (saídas normais + alta resolução, por proporção). Escolha guardada por câmera; o arquivo sai com a resolução mostrada (se o CameraX recuar, o app avisa). O que só existe no modo de resolução máxima do sensor aparece como **bloqueado pelo fabricante** | Testes (`PhotoSizesTest`) | Ver item 6 do roteiro |
| Térmico | Aquecimento severo: sem fusão multiquadro; crítico: só o original | Compilação | — |

### Sobre 12 / 50 / 200 MP (prioridade alta do dossiê)

A 0.5 escolhia "Máxima" de forma genérica e o botão só aparecia se a maior saída fosse 20% maior que a padrão, por isso na traseira não havia escolha. Agora as opções vêm das listas públicas do Camera2 de **cada câmera exposta** e o Diagnóstico mostra, por câmera e por lente física, "Resoluções oferecidas", "Bloqueadas" e o tamanho real do sensor.

Limite que não depende do app: se a Samsung não anunciar 50/200 MP em `getOutputSizes`/`getHighResolutionOutputSizes`, nenhum app de terceiros consegue gravá-los pelo CameraX. Se o Diagnóstico mostrar 200 MP **só** no modo de resolução máxima (`ULTRA_HIGH_RESOLUTION_SENSOR`), dá para implementar uma captura Camera2 dedicada — **ainda não implementada**. Lentes físicas que não são expostas como câmeras separadas (comum na tele 5×/50 MP) não têm resolução própria acessível; nesse caso o app mostra a resolução da câmera lógica. **Não há upscaling em nenhum caso.**

Os modos com fusão (Noturno, HDR Nova, Retrato, Food) usam a resolução padrão, por memória e tempo de processamento.

### Bloqueado pelo fabricante/API × não implementado

| Item | Situação |
|---|---|
| Processamento interno da Samsung (Nightography, IA de zoom, detalhes do Retrato) | **Bloqueado**: inacessível a terceiros |
| 200 MP / 50 MP fora das listas públicas | **Bloqueado** se o Diagnóstico não os anunciar; **não implementado** se só existirem no modo de resolução máxima |
| Panorâmica (captura guiada + costura) | **Não implementado** |
| Vídeo Retrato (segmentação a 30 fps) | **Não implementado** |
| Pós-processamento de vídeo em segundo plano | **Não implementado** |
| Super-resolução multiquadro 20×/30× | **Não implementado** (o alinhamento atual é de pixel inteiro; super-resolução exige subpixel) |
| Alinhamento subpixel e merge no domínio RAW | **Não implementado** (próximo passo de qualidade do Noturno) |

### Custos desta versão

- APK maior (~+25 MB): biblioteca nativa do ML Kit. Só arm64 é incluído (S24/S25 Ultra).
- O ML Kit declara permissão de internet para telemetria de uso; **ela é removida no manifesto** — o app continua sem internet.
- `largeHeap` ativado para a fusão de quadros.

## Roteiro de teste físico — 0.6

1. **Noturno**: cena escura parada (sala com luz baixa), segurando firme até o fim de "Segure firme · 4/5". Comparar com FOTO e com a Noite da Samsung: ruído, cor, sombras. Repetir com uma pessoa se mexendo (há fantasma?). Repetir na tele 3×.
2. **Retrato**: selfie com cabelo solto/barba/óculos; traseira 1× e 3× a 1–2 m. Tocar na miniatura: o contorno respeita cabelo e armação? A pele ficou idêntica ao original? Ajustar o desfoque e salvar.
3. **HDR**: FOTO › HDR até "Nova"; janela clara com interior escuro; depois com alguém passando.
4. **Pano verde** (cena do dossiê) em FOTO com "Aprimorar fotos" ligado: a cópia aprimorada mantém a cor?
5. **Aspirador 10×** em Noturno e em FOTO + Aprimorar: véu e contraste versus a 0.5.
6. **Resolução**: em cada lente (0,6×, 1×, 3×, 5×, frontal) tocar no botão de MP e anotar as opções; capturar em cada uma e conferir as dimensões do arquivo. Enviar o Relatório técnico (seção "Resoluções de foto").
7. **Timelapse**: 1 min a 1 s com o aparelho apoiado → vídeo de ~2 s; conferir orientação.
8. **Cancelamento**: tirar uma foto no Noturno e tocar no ✕ do selo; o original deve estar em "Originais".
9. Anotar tempo de processamento de cada modo e se o aparelho aqueceu (com e sem carregador).

## 0.5 — dossiê de testes de 14/10/2026 (S24 Ultra)

O que o dossiê mostrou e o que foi feito. As fotos do dossiê chegaram reduzidas (1152×1536, sem EXIF), por isso não serviram para comparar nitidez fina; a origem dos dois vídeos não está confirmada.

| Problema relatado / observado | Causa identificada | Correção | Validação | No aparelho |
|---|---|---|---|---|
| Zoom lento e "engasgando" ao tocar 3×/5×/10× | A 0.4 enviava um `setZoomRatio` **por quadro de tela** e convergia exponencialmente: requisições enfileiradas na câmera e chegada lenta | Curva com duração limitada (foto ≤ 320 ms para 1→10×; vídeo mais suave, ≤ 900 ms), ease-out em escala logarítmica, interrompível; **no máximo uma requisição em voo** (a interface acompanha a curva, a câmera recebe só o valor mais recente) | Testes (`ZoomEngineTest`: duração, monotonia, interrupção, sem empilhar) | Medir no Diagnóstico › Registro de zoom (latência e quadros perdidos) |
| Qual lente está ativa no 3×/5×/10× | Não era lido | `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` + `CONTROL_ZOOM_RATIO` de cada resultado; aviso discreto "Tele 5×" ao trocar; registro com horário | Testes (telemetria) | Confirmar se o firmware Samsung informa a lente física |
| Perda de definição/estabilidade em 5× e 10× | 10× é recorte do sensor 5×; com pouca luz o firmware pode manter outra lente e recortar. Parte disso é processamento proprietário inacessível | OIS ligada explicitamente quando a lente anuncia; a lente ativa agora aparece, para separar "recorte" de "óptica" | Compilação | Comparar 5×/10× com boa luz e com pouca luz, olhando o aviso de lente |
| Exposição saltando durante o zoom no vídeo (133446) | Medição no quadro inteiro: áreas claras saem do enquadramento ao aproximar | Vídeo sem ponto escolhido mede pela região central (opção "Exposição estável no zoom") | Compilação | Repetir 1×→10× com janela clara na borda |
| Tela preta ao ajustar ISO no Pro | A régua começava em AUTO e o primeiro arraste saltava para **ISO 50** mantendo o tempo do automático: até −5 EV (imagem preta) | Régua abre no valor que o automático usa; botão AUTO separado; medidor "±EV em relação ao automático"; sem `SENSOR_FRAME_DURATION` forçado; vigia volta ao automático se o sensor parar de entregar quadros | Testes (medidor −5 EV, posição da régua) | Ajustar ISO/velocidade em ambiente interno |
| Selfie espelhada errada | A 0.4 gravava o espelho só como orientação EXIF, ignorada por muitas galerias | Recorte + rotação + espelho **nos pixels**, mesma resolução, EXIF técnico copiado (uma recompressão JPEG 95) | Teste em pixels reais (`MirroredPhotoSaverTest`) | Abrir na Galeria Samsung |
| Resoluções reais / 200 MP | — | Opção "Resolução máxima" usa a maior saída que o aparelho libera ao app; o valor real (MP) aparece no botão. Diagnóstico mostra tamanhos de alta resolução e o modo de resolução máxima do sensor. **200 MP só se o aparelho liberar; o modo de resolução máxima do sensor exige sessão Camera2 dedicada, ainda não implementada** | Compilação | Ler o diagnóstico e o MP real no botão |
| Proporções | — | 4:3, 16:9, **1:1** e tela cheia (foto igual ao visor) | Compilação | — |
| Vídeo: resolução e FPS | — | Resolução (Automática/Máxima/4K/FHD/HD) e 24/30/60 fps **só entre os que a câmera aceita**; recuo com aviso | Compilação | Testar 4K60 e 24 fps |
| Modos adicionais | — | Painel MAIS: **Pro, Vídeo Pro, Noturno, Câmera lenta** reais; **RAW (DNG)+JPEG** no Pro quando suportado; Panorâmica, Timelapse, Food e Vídeo Retrato marcados "em breve" e não capturam | Testes (render) + Compilação | Câmera lenta 120/240 fps; DNG abre em editor |

**Não implementado nesta versão:** Retrato próprio (cabelo/transparências exigem segmentação e matting locais — próximo grande bloco), Panorâmica com stitching, Timelapse, Food, Vídeo Retrato, super-resolução 20×/30× e o modo de resolução máxima do sensor (200 MP via Camera2).

## 0.4 — base

### Implementado nesta versão

| Recurso | Como funciona | Validação | Pendente no aparelho |
|---|---|---|---|
| Nova interface | Visor 4:3 / 16:9 / tela cheia; barra superior com ícones vetoriais; barra de zoom compacta; seletor de modos em texto; obturador animado; áreas seguras (status, navegação, recorte) respeitadas | Testes (render dos modos Foto, Vídeo, Pro e Retrato indisponível) | Fluidez a 120 Hz, legibilidade sob sol |
| Foto igual ao visor (WYSIWYG) | `ViewPort` do PreviewView aplicado a todos os casos de uso | Compilação | Comparar enquadramento do visor com a foto salva |
| Controles giram com o aparelho | Interface travada em retrato (sessão não reinicia); ícones/textos giram; orientação real gravada no arquivo | Testes (mapeamento de rotação) | Foto/vídeo na horizontal |
| Foco por toque | `FocusMeteringAction` AF+AE no ponto. Após 4 s (configurável) o AF volta ao contínuo e **a medição de exposição permanece no ponto** (sem salto de exposição); 20 s depois, sem EV manual, retorna ao automático completo | Compilação | Velocidade, oscilação e acompanhamento de assunto |
| Bloqueio AE/AF | Toque longo: AF travado + `CONTROL_AE_LOCK` (e `AWB_LOCK`, opcional) via Camera2; selo "AE/AF BLOQUEADO ✕" para desbloquear; funciona durante gravação | Compilação | Confirmar que a exposição não muda ao reenquadrar |
| Exposição por gesto | Arrastar na horizontal do usuário (corrigido para aparelho deitado) ajusta EV dentro da faixa real; régua com sol ao lado do retículo; ~80 dp = 1 EV | Testes (conversão EV/passo/limites) | Sensação do gesto |
| Prioridade a rostos | `CONTROL_SCENE_MODE_FACE_PRIORITY` quando a câmera anuncia; desativada enquanto há ponto manual | Compilação | Verificar se o firmware Samsung honra o modo |
| Selfie como visualizada | Padrão ativo. Inversão gravada como orientação EXIF (sem recodificar, sem perda); opção para desativar | Compilação | Abrir na Galeria Samsung e em outro visualizador |
| Vídeo frontal espelhado | `MirrorMode` configurável separadamente | Compilação | Conferir arquivo |
| Zoom suave | Animação logarítmica por quadro; mais lenta no vídeo; pinça e arraste na barra passam pelo mesmo filtro; paradas só as alcançáveis | Testes (curvas e mapeamento) | Saltos na troca física de lentes durante gravação |
| Troca de câmera física | Se outra câmera exposta cobrir melhor a parada (ex.: ultra-angular separada), reabre nela; fora da gravação | Testes (seleção) | No S24U a câmera lógica cobre 0,6–10× (visto no print), então não deve haver troca |
| Modos OEM | Retrato/Noite/HDR usam extensão do fabricante **só se disponível**; caso contrário o modo aparece esmaecido com explicação e **não captura** | Testes (resolução de modos) | Ler o diagnóstico de cada aparelho |
| Noite básica (própria) | Sem extensão Noite: permite exposições mais longas (faixa de fps de AE com mínimo < 15). **Não há fusão multiquadro** | Testes (escolha da faixa) | Ganho real em pouca luz versus Foto |
| Pro | ISO, velocidade, foco manual, balanço de branco e EV reais via Camera2; parâmetros não suportados ficam desativados; mostra os valores do automático | Testes (escalas) + Compilação | Exposições longas, foco mínimo |
| Vídeo | Qualidade automática/4K/FHD/HD conforme suporte; 60 fps quando a combinação é aceita (senão recua para 30 com aviso); estabilização anunciada; pausa/retomada; aviso de pouco espaço | Compilação | 4K60, estabilização, 5–10 min contínuos |
| Flash | Automático/ligado/desligado; flash de tela para selfie; lanterna no vídeo | Compilação | Exposição com flash |
| Temperatura e energia | Eventos do Android (sem polling). Selo só em aquecimento **severo**, com contexto "carregando". Economia de energia ou aquecimento severo: 4K cede a Full HD; crítico: finaliza a gravação com integridade. Pausa a câmera após inatividade (padrão 2 min) | Testes (política térmica) | Medições com e sem carregador |
| Diagnóstico | Lentes físicas e focais equivalentes, extensões Camera2 (API 31+) e CameraX, faixas de ISO/exposição/EV/fps, estabilização, qualidades de vídeo, estado térmico | Compilação | Enviar relatório de cada aparelho |
| Configurações | Foto, Vídeo, Foco e exposição, Energia, Interface, Diagnóstico e privacidade | Compilação | — |

## Ainda NÃO implementado (não há botão fingindo que existe)

- Panorâmica, Vídeo Retrato, pós-processamento de vídeo.
- Super-resolução 20×/30×, alinhamento subpixel, fusão em RAW.
- Captura de 200 MP pelo modo de resolução máxima do sensor (depende do Diagnóstico do S24U/S25U).
- Histograma, focus peaking, indicação visual de rostos detectados.

## Roteiro de teste físico — 0.5 (S24 Ultra, depois S25 Ultra)

1. Zoom em foto: tocar 1→3→5→10→3→1 rápido. Deve responder de imediato, sem engasgo. Observar o aviso "Tele 3×/5×".
2. Mesmo percurso gravando vídeo 1080p30 e 4K30. Depois: Configurações › Relatório técnico › compartilhar (inclui o **Registro de zoom**: latências, trocas de lente e quadros perdidos).
3. Vídeo com janela clara na borda: aproximar 1×→10×; a exposição deve variar menos que no 133446.
4. Pro: mover ISO em ambiente interno — a imagem não deve ficar preta; observar o medidor de EV; tocar AUTO para voltar.
5. Selfie com objeto à direita: continua à direita na Galeria.
6. Resolução: tocar no botão "12 MP" e anotar o valor máximo que aparece; capturar e conferir as dimensões.
7. MAIS › Câmera lenta: gravar e conferir a reprodução lenta; Vídeo Pro com ISO manual.
8. Pro › RAW: conferir que um .dng aparece junto do .jpg.

## Roteiro de teste físico — 0.4 (S24 Ultra, depois S25 Ultra)

Teste **sem carregador** primeiro e anote se estava carregando.

1. Instalar por cima da versão anterior (mesma assinatura) e abrir. Conferir que nada fica sob a barra de status ou de navegação.
2. Configurações › Diagnóstico › Compartilhar: enviar o texto.
3. Foto 1×: tocar num objeto próximo e depois num distante (o foco muda?). Arrastar na horizontal (exposição muda e para nos limites?).
4. Toque longo: aparece "AE/AF BLOQUEADO"; reenquadrar para algo claro/escuro — a exposição não deve mudar. Tocar no selo para desbloquear.
5. Selfie com texto/objeto à direita: na Galeria deve continuar à direita. Repetir com a opção desligada.
6. Fotos na horizontal (traseira e frontal): orientação correta na Galeria.
7. Proporção 4:3, 16:9 e Tela: a foto salva tem exatamente o enquadramento do visor?
8. Vídeo 30 s: zoom 1→3→5→10 pela barra e pela pinça; anotar saltos na troca de lente. Pausar/retomar. Sair do app gravando: arquivo deve abrir.
9. Modos Retrato/Noite: aparecem disponíveis ou esmaecidos? Se Noite básica, comparar com Foto em ambiente escuro.
10. Pro: fixar ISO e velocidade; foco manual perto/infinito.
11. Gravar 5–10 min em 4K: anotar se surge o selo de aquecimento e se estava carregando.

Formato de retorno: modelo, item, resultado (ok/falha), observação e, se houver, o relatório de diagnóstico. Não é necessário enviar fotos pessoais.


