# 0.32.0 — Galeria no app, disparo com a palma e IA de detalhe no zoom (Claude; branch `claude/nova-interface`; APK de teste build113 entregue)
- **[Baixar ZIP com app-release.apk — build113 (1113)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39356816098/artifacts/12235058659)**. Artefato 12235058659, 156086536 bytes; sha256:369405794c2a92918134248749e2c261755644c7981f3fa2e989cc43b72f8987; expira 2026-11-19T02:12:26Z; sem release. A verificação do CI confirmou no APK os modelos de detalhe, de mão e de profundidade (NAFNet obrigatório). Fonte 25b1fae; gatilho restaurado em 96bc27a.
- **Pedido do dono (20/10):** fazer os extras que faltavam; DNG fundido fica de fora (a fusão é em JPEG, então não seria RAW de verdade).
- **Galeria no app (`GalleryActivity`, `core/GalleryBadge.kt`):** o toque na miniatura agora abre uma grade com as fotos e vídeos da Nova (sem originais e sem rajadas de diagnóstico). Cada foto tem um selo:
  - **IA:** ruído reduzido ou detalhe aplicado. Vem da descrição EXIF, então vale para qualquer foto.
  - **IA…** ou **IA na fila:** a IA está trabalhando; o selo é relido a cada 3 s.
  - **IA recusada** ou **sem ruído:** só nas últimas 4 sessões de revisão.
  - **NOVA:** ajustada sem IA.

  Tocar numa foto abre a comparação quando ela ainda tem sessão de revisão; senão abre o visualizador do aparelho. Toque e segure: abrir fora, compartilhar, apagar. Não precisa de permissão de leitura, porque o Android mostra ao app só os arquivos que ele criou. Depois de reinstalar, as fotos antigas somem da grade, mas continuam na galeria do aparelho. A comparação, que antes ficava no toque e segure da miniatura, continua lá.
- **Disparo com a palma (`camera/PalmShutter.kt`, `core/PalmPose.kt`), desligado por padrão:** na Foto e no Retrato, a palma aberta por ~0,7 s inicia o contador de 3 s (ou o temporizador, se for maior). Depois do disparo, espera 5 s antes de aceitar outra palma.
  - **Modelo:** detector de mão do MediaPipe (Apache-2.0 pelo model card; 7,8 MB baixados no build com sha256 fixo). O classificador de gestos pronto da Google não entra porque não tem licença publicada.
  - **Regra da palma, feita pelo app:** quatro dedos esticados (ponta ≥ 1,15× mais longe do pulso que a junta), polegar afastado, mão com pelo menos 6% da imagem.
  - **Validação:** com o modelo real no servidor, nas imagens de teste do próprio MediaPipe, acertou 9 de 9 (2 mãos abertas sim; punho, polegar para cima e para baixo, indicador e "V" não). O conjunto é pequeno, então falta ver no aparelho como se comporta a mão de lado ou com pouca luz.
  - **Custo:** lê a prévia em 480 px ~4×/s (~1,5×/s com calor ou economia) e pula a leitura se outra IA nativa estiver ocupada. O custo por leitura não foi medido no S24.
- **IA de detalhe (`processing/DetailRestorer.kt`, `core/imaging/DetailGate.kt`, `core/DetailPolicy.kt`):** Real-ESRGAN compacto (`realesr-general-x4v3` misturado com o `wdn` a 0,3) no ncnn que já existe, convertido no CI (`tools/realesr/export_ncnn.py`, ncnn × PyTorch 68 dB no servidor).
  - **Quando:** só na foto ampliada digitalmente sobre a tele (super-resolução ≥ 1,8×, ou zoom ÷ lente ≥ 1,8 com lente ≥ 2,5×; por exemplo 10× na lente 5×). Fica fora na principal, onde o 2× é recorte real do sensor de 200 MP, e também no Noturno, Retrato, Food e em fotos acima de 16,5 MP.
  - **Como:** a foto vai à metade (a escala que o sensor viu), a rede devolve o dobro e cada bloco de 32 px passa pela checagem de volta: a saída reduzida precisa bater com a entrada (erro ≤ 0,010 aceita, ≥ 0,016 recusa). Blocos recusados crescem um bloco para os lados; a transição é suavizada. Se menos de 15% da foto for aceita, a foto fica como está.
  - **Calibração nas fotos de 10× do dono (15/10, fora do Git):** a checagem acendeu exatamente nas letras pequenas deformadas ("ÁGUA E PÓ") e na parede de reboco "pintada". Cimento aceito em 75% (desenho e grade bem mais nítidos), aspirador em 91% (mangueira mais nítida, letras mantidas do original), parede em 12% (foto mantida). Sem costura entre blocos.
  - **Limite visto:** o chão liso de cimento perde um pouco de grão fino.
  - **Ordem:** roda no mesmo refino em segundo plano do NAFNet, depois dele. Se o NAFNet recusar ou dispensar, o detalhe ainda pode entrar sozinho. O tom é reaplicado depois, então não muda.
  - **Custo:** ~9 TFLOP numa foto de 12 MP; não medido no S24 (o diagnóstico mostra "IA de detalhe … N blocos em X ms").
  - **Licença:** repositório BSD-3, com a ressalva de que os pesos não têm declaração separada (`docs/THIRD_PARTY.md`).
- **Mudou × não mexi:** mudaram o toque na miniatura (agora abre a galeria do app), uma opção nova (palma) e a IA de detalhe só no zoom digital da tele. Não mexi em Noturno, tom, Retrato, vídeo, foto na principal nem na IA de ruído (o NAFNet não mudou; só o fluxo deixou de parar quando ele é dispensado).
- Testes: `GalleryBadgeTest`, `PalmPoseTest` (pontos reais do detector), `DetailGateTest` (checagem, recusa com margem, mistura, política).

---

# 0.31.6 — Etapas F (documento), G (foco no escuro), aviso de lente suja e local opcional (Claude; branch `claude/nova-interface`; APK de teste build112 entregue)
- **[Baixar ZIP com app-release.apk — build112 (1112)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39350868586/artifacts/12233283362)**. Artefato 12233283362, 147922395 bytes; sha256:13e5e23d208a297141d17c57788ef4d0adc22b4545dd15cb15bb38550cf87185; expira 2026-11-19T01:07:44Z; sem release. Fonte 527ead4; gatilho restaurado em 0e381d5.
- **F — Documento (Ferramentas › "DOC"):** prévia na maior resolução disponível (sem nova captura) → `core/DocQuad.kt` acha a folha (maior região clara pelo limiar de Otsu, sem as que encostam em 3+ bordas; cantos pelos extremos de x+y e x−y; recusa se a área do quadrilátero não bate com a da região ou < 15% da cena) → perspectiva endireitada (`Matrix.setPolyToPoly`, até 3000 px) → `Pictures/Nova Camera/Documentos`. "Compartilhar PDF": uma página A4 pelo `PdfDocument` do Android em `Documents/Nova Camera`. Limites: folha clara sobre fundo mais escuro, inteira na tela; resolução da prévia (não da foto).
- **G — Foco no escuro (`FocusPolicy.DARK_WAIT_MS`):** fora do Noturno, com ISO ≥ 1600 ou ≥ 33 ms, o refoco antes da rajada (ponto tocado ou centro) espera até 1,5 s em vez de 0,7 s. Noturno inalterado (1,2 s).
- **Extra 1 — Aviso de lente suja (`core/LensSmudge.kt`):** reaproveita o quadro que o leitor de QR já lê, então o custo é quase zero. Só roda com luz boa (ISO ≤ 400 e ≤ 20 ms). O aviso "A lente parece suja" aparece quando o ponto preto fica ≥ 12% e o contraste ≤ 45% em ≥ 80% das amostras de 30 s, em ≥ 3 cenas diferentes. No máximo uma vez a cada 30 min. Os limiares vêm da teoria e não foram calibrados no S24 Ultra; se avisar sem motivo, subir `VEIL_BLACK`.
- **Extra 4 — Salvar local (`core/GeoTag.kt`), desligado por padrão:** ao ligar, a câmera pede localização (precisa ou aproximada, a pessoa escolhe). Com a câmera aberta, usa a última posição do Android (provedor fused no Android 12+, senão GPS/rede; a cada 30 s ou 25 m). O app continua sem internet. A posição é copiada **no disparo** (`Job.geo`), não ao fim do processamento, e vai para o EXIF GPS da foto final, do original, da selfie espelhada e da gravação direta do CameraX. Posição com mais de 10 min ou imprecisão acima de 500 m não entra. Negar a permissão desliga a opção. Vídeo não leva local.
- **Mudou × não mexi:** novo botão DOC; só o tempo de espera do foco no escuro; aviso de lente (só texto na tela). Local só com a opção ligada. Processamento, tom, QR, texto, vídeo intocados.
- Testes: `GeoTagTest` (graus/min/seg e hemisfério; desligado, velho ou impreciso fica fora), `LensSmudgeTest`, `DocQuadTest` (cantos de uma folha inclinada ±3 px; mesa sem folha e parede clara recusadas), `FocusPolicyTest` (+1).

---

# 0.31.5 — Etapa E: ler texto (offline) + build resistente à falha do Google Drive (Claude; branch `claude/nova-interface`; APK de teste build111 entregue)
- **[Baixar ZIP com app-release.apk — build111 (1111)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39349567047/artifacts/12233500190)**. Artefato 12233500190, 147905968 bytes; sha256:d86b38acd3d523add65253718830f9a2fcc868a1896a5b43fb495b00fa5bec4b; expira 2026-11-19T00:53:26Z; sem release. Inclui 0.31.1–0.31.4 (B, B2, C, D). Gatilho restaurado em d7dfb0e.
- **Ler texto:** Ferramentas › "TEXTO" congela a prévia (snapshot de 1600 px, sem nova captura) e lê com PP-OCRv5 mobile (detector DB + reconhecedor CTC, 18.383 caracteres, inclui acentos do português) no ncnn que o app já tem (Vulkan quando disponível; uma IA nativa por vez). Resultado: texto selecionável (copiar um trecho com o gesto normal), "Copiar tudo", "Compartilhar"; links, e-mails e telefones viram toque (o Android pergunta o app). Sem internet.
- **Pós-processamento sem OpenCV (`core/OcrPost.kt`):** componentes conectados do mapa do detector (limiar 0,3; caixa ≥ 0,6), expansão pela altura da linha (×1,95, como o exemplo oficial do ncnn), CTC (vazio, repetição, espaço), ordem de leitura por linha. Texto inclinado/vertical fica fora nesta versão. Linhas com confiança média < 0,5 descartadas.
- **Validação real (no servidor, mesmo modelo e mesmo pós-processamento, ncnn Python):** imagem limpa com "Máscara descartável INFANTIL / Cuidando com carinho / das crianças do Brasil / Contém 50 unidades · R$ 19,90" → 4/4 linhas corretas (confiança 0,96–0,99). Recorte escuro da caixa na foto Samsung de 19/10 → "INFANTIL", "Contem", "50" e parte das linhas (cena no limite do sensor).
- **Licença:** pesos PaddleOCR Apache-2.0; conversão ncnn do autor do ncnn (nihui/ncnn-assets), baixada no build com sha256 fixo; dicionário e pré/pós-processamento adaptados do exemplo oficial do ncnn (BSD-3). `docs/THIRD_PARTY.md`. APK +~10,7 MB.
- **CI:** o build 110 (0.31.3) caiu porque o Google Drive dos pesos oficiais do NAFNet recusou o download ("acessos demais") e o APK saiu sem a IA (a verificação final barrou, como deve). Agora o modelo convertido fica em cache do Actions e o download tenta 3 vezes.
- **Mudou × não mexi:** novo botão e leitor sob demanda; nada roda sozinho. Captura, processamento, QR, vídeo intocados.
- Testes: `OcrPostTest`.

---

# 0.31.4 — Etapa D: QR e códigos de barras sempre ligados na FOTO (Claude; branch `claude/nova-interface`; no APK build111 (0.31.5))
- **Pedido do dono (20/10):** leitura de QR como a câmera nativa, sempre ligada na FOTO.
- **Como:** zxing-cpp 3.0.2 (Apache-2.0, conferido no POM oficial; `docs/THIRD_PARTY.md`) lendo a própria prévia (snapshot de 960 px pelo `PreviewFrameReader`, sem stream novo da câmera) ~2,5×/s; com calor ≥ moderado ou economia, 1×/s; pausa durante a captura; só no modo FOTO (`camera/QrScanner.kt`).
- **Na tela:** chip "QR · domínio ›" (vibração curta); some 4 s depois de o código sair da imagem. Toque mostra o conteúdo inteiro antes de qualquer ação: link (domínio no título; sem https leva aviso) → Abrir/Copiar; Wi-Fi → Conectar (painel do próprio Android, API 30+) / Copiar senha; telefone → Ligar (abre o discador); e-mail → Escrever; texto/código de produto → Copiar/Compartilhar. **Nada abre sozinho.** Sem permissão nova (o app continua sem internet).
- **Classificação testável (`core/QrContent.kt`):** link (https/http/www), WIFI: com escapes, tel:, mailto:, MATMSG:, texto; repetição do mesmo código suprimida (`QrDedupe`).
- **Opção:** "Ler QR e códigos de barras na Foto" (ligada).
- **Mudou × não mexi:** só a leitura da prévia na FOTO e o chip. Captura, processamento, foco, vídeo intocados.
- **Não medido no aparelho:** custo por leitura (o diagnóstico ainda não mostra; `QrScanner.lastReadMs` disponível para a próxima versão).

---

# 0.31.3 — Etapa C: atenção ao assunto na FOTO no escuro (Claude; branch `claude/nova-interface`; no APK build111 (0.31.5))
- **Pedido do dono (19–20/10):** sem toque, achar sozinho onde há informação na sombra (a caixa com a criança e as letras embaixo do frasco) e dar prioridade a ela; com toque, o objeto tocado; o resto não muda. Nada de deixar a foto inteira clara como no Noturno.
- **Detector de objetos adiado por licença:** o EfficientDet-Lite0 do MediaPipe não tem licença publicada pela Google (nem no arquivo nem em model card; só um pacote de terceiros diz Apache-2.0). A regra do projeto exige licença confirmada. Alternativa com licença clara para depois, se precisar: NanoDet (Apache-2.0, roda no ncnn que já temos).
- **Como acha o assunto (`core/imaging/SubjectAttention.kt`):** na foto reduzida (~512 px) e suavizada (o ruído some, contorno real fica): densidade de contornos acima do piso de ruído da própria cena × escuridão local (média abaixo de ~30%); mais o ponto tocado (círculo de 12% do menor lado). Bordas suavizadas para não haver recorte visível.
- **O que faz:** só nessas áreas, sombra sobe até +0,8 EV pela luminância suavizada (como o tom local), preto profundo (< ~3%) e meio-tom/alta luz intocados, sem estourar (teto 250).
- **Validação antes de valer:** cobertura de assunto entre 0,5% e 40% da cena (senão não é "atenção"), a área clareia ≥ 8% e não estoura mais que 0,5%. Recusada: nada muda. Rastro: "atenção: cobertura=…% toque=… aceita=…"; nota da foto: "sombra do assunto clareada (N% da cena)".
- **Onde vale:** FOTO (um quadro, multiquadro, zoom, HDR) com ISO ≥ 800. Fora: Noturno, Retrato, Food, vídeo, foto com luz. A revisão (inclusive a prévia da IA por região) reaplica o mesmo mapa gravado na sessão.
- Testes: `SubjectAttentionTest` (acha as letras na sombra e deixa a parede; cena sem assunto recusada; toque na sombra vira assunto; recorte = mesmo trecho da foto inteira).

---

# 0.31.2 — Etapa B2: rajada rápida na tele da FOTO (Claude; branch `claude/nova-interface`; no APK build111 (0.31.5))
- **Medido (diagnóstico 19/10, FOTO 3×/5× no escuro):** 960–1040 ms entre quadros da rajada (solicitação ~260 ms + entrega ~700 ms por JPEG); 8 quadros da Alta resolução = 8 s na mão. No Noturno, desde a 0.29.5, a tele fica fixada na lente física e a rajada caiu para ~250 ms/quadro.
- **Causa:** a FOTO só fixava a tele nativa com luz boa (ISO < 800 e < 20 ms); no escuro ficava na câmera lógica, onde o firmware processa cada JPEG por ~1 s (e pode trocar a 5× pela 3× recortada).
- **Mudança:** FOTO fixa a tele nativa a partir de 2,5× também no escuro, igual ao Noturno, com o mesmo resgate: foco que não fecha por 2,5 s (perto demais/escuro) devolve à lente automática com aviso. Fora de FOTO/Noturno, nada muda.
- **Disparo sem atraso (parcial):** o "toque→captura 968 ms" medido vinha do refoco no ponto antes da rajada (espera até 700 ms) + ~260 ms de início; o refoco continua (foto nítida > 0,7 s), o ganho aqui é na rajada.
- **Mudou × não mexi:** só a escolha da lente na tele da FOTO no escuro. Intocados: processamento, tom, IA, Noturno, vídeo, Retrato.
- **Não medido no aparelho:** confirmar no diagnóstico "timestamp bufferN: intervalo" ~250 ms na FOTO 3×/5× no escuro.

---

# 0.31.1 — Etapa B: acabamento em vários núcleos, foto idêntica (Claude; branch `claude/nova-interface`; no APK build111 (0.31.5))
- **Medido (diagnóstico 19/10, 49 MP):** tom 3,5–4,1 s, pouca luz 3,5–4,0 s, perfil 3,8–4,3 s — três passadas de `Engine.toneInPlace` em um núcleo, 12–14 s do acabamento.
- **Mudança:** `toneInPlace` lê e grava faixas de 64 linhas em série (o Bitmap nunca é tocado por duas threads) e faz a conta tonal das faixas em paralelo (`Parallel`, que já respeita calor e economia). Cada pixel depende só dele e da coordenada: resultado idêntico. Esperado ~3× mais rápido com 4 núcleos (não medido no S24).
- **Prova:** `EngineAndroidTest.tomEmParaleloIgualAUmNucleo` (tom + pouca luz + perfil, 1 × 4 núcleos, pixel a pixel) e o teste de regressão de faixas existente. JVM: 547 → 391 ms.
- **Mudou × não mexi:** só a distribuição do trabalho do tom. Fórmula, planos e imagem intocados; detalhe e crominância continuam como estavam.

---

# 0.31.0 — Etapa A: estabilidade (Claude; branch `claude/nova-interface`; APK de teste build109 entregue)
- **[Baixar ZIP com app-release.apk — build109 (1109)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39347132376/artifacts/12231662706)**. Artefato 12231662706, 138720298 bytes; sha256:dea6ec5b08e1f4189d10411d9470b180776bdf834c290f54e7e0b2d0acde3677; expira 2026-11-19T00:31:06Z; sem release. Gatilho restaurado em e0eff22.
- **Plano autorizado pelo dono (20/10):** A estabilidade → B acabamento mais rápido → B2 rajada rápida e disparo sem atraso → C atenção ao assunto → D QR sempre ligado na FOTO → E texto → F documento → G foco no escuro → itens extras (lente suja, DNG fundido, palma, localização opcional, galeria própria) e IA de detalhe com trava de consistência. Uma etapa por APK.
- **Fotos de Alta resolução perdidas (#3/#4 do diagnóstico de 19/10):** a checagem de memória antes da super-resolução usava o heap Java (cheio de temporários do alinhamento: ~320 MB "necessários" × ~310 livres) para uma saída que é Bitmap em memória nativa → desistia e salvava só o original. Agora usa a RAM livre do aparelho com folga 2× e sem pressão de memória (`core/OutputMemory.kt`).
- **Fechamentos nativos (MediaPipe/ncnn):** causa ainda desconhecida. (1) Diagnóstico novo (`core/NativeTombstone.kt`): lê o tombstone (protobuf do AOSP) e mostra sinal, mensagem do abort, **a thread que caiu** com o topo da pilha e as últimas linhas de log de erro (antes: textos soltos de todas as threads). (2) Proteção (`core/NativeGate.kt`): uma inferência nativa por vez; trabalho de foto (NAFNet por bloco, profundidade e segmentação da foto, contorno ao toque) espera a vez; análise ao vivo (Retrato/Cinema) pula o quadro se ocupado, e a IA em segundo plano cede 120 ms quando a prévia pulou quadro.
- **Mudou × não mexi:** mudou só a checagem de memória da Alta resolução, o diagnóstico de falha nativa e a ordem de execução das IAs nativas. Intocados: imagem (fusão, tom, Noturno, IA), foco, vídeo, interface.
- **Não medido no aparelho.** Testes: `StabilityTest`, `NativeGateTest`.

---

# 0.30.7 — IA que mede, age na medida e prova que melhorou; Noturno automático da FOTO desligado por padrão (Claude; branch `claude/nova-interface`; APK de teste build108 entregue)
- **[Baixar ZIP com app-release.apk — build108 (1108)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39344782935/artifacts/12230218836)**. Artefato 12230218836, 138719432 bytes; sha256:cf4eff57c76519dff8e6c927ec11fabfaf03eb7c39ca408a05f5e5a91e09710a; expira 2026-11-19T00:06:39Z; sem release. Gatilho restaurado em 2391d82.
- **Pedido do dono (19/10):** IA com identificação e validação (aprovada); **não** entrar no Noturno sozinho na FOTO.
- **Pesquisa (resumo):** Night Sight (Liba et al., SIGGRAPH Asia 2019) tira o ganho da captura adaptada ao movimento + fusão robusta + balanço de branco aprendido (FFCC) + tom; redes de clarear (Zero-DCE/SCI) erram cor/fidelidade (~15 dB no LOL); NAFNet generaliza bem para ruído de outros celulares (~38,4 dB fora do SIDD); avaliação sem referência no aparelho é prática com medidas por defeito (ruído, nitidez).
- **Medir (`core/imaging/AiQuality.kt`):** ruído = mediana do σ de 9 blocos em resolução cheia; bordas = média dos 5% maiores gradientes (Sobel) na foto reduzida 4× (o ruído some, o contorno real fica).
- **Agir na medida:** força da rede pelo ruído: σ < 1,2 nada (foto já limpa), < 3 → 55%, < 6 → 75% (antes fixo), ≥ 6 → 90%.
- **Validar:** a foto só é trocada se o ruído caiu ≥ 10% e as bordas perderam ≤ 8%; senão fica a da galeria e o diagnóstico registra "IA recusada pela validação (ruído σ a→b, bordas x→y, força z%)". Aceita: "IA aceita (…)". Testes: limpeza real aceita, limpeza que borra o texto recusada, sem ganho recusada.
- **Noturno automático da FOTO (0.30.6):** continua no código, **desligado por padrão** (opção em Ajustes).
- **Escopo:** mudou só a decisão de força/aceite da IA e o padrão da opção. Intocados: Noturno, tom, fusão, foco, vídeo, Retrato.

---

# 0.30.6 — Noturno automático na FOTO (Claude; branch `claude/nova-interface`; APK de teste build107 entregue)
- **[Baixar ZIP com app-release.apk — build107 (1107)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39343446835/artifacts/12230131842)**. Artefato 12230131842, 138719617 bytes; sha256:a87c7668b7331bd2c5e4afdc14bc94cea0ab797cdc371762018240cb3b5780f5; expira 2026-11-18T23:53:01Z; sem release. Gatilho restaurado em e4f9eb3.
- **Retorno do dono (19/10):** a FOTO comum no 5×/10× no escuro sai fraca, enquanto o modo Noturno na mesma cena está "perfeito". Pediu resolver, não desligar opção, e não mexer no que funciona.
- **Raiz:** na FOTO, a tele no escuro ia para a Alta resolução (8 quadros com o tempo/ISO do automático — ISO 2056 1/24 s — ampliados para 49 MP) ou para 3 quadros iguais; nunca usava o plano de exposição do Noturno (tempo mais longo por quadro pelo giroscópio, ISO menor, fusão e tom do Noturno).
- **Mudança (`core/AutoNightPolicy.kt`):** FOTO, antes do disparo: tempo do automático ≥ 25 ms e ISO ≥ 800 na tele (≥ 2,5×) ou ≥ 2400 na principal → a captura e o processamento usam o motor do Noturno, **sem mudar o Noturno**. Nesses casos não há Alta resolução (com ISO alto ela só ampliava ruído). 10× com recorte usa a super-resolução do Noturno (0.29.6). Foco: o da FOTO (ponto tocado ou AF contínuo mantido), não o centro do modo Noturno. Quente/economia: não entra. Opção "Noturno automático na Foto" (ligada). Legenda "Noturno automático: …".
- **Contrato de escopo (pedido do dono):** mudou só a decisão da FOTO no escuro. Intocados: modo Noturno (exposição, fusão, tom, lente), FOTO com luz, Retrato, vídeo, IA (0.30.4/0.30.5), foco (0.30.2/0.30.3).
- **Não medido no aparelho.** Testes: `AutoNightPolicyTest` (usa as fotos reais de 19/10 como casos).

---

# 0.30.5 — IA na Alta resolução em meia resolução (Claude; branch `claude/nova-interface`; APK de teste build106 entregue)
- **[Baixar ZIP com app-release.apk — build106 (1106)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39342682636/artifacts/12229917609)**. Artefato 12229917609, 138717859 bytes; sha256:ce8449cc117fd39f32de0b4462321755239953850d0c68f7470ff62f61200b29; expira 2026-11-18T23:43:48Z; sem release. Gatilho restaurado em 369a11b.
- **Retorno do dono (19/10 20:22, 0.30.4, 5× Alta resolução 49 MP):** a IA não entrou. Diagnóstico: "IA dispensada sem carregar: a última projeção para 6120×8160 excedeu o orçamento" — 768 blocos × ~0,35–0,45 s não cabem em 300 s, e a projeção passa a pular a foto por 30 min.
- **Correção (`core/imaging/HalfResidual.kt`, `NcnnDenoiser.denoiseHalf/denoiseAuto`):** acima de 16,5 MP a foto é reduzida 2× (média 2×2, em faixas), a rede limpa a reduzida (¼ dos blocos, custo de ~12 MP) e só a diferença que ela fez volta ampliada (bilinear) para a foto cheia. O detalhe da foto grande não passa pela rede. A prévia por região da revisão usa o mesmo caminho (recorte mínimo 576 px) para mostrar o que a foto inteira vai receber.
- **Limite conhecido (teste `HalfResidualTest`):** ruído de grão ~2 px (o da super-resolução, vindo dos quadros de 12 MP) cai bem; ruído de 1 pixel isolado cai pouco (~10%).
- **Também neste diagnóstico (sem correção, a confirmar):** (1) **cor**: tampa branca do frasco saiu (120,130,91) RGB — verde-amarelada — contra neutra (104,98,102) na foto 5× das 19:08; nenhum passo da Nova mexe em balanço de branco, então veio do AWB da captura ou da luz da sala; falta foto da Samsung no mesmo momento. (2) **foco**: a captura com toque registrou "AF solicitado POINT … AF limite de espera" (700 ms sem confirmar), e a sem toque "AF mantido onde o contínuo focou: 0.58 D" — o HOLD funciona. (3) duas rajadas (#3/#4) pararam depois do alinhamento, sem resultado no rastro — causa não identificada.
- Testes: `HalfResidualTest`.

---

# 0.30.4 — IA automática em zoom e pouca luz, inclusive Alta resolução (Claude; branch `claude/nova-interface`; APK de teste build105 entregue)
- **[Baixar ZIP com app-release.apk — build105 (1105)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39339531555/artifacts/12228572728)**. Artefato 12228572728, 138714343 bytes; sha256:4981141b0b60b68a246c2de887674a93d50367bc1cc2929742e9af384b894c3b; expira 2026-11-18T23:15:16Z; sem release. Gatilho restaurado em dc70ce4.
- **Pedido do dono (19/10):** IA em toda foto em que ajude (zoom 3×/5×/10× ou recorte digital, foto ruim/escura), nunca na foto boa de dia; aprovado o critério proposto.
- **Critério (`core/AiAutoPolicy.kt`):** Noturno e Retrato (como antes) + zoom ≥ 1,3× + ISO ≥ 800, até ~52 MP (200 MP fora). Food fora. Vale também para a FOTO comum de um quadro (antes a IA nunca entrava nela): nesse caso, sem tom/assinatura/estilo, a foto da galeria fica intacta até a IA terminar, e o original vai para Originais se "manter originais" estiver ligado.
- **Padrão:** "Redução de ruído por IA" agora vem ligada (quem desligou antes continua desligado).
- **Alta resolução (49 MP):** a IA processa em faixas no próprio bitmap (`core/DenoiseBands.kt`): memória extra ~3 faixas (≈19 MB) em vez de uma cópia (≈196 MB). Teste prova que nenhuma faixa é gravada antes de a última leitura das linhas dela.
- **Orçamento:** automático passa a usar o mesmo teto do pedido manual (300 s; antes 60 s, que fazia o 12 MP desistir sempre). Projeção do custo conta os blocos limpos já pulados. Temperatura ≥ moderada, economia de bateria, bateria ≤ 15% fora do carregador e foto nova continuam interrompendo.
- **Revisão:** quando a IA não está disponível, a tela agora diz por quê (antes simplesmente não aparecia nada).
- **Limite (dito ao dono):** a rede tira ruído; não recupera foco nem detalhe. O realce de detalhe da tele (já existente, depois da IA) aproveita a base mais limpa.
- **Não medido:** tempo real em 12/49 MP, calor em sequência de fotos, quanto pula. Diagnóstico: linha "NAFNet …" com blocos/limpos pulados/tempo/térmico.
- Testes: `AiAutoPolicyTest` (inclui faixas).

---

# 0.30.3 — Toque no objeto: exposição volta à região normal (Claude; branch `claude/nova-interface`; APK de teste build104 entregue)
- **[Baixar ZIP com app-release.apk — build104 (1104)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39338267955/artifacts/12228546256)**. Artefato 12228546256, 138712317 bytes; sha256:b96e18657abf48b2d0b8ada21701efa98daeac6ba387223d85204e9436f763ec; expira 2026-11-18T23:05:07Z; sem release. Gatilho restaurado em a2d2a51.
- **Retorno do dono (19/10, 3×, Alta resolução 49 MP, 0.30.2):** foto "mais clara, esbranquiçada". Medido: os mesmos frascos saíram ~40% mais claros que na foto 5× da 0.30.1 (lado do frasco 83→117), com exposição 1,55× maior na captura (ISO 2056→3196, 1/24 s) — a diferença veio da exposição do sensor, não do tom (pipeline de tom inalterado desde 0.30.0).
- **Causa provável (minha, 0.30.2):** o refino do toque no objeto movia AF **e AE** para dentro do objeto; tocar a caixa na sombra fazia a exposição medir só a sombra e clarear a foto inteira.
- **Correção:** só o AF vai para o objeto; a AE fica na região normal do toque (15% em volta do dedo, como até a 0.30.1), também no refoco antes da rajada e no retorno ao automático (`NovaCamera.meterAction`).
- **IA na revisão "não aparece":** sem print/diagnóstico não dá para saber se a linha da IA nem aparece (modelo/biblioteca não carregados) ou se a prévia falhou. Pedido ao dono. Limite reafirmado: NAFNet tira ruído, não recupera foco/detalhe (caixa 3× no escuro, 574×344 px reais).

---

# 0.30.2 — Foco no objeto tocado e foco sem toque no assunto da câmera (Foto e Retrato) (Claude; branch `claude/nova-interface`; APK de teste build103 entregue)
- **[Baixar ZIP com app-release.apk — build103 (1103)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39335098544/artifacts/12225573802)**. Artefato 12225573802, 138712103 bytes; sha256:a2bc5b6deb5a35c641d49f11010a9f09353e3b570454aca46247b38061145b27; expira 2026-11-18T22:36:21Z; sem release. Gatilho restaurado em db27c00.
- **Retorno do dono (19/10, 5× à noite, Foto, Alta resolução 49 MP, ISO 2056 1/24 s × 8):** caixa com a criança na prateleira de baixo borrada na Nova e nítida na Samsung (mesma lente 115 mm f/3.4, ISO 2000 1/15 s). Frascos nítidos (até mais limpos) na Nova. O borrão é uniforme, sem contorno duplo: **profundidade de campo/foco**, não fusão nem IA — e não se recupera com IA de ruído (só uma IA generativa "inventaria" o desenho). Queixa junto: "toco e não acontece nada, só marca".
- **Sem diagnóstico desta foto:** não dá para afirmar se houve toque. Sem toque, a rajada multiquadro da tele travava o AF no **centro** (parede/prateleira); a Samsung deixa o próprio AF escolher.
- **Toque no objeto (Foto e Retrato):** o contorno MagicTouch que já aparecia ao toque agora define a região do AF: o maior retângulo dentro do objeto em volta do dedo (até 20% do menor lado; mín. 3%) — o AF refaz a medição só no objeto, sem o fundo que a região padrão (15% em volta do dedo) pegava. Dedo na borda: usa o ponto do objeto mais próximo. Toque no fundo/cena inteira: fica a região normal. Bloqueio (toque longo), rastreio e vídeo inalterados. Depende do "Contorno ao toque" ligado (padrão).
- **Sem toque, rajada na tele (Foto/Retrato):** se o AF contínuo do aparelho já assentou (`PASSIVE_FOCUSED`) e a lente aceita foco manual, a lente fica exatamente onde ele focou durante a rajada (AF desligado na mesma distância; liberado ao terminar) — a escolha de assunto da própria câmera. AF ainda procurando: centro, como antes. Noturno inalterado.
- **Diagnóstico:** o rastro da foto agora tem "AF antes: estado/foco D/ponto" e "AF mantido onde o contínuo focou: X D".
- **Não medido no aparelho.** Testes: `FocusPolicyTest` (+1), `ContourFocusTest`.

---

# 0.30.1 — Vídeo travado no rosto/assunto + IA na região ampliada e na foto inteira (Claude; branch `claude/nova-interface`; APK de teste build102 entregue)
- **[Baixar ZIP com app-release.apk — build102 (1102)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39288075407/artifacts/12203164450)**. Artefato 12203164450, 138706096 bytes; sha256:7cad63e2b47ab67863aef88713f99406072bea18b37d7a1341da99e4f71a1f08; expira 2026-11-18T16:53:45Z; sem release. Gatilho restaurado em 17d74b5.
- **Responsabilidade:** o dono pediu os dois ao Claude em 19/10 ("pode fazer a IA e o vídeo travado no rosto ou tá nas pessoas também, objetos"), com o plano do Codex pausado. A rede (NAFNet), a força (75%) e o acabamento não mudaram; o que é novo é onde e quando ela roda.
- **Vídeo — rosto travado (`core/FaceLock.kt`, `camera/SubjectTrackingSession.kt`):** com "acompanhar assunto" ligado, Vídeo e Retrato pedem os rostos do próprio sensor (modo simples, sem CPU do app). Toque num rosto (com 35% de folga: queixo/testa) segue aquele rosto pelo sensor a cada 100 ms (250 ms quente/economia) — sem ler a prévia. Recusa trocar de pessoa (distância ≤ 1 rosto, tamanho 0,6–1,7×). Rosto sumido por 0,8 s (virou de costas, mão na frente) passa ao rastreio por aparência na última posição, com o tamanho do rosto; quando o rosto reaparece no alvo, volta ao sensor. Chip: "ROSTO TRAVADO". Vídeo Pro e Noturno não pedem rostos (como antes).
- **Pessoas sem rosto e objetos (`core/SubjectTracker.kt`):** o modelo de aparência agora se adapta devagar (12% por leitura) só com leitura inequívoca (correlação ≥ 0,9 e margem ≥ 0,1), preso ao modelo do toque (correlação ≥ 0,6) para não derivar para o fundo; incerto, procura numa área 2× maior (passo 4 px) e só desiste após 1,0 s (era 0,6 s). Testes: aparência que muda até 60% (falha sem a adaptação), salto de 54 px (falha sem a busca ampla), oclusão, rosto→aparência.
- **IA na região ampliada (revisão):** dois dedos ampliam/movem o comparador (até 8×, toque duplo volta). A partir de 2×, o recorte visível em resolução cheia (até 992×992, 16 blocos) passa pela rede e aparece com o mesmo acabamento da foto inteira; a linha compara "Sem IA × Com IA" ali. Nada é gravado. O tom do recorte usa os planos da foto inteira (`ProcessingQueue.RenderPlans` + `Engine.Region`); `RegionRenderTest` prova recorte = mesmo trecho da foto inteira (±2 níveis) e que o tom calculado só do recorte divergiria. Retrato com pessoa: sem prévia por região (o desfoque depende da foto inteira).
- **IA na foto inteira (revisão → "Refinar foto inteira com IA"):** entra na fila de refino existente (0.9.2) com orçamento próprio de 300 s (o automático segue 60 s); roda mesmo com a IA automática desligada; espera foto em processamento, aquecimento ≥ moderado, economia de bateria e bateria ≤ 15% fora do carregador, e recomeça sozinho quando a temperatura/bateria permitirem (antes só depois da próxima foto). Ao terminar troca a mesma entrada da galeria; o original continua em Originais. Progresso na revisão.
- **Blocos limpos pulados (`core/imaging/TileNoise.kt`):** antes de cada bloco, σ do ruído pela mediana do laplaciano da luminância; σ < 1,2 (8 bits) não passa pela rede. Contorno/textura só aumentam a estimativa (bloco detalhado nunca é pulado). Vale para região, foto inteira e refino automático. O relatório da IA no diagnóstico mostra "limpos pulados=N".
- **Não medido no aparelho:** tempo da região (estimativa 16 × ~0,35–0,45 s ≈ 6–7 s), quantos blocos uma foto real pula, tempo da foto inteira (~190 blocos em 12 MP sem pulos ≈ 70–90 s), economia e qualidade da passagem rosto→aparência. Calibrar `CLEAN_SIGMA` pelo diagnóstico.
- Testes: `FaceLockTest`, `SubjectTrackerTest` (+3), `SubjectTrackingSessionTest` (+1), `RegionRefineTest`, `TileNoiseTest`, `RegionRenderTest`.

---

# 0.30.0 — 4K60/8K: resposta definitiva no diagnóstico + tela poupando energia na gravação (Claude; branch `claude/nova-interface`; APK de teste build101 entregue)
- **[Baixar ZIP com app-release.apk — build101 (1101)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39283541235/artifacts/12201559572)**. Artefato 12201559572, 138687599 bytes; sha256:af1777465f1fccc7e5f5dc2f5bdca8e31a12e93371af23b15d29d0587b4082ec; expira 2026-11-18T16:24:03Z; sem release. Gatilho restaurado em 6475691.
- **Pedido do dono:** 4K/60 e 8K/30 como na câmera Samsung; filmar até uma hora sem esquentar.
- **8K:** o diagnóstico já mostrava saída máxima 4080×3060 (12,5 MP) para apps de terceiros em JPEG, YUV e RAW, e "ultra-alta resolução: false"; 8K (7680×4320, 33 MP) não é possível sem a Samsung liberar. Nova linha no diagnóstico: "Maior gravação anunciada a apps (Camera2)" (tamanhos do `MediaRecorder`).
- **4K/60:** a sessão UHD só listava fps até 30. Nova linha "Combinações de vídeo (consulta oficial do CameraX)": `CameraInfo.isSessionConfigSupported` com feature groups (CameraX 1.5+) para 4K/60, 4K/60 HLG, 4K/30 HLG e FHD/60 — só consulta, nada é vinculado.
- **Gravação longa (`core/RecordingPowerPolicy.kt`):** gravando, a janela pede 60 Hz (vídeo tem 30/60 quadros; a tela chega a 120 Hz); sem toque por 20 s (5 s com térmico ≥ moderado) a tela escurece para 15% e o primeiro toque devolve o brilho. Vídeo gravado inalterado. Medir no diagnóstico: "VÍDEO (gravando)" (corrente, temperatura, térmico) antes × depois.
- **Não medido ainda:** quanto isso economiza no S24 (o ganho real depende de quanto a tela pesa frente ao codificador e ao sensor).
- Testes: `RecordingPowerPolicyTest`.

---

# 0.29.7 — Auditoria do Noturno em zoom + fusão em paralelo (Claude; branch `claude/nova-interface`; APK de teste build100 entregue)
- **[Baixar ZIP com app-release.apk — build100 (1100)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39263835515/artifacts/12193100411)**. Artefato 12193100411, 138682920 bytes; sha256:b1d0d29bbec35f1e3c3f0a4132970edb8f2dda7958beff9a0276e25fd0edda88; expira 2026-11-18T13:57:52Z; sem release. Gatilho restaurado em 97c5ed5.
- **Auditoria completa:** `docs/pendente/AUDITORIA_NOTURNO_ZOOM.md` (caminho real da captura com números do diagnóstico, causas confirmadas × hipóteses, perdas de informação, propostas A–D para o Codex, conjunto de validação).
- **Medido no diagnóstico 0.29.5 (5×, lente 7 confirmada):** rajada 250 ms/quadro (era ~1.000 ms); rejeição da fusão 4,9% e 0,7% (era 58% na 0.29.1); 5/5 quadros; total 13,5–15,8 s, dos quais a **fusão 6,5–8,0 s em um núcleo**; heap até 391/512 MiB no alinhamento.
- **Otimização (escopo Claude, imagem idêntica):** `Engine.mergeInto` lê em série (JPEG por faixas), calcula os blocos em paralelo (`Parallel`, respeita calor/economia) e grava em série; `readAligned` = `fetchAligned` + `finishAligned`. `EngineAndroidTest.fusaoEmParaleloIgualAUmNucleo` compara pixel a pixel 1 × 4 núcleos (idênticos). JVM 653 → 428 ms (não é medida do S24; o próximo diagnóstico mede).
- **Crashes do Cinema (tombstone, 0.29.2):** `abort()` dentro de `ImageSegmenter.segment` (MediaPipe, 09:20) e `DepthEstimator.nativeRun` (ncnn/OpenMP, 09:36), ~2 s após abrir a sessão. Causa interna não identificada.
- **Nota de responsabilidade:** 0.29.2–0.29.6 mudaram qualidade (fusão, lente, SR) por pedido do dono com o plano do Codex encerrado; a partir do prompt de 19/10 o Claude volta a só auditar/propor qualidade e implementar desempenho.

---

# 0.29.6 — Noturno 10×: super-resolução multiquadro (Claude; branch `claude/nova-interface`; APK de teste build99 entregue)
- **[Baixar ZIP com app-release.apk — build99 (1099)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39261441950/artifacts/12190863062)**. Artefato 12190863062, 138680238 bytes; sha256:98cb2b79647863f108db158ddee8ab9e621b234e2d0d59b77582ff8cb8ad69ec; expira 2026-11-18T13:43:50Z; sem release. Gatilho restaurado em 420d6a1.
- Retorno do dono (0.29.5 ou 0.29.6, não informado): bandejas 5× agora com 115 mm f/3.4 (lente 5× confirmada). Mais grão e cor mais apagada que a 0.29.2 na mesma cena, com exposição idêntica (1/24 s ISO 2056, teto do Noturno 5×) e cena visivelmente mais escura. Sem diagnóstico; pedido.
- **O que muda:** no Noturno com recorte digital sobre uma tele física fixada (10× = tele 5× × 1,96; também 5× quando o recuo de foco cai na lente 3×), a montagem final usa `Engine.superRes` (amostras de todos os quadros na posição subpixel, tremor da mão) em vez de fundir e ampliar um quadro. Rajada, plano de exposição do Noturno, AF e tom do Noturno inalterados (o job continua NIGHT; a lente sai do recorte antes da rajada, como na SR da Foto). 3× e 5× na própria tele não têm recorte: seguem a fusão normal.
- **Hipótese descartada com teste:** alimentar a SR com o ruído medido entre quadros não melhora (ruído em manchas: 19,93 dB com o piso × 19,98 dB medido); a robustez pela faixa 3×3 da referência já tolera manchas. Mudança revertida para não ampliar a tolerância a fantasma.
- **Prova (sintética):** Noturno com manchas, 5 quadros: SR 19,9 dB × ampliar um quadro 17,8 dB (`SuperResTest.noturnoComRuidoEmManchasPrecisaDoRuidoEntreQuadros`).
- **Custo/risco:** processamento do 10× noturno mais longo; não testado no aparelho.

---

# 0.29.5 — Noturno fixa a lente da tele (Claude; branch `claude/nova-interface`; APK de teste build98 entregue)
- **[Baixar ZIP com app-release.apk — build98 (1098)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39260143326/artifacts/12191181501)**. Artefato 12191181501, 138680105 bytes; sha256:b274f8ae444c606653802cbe0072439e4b5412846ef708fd5a128e369727355b; expira 2026-11-18T13:34:35Z; sem release. Gatilho restaurado em d5f5624.
- **Evidência (dono, 0.29.4):** pedindo 5× no Noturno, a foto saiu com 69 mm f/2.4 — a lente 3× ampliada ~1,6×. Causa: com ISO ≥ 800 (sempre no Noturno) `zoomTo` não preferia a lente nativa e o firmware, na câmera lógica, troca a 5× pela 3× no escuro. Perde ~40% da resolução linear e nem ganha luz: para o mesmo enquadramento a pupila da 5× (≈5,5 mm) é maior que a da 3× (≈3,3 mm).
- **Correção (`core/NightLensPolicy.kt`):** no Noturno, traseira, zoom ≥ 2,5×: a lente física nativa fica fixa (3× → lente 3×; 5× e 10× → lente 5×, 10× = 5× com recorte 1,96×), também ao entrar no Noturno já com zoom. Ao sair do Noturno volta à câmera lógica (a Foto continua fixando só com luz boa). 1× e selfie inalterados.
- **Recuo:** a tele não foca perto. Se o AF da prévia ficar 2,5 s sem focar (estados 1/3/5/6, fora da captura), volta à lente automática e avisa "A tele não focou…"; a rota fica bloqueada até sair do Noturno ou tocar outra parada. Recuo antigo (lente sem quadros / sessão recusada) continua.
- **Não testado no aparelho.** Risco: prévia mais escura/ruidosa na tele (f/3.4) que na 3× ampliada; o resultado final deve ter mais detalhe. Próximo passo combinado: super-resolução multiquadro no Noturno com recorte digital (10×).
- Testes: `NightLensPolicyTest` (escolha 3×/5×/10×, fora do Noturno/selfie não muda, vigia de foco).

---

# 0.29.4 — Noturno: "pontos" da referência (Claude; branch `claude/nova-interface`; APK de teste build97 entregue)
- **[Baixar ZIP com app-release.apk — build97 (1097)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39254955381/artifacts/12187343548)**. Artefato 12187343548, 138678175 bytes; sha256:c6cf01d1760c7a42d45e0e1fe0972efcce310e7777a85f0c3cf2688a018af0de; expira 2026-11-18T12:56:49Z; sem release. Gatilho restaurado em 03e67a6.
- **RAW no Noturno (só proposta):** dono perguntou; resposta: ganha textura fina/sem halos/mais faixa dinâmica, custa cor/tom próprios (o tom aprovado muda), memória/tempo, e RAW da tele pela câmera lógica não confirmado. Próximo passo, se aprovado: sonda de RAW na tele, depois Noturno RAW opcional em 5× para A/B.
- **Pedido do dono:** Noturno o mais perto do perfeito em 3×/5×/10× e na selfie (mesmo caminho `Engine.mergeInto`; Noturno nunca usa SR). Evidência: foto 5× das bandejas (0.29.2) ampliada — grão claro isolado ("pontos") na parede e no frasco rosa; madeira com veio borrado; halo leve nas bordas.
- **Raiz dos pontos:** a fusão ancora cada pixel no quadro de referência. Um ponto claro do ISP (ruído impulsivo + nitidez da Samsung) presente só na referência fazia os outros 4 quadros discordarem; eles eram rejeitados como "movimento" e o ponto ficava com força total. Os pontos dos outros quadros sumiam; os da referência, todos, ficavam.
- **Correção (`Merge.impulseAnchor`):** um pixel da referência vira ruído só se (1) passa do 2º maior (ou fica abaixo do 2º menor) vizinho dele por mais de kσ — ponto de 1–2 px, não borda nem textura; (2) os outros quadros confiáveis concordam entre si (±kσ); (3) esse valor combina com a vizinhança. Aí a âncora passa a ser o quadro mais próximo da mediana dos outros, e a referência entra como amostra comum. Objeto em movimento ocupa área e não passa em (1); o mapa de confiabilidade continua valendo.
- **Testes:** ponto só na referência some (antes ficava 150 sobre fundo 80); ponto real em todos os quadros fica; objeto 3×3 só na referência não é apagado; objeto em movimento continua sem fantasma.
- **Não resolvido:** veio da madeira borrado e halo nas bordas vêm do próprio JPEG da câmera (alisamento e nitidez do ISP antes do app); a fusão não recria detalhe que nenhum quadro tem. Selfie noturna e 3×/10× recebem a mesma correção, mas ainda não foram fotografadas.

---

# 0.29.3 — Cinema na tele: máscara não fica pronta + pista do crash nativo (Claude; branch `claude/nova-interface`; APK de teste build96 entregue)
- **[Baixar ZIP com app-release.apk — build96 (1096)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39252222260/artifacts/12185624870)**. Artefato 12185624870, 138679410 bytes; sha256:543a97aafbfcc534f9f4853acd1e2d4fb1e3f680f2155a52b6fd0d67c3910add; expira 2026-11-18T12:35:16Z; sem release. Gatilho restaurado em e62c594.
- **Noturno encerrado pelo dono (0.29.2):** aprovado em 5× e considerado pronto para 3×/10×/frontal (mesmo caminho `mergeInto`; Noturno nunca usa SR). Diagnóstico da foto das bandejas (5×, luz de dia, 1/24 s ISO 2056): sigma espacial 2,22, entre quadros 2,38, rejeição 0,20, 5/5 quadros, inseguro ≤0,043. Nesta cena o espacial já não caía no piso; o caso escuro (onde o piso 0,5 travava a fusão) segue sem confirmação no aparelho. Mais 2 crashes nativos na 0.29.2 (09:20:45 e 09:36:26), ambos ~2 s depois de abrir sessão cinematográfica (carga do modelo de profundidade ≈2,2–2,4 s): hipótese, sem tombstone ainda.
- Retorno do dono (0.29.2, Noturno 5×, cama, mesma exposição 1/24 s ISO 2056): parede sem as manchas antigas; textura do tecido mantida; estampa azul com aspecto de "aquarela" e contorno duro no branco (acabamento depois da fusão). Sem diagnóstico: não confirma o sigma da fusão. Pedido: linhas "fusão:" e repetir a cena escura.
- **Evidência (dono, 0.29.2 build95):** Cinema a 3× (lente 6), carregando, térmico 2. Ao gravar: "Cinematográfico indisponível — a máscara ainda não está pronta". Diagnóstico: 137 quadros analisados, só 27 máscaras enviadas; 6 profundidades (~1,56 s cada). Novo crash nativo 09:20:45, 2 s depois de abrir a sessão cinematográfica.
- **Raiz da máscara:** `CinematicSceneGuard` tratava qualquer diferença média > 0,045 entre análises como "cena mudou". Na tele, o tremor da mão passa disso a cada análise: a máscara era apagada e a profundidade em voo descartada (geração nova) antes de chegar. Com profundidade de ~1,5 s, ela quase nunca era usada.
- **Correção:** só troca de cena (diferença > 0,15, `SCENE_CUT`) descarta a profundidade e apaga a máscara. Tremor/pan leve mantém a profundidade anterior até a próxima, que já roda no ritmo de cena em movimento. Custo aceito: objeto que se mexe dentro do quadro pode ficar até ~1–2 s com a máscara atrasada, em vez de ficar sem desfoque.
- **Crash nativo:** não tenho a causa. Profundidade (lock + marca de fechada) e segmentador (fechado na mesma thread da análise) foram revisados e não têm corrida visível. O diagnóstico agora extrai do tombstone (API 31+) o sinal, a thread, as .so e as funções da pilha (`core/NativeCrashHints.kt`). O próximo diagnóstico com crash diz onde foi.
- Testes: `CinematicPolicyTest` (tremor ≠ troca de cena; pistas do tombstone).

---

# 0.29.2 — Noturno na tele (raiz do ruído) + assunto errado no Cinema (Claude; branch `claude/nova-interface`; APK de teste build95 entregue)
- **[Baixar ZIP com app-release.apk — build95 (1095)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39185802925/artifacts/12158251043)**. Artefato 12158251043, 138679672 bytes; sha256:aa55dcf34f911d0d941770ed2fba744fb6106d3942c348937654be814faf4fa0; expira 2026-11-18T01:38:30Z; sem release. Gatilho restaurado em f7a4bc1.
- **Contexto:** o plano do Codex acabou; o dono passou qualidade de imagem para o Claude. Evidência: diagnóstico 0.29.1 (build94), duas fotos Noturno 5× (115 mm, f/3.4, 1/24 s, ISO 2056) com ruído em manchas e um MP4 do Cinema (bandeja, pouca luz).
- **Raiz do ruído na tele:** a fusão só usava o ruído medido pela diferença entre pixels vizinhos (`Merge.estimateNoise`, `Consistency.noiseOf`). O JPEG da tele já vem alisado pixel a pixel pelo ISP, com ruído em manchas de vários pixels: o vizinho a vizinho cai no piso (sigma 0,5 em 5× e 10×; 2,2 no 1×) e `CorrelatedNoise` descarta manchas maiores que 4 px. Com sigma 0,5, diferença acima de 2,25 níveis entre quadros contava como movimento → rejeição 58% (5×): a foto saía praticamente de um quadro só.
- **Correção:** o ruído passa a ser medido também **entre a referência e os quadros já alinhados** (1º quartil das diferenças, robusto a até ~75% de área em movimento) — é a mesma grandeza que a fusão compara. `Merge.temporalNoise` (sigma da fusão, teto 8 níveis) e `Consistency.pairNoise` (verificação de confiabilidade por quadro, teto 0,025). Só podem aumentar a tolerância, nunca reduzir; os tetos mantêm objeto em movimento fora (teste de fantasma passa).
- **Prova (sintética, não S24):** ruído em manchas de 8 px, σ=7, 5 quadros: antes sigma 0,5 e RMS 6,40→6,40 (fusão nula, igual ao campo); agora sigma 6,7, 4/4 quadros aceitos, RMS 6,40→3,05 (ideal √5: 2,86). `NightNoiseRegressionTest.ruidoEmManchasMedidoEntreQuadrosPermiteFusao` e `ruidoEntreQuadrosNaoAbreFantasma`.
- **Diagnóstico:** a linha "fusão:" agora mostra `espacial=` e `entre quadros=` e o ruído por par — o próximo diagnóstico confirma se o sigma subiu de verdade no S24.
- **Cinema, objeto inteiro desfocado nos primeiros segundos:** antes da profundidade chegar (~0,8 s após iniciar ou mexer), o vídeo usava a máscara de "pessoa" com cobertura mínima de 3%. No escuro, uma figura no pôster da prateleira de baixo passava como pessoa e tudo fora dela (inclusive a bandeja) era desfocado; com a profundidade também virava "assunto". Agora, só no vídeo, pessoa vira assunto com ≥12% do quadro ou sob o toque (`CinematicPolicy.personSubject`). Retrato foto inalterado.
- **Não corrigido / não confirmado:** (1) véu magenta em 3 quadros (t≈1,4 s) subindo de baixo: o compositor não tem caminho que gere isso; hipótese mais provável é luz da TV mudando de cena — pedir ao dono para repetir com a TV desligada. (2) Tele noturna continua com 5 quadros a ~1 s cada; mais quadros reduziriam mais ruído, mas alongam a captura na mão. (3) Nada mudou no tom do Noturno.

---

# 0.29.1 — correções: landscape cortado, crash cinematográfico, face em TV (Claude; branch `claude/nova-interface`; APK de teste build94 entregue)
- **[Baixar ZIP com app-release.apk — build94 (1094)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39178742818/artifacts/12155502989)**. Artefato 12155502989, 138679983 bytes; sha256:fe4784a32e47caafc3f8031f031015ea28b56d0ce9a3ee23d1991089c07ae637; expira 2026-11-18T00:07:38Z; sem release. Gatilho restaurado em 17e31e4.
- **Landscape "cortado":** a Activity fica travada em retrato (manifesto). Em landscape a prévia usava `FILL_CENTER` (recorte central) — o que a câmera gravava era mais largo do que o usuário via na prévia. Agora em landscape a prévia troca para `FIT_CENTER` (barras pretas em cima/embaixo, campo de visão completo). O vídeo final continua com a orientação certa (metadados de rotação no MP4).
- **Crash nativo no cinematográfico (0.28.2):** `PortraitMaskAnalyzer.close()` chamava `depthExecutor.shutdown()` sem esperar a tarefa de profundidade em voo terminar, e logo depois agendava `DepthEstimator.release()`. Se a tarefa ainda estivesse dentro do `nativeRun`, o handle nativo podia ser fechado por baixo. Agora `close()` espera até 3 s pela tarefa terminar (`awaitTermination`) antes de liberar.
- **Face na TV (desfoque no rosto em vez de no fundo):** o sensor detectava rostos na TV com score >= 50 e os usava como alvo do cinematográfico. Rostos numa tela de TV a 2–3 m são pequenos e de score baixo. Agora: score mínimo 75, tamanho mínimo 80×80 px no sensor, e validade de 600 ms (era 1 s). Rostos de TV ficam abaixo desses limiares na maioria dos cenários.
- Testes: todos os existentes continuam verdes.

---

# 0.29.0 — Nova Live: foto com movimento (Claude; branch `claude/nova-interface`; APK de teste build93 entregue)
- **[Baixar ZIP com app-release.apk — build93 (1093)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39177677299/artifacts/12154937026)**. Artefato 12154937026, 138680021 bytes; sha256:44d88d478d0e4aad3cde880117c89c823c709e57a875d274aee5c15b2479b497; expira 2026-11-17T23:51:42Z; sem release. Gatilho restaurado em 4a59407.
- **Formato:** o dono abriu na Galeria do S24 os 3 arquivos de teste (Google Motion Photo, trailer Samsung, combinado) e os três tocaram. Fica o **Google Motion Photo v1** (JPEG + XMP `GCamera:MotionPhoto` + MP4 no fim; `core/MotionPhoto.kt`), também lido pelo Google Fotos.
- **Captura:** com "Nova Live" ligado (painel de ferramentas, só na Foto), uma análise YUV ~1280×960 alimenta um codificador H.264 de hardware (6 Mb/s, quadro-chave a cada 1 s) e só os últimos ~3,2 s comprimidos ficam na memória (`camera/LiveRecorder.kt`). No disparo: 1,5 s antes + 1,5 s depois viram um MP4 (orientação do sensor).
- **Anexo:** `processing/LiveClips.kt` liga o clipe à URI final (Job → `finish`, ou foto comum → `onImageSaved`) e anexa quando os dois estão prontos. Toda regravação por `MediaWriter.write` (acabamento, IA, revisão) anexa de novo depois do EXIF; tudo sob o mesmo lock (nunca lê JPEG pela metade).
- **Limites honestos:** (1) foto com **Ultra HDR** (gainmap, MPF) fica **sem** movimento nesta versão — o Diagnóstico diz "Nova Live não anexado: Ultra HDR (MPF)"; (2) frontal espelhada, RAW, alta resolução nativa e extensões do fabricante: sem Nova Live; (3) o vídeo é ~1280×960 a até 30 fps (não 4K) e sem som; (4) **gasta bateria** enquanto ligado (codificador sempre ativo) — desligado por padrão; (5) se a sessão recusar a análise extra, o app desliga o Nova Live na sessão e avisa. Não testado no aparelho.
- Diagnóstico: linha "Nova Live: …" (tamanho do vídeo, ms antes do disparo, ou motivo de não anexar).
- Testes: `MotionPhotoTest` (XMP após SOI, MP4 no fim, offsets, recusa de Ultra HDR/XMP existente).

---

# 0.28.2 — Cinema no estilo do iPhone (Claude; branch `claude/nova-interface`; APK de teste build92 entregue)
- **[Baixar ZIP com app-release.apk — build92 (1092)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39177239844/artifacts/12154534793)**. Artefato 12154534793, 138669692 bytes; sha256:ecfd9e044a092ecba0bc3c9a9a8980dff419d47822791ccfe9e493ad9c57422c; expira 2026-11-17T23:42:30Z; sem release. Gatilho restaurado em fefba2a.
- Referência (pesquisa): o Cinematográfico do iPhone detecta rostos e muda o foco sozinho, faz "rack focus" suave ao tocar noutro assunto e permite mudar foco/abertura depois (guarda a profundidade junto do vídeo).
- **Rack focus:** tocar noutro assunto com a cena parada não apaga mais o desfoque para recalcular: a profundidade da cena continua válida, só o plano de foco muda, e a máscara anda até o novo assunto em ~1–2 s (`CinematicSceneGuard.selectionOnly`, `RACK_KEEP` 0,7 por quadro). Movimento da cena continua reiniciando como antes.
- **Foco automático no rosto:** sem toque, o maior rosto detectado pelo próprio sensor (modo SIMPLE, sem CPU do app) vira o assunto; some 1 s depois do último rosto. Detecção ligada no cinematográfico mesmo com "Mostrar rostos" desligado.
- **Abertura (f/) ao vivo** já valia no cinematográfico; continua ajustável durante a gravação.
- **Não feito:** mudar foco/abertura DEPOIS de gravar (exige gravar o mapa de profundidade junto do vídeo e um editor; fora do escopo de hoje).

---

# 0.28.1 — correções do teste do 0.28.0 (Claude; branch `claude/nova-interface`; APK de teste build91 gerado, substituído pelo 0.28.2)
- [ZIP build91 (1091)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39176712295/artifacts/12155010938): artefato 12155010938, 138668870 bytes, sha256:bb026a7bb9278a18c7ce6d482338eb72207379f601ff3a1935e461dc4c7844ef, expira 2026-11-17T23:35:03Z; sem release. Gatilho restaurado em bf0bedd.
- **Teste do dono (0.28.0):** HDR funcionou (Diagnóstico: HLG anunciado para UHD/FHD/HD/SD; gravados FHD HDR/60 e 4K HDR/30 em HEVC, 59,7 e 30,0 fps). 4K/60 ainda mostrou o cartão de erro na 1ª vez (a recusa só passou a ser lembrada depois — no log, as trocas seguintes em 4K já pularam o 60). CINEMA: "A máscara ainda não está pronta ou perdeu validade" numa cena sem pessoa.
- **Causa do Cinema:** sem pessoa, a máscara depende da profundidade (~1,4 s de parede no S24). Ela rodava dentro do quadro de análise: esse quadro passava de 650 ms e a máscara era apagada ("análise atrasada"), e entre cálculos a máscara anterior vencia (750 ms). Resultado: quase nunca "pronta". **Correção:** no cinematográfico a profundidade roda em thread própria (`nova-cine-depth`); a análise continua a cada quadro e a profundidade nova entra quando termina; movimento da cena descarta a que está em curso. Foto/Retrato inalterados (continuam no fluxo antigo).
- **Gravar no Cinema sem máscara pronta:** aguarda até 4 s ("Preparando o desfoque…") e grava sozinho quando fica pronta; só depois disso mostra o diálogo de vídeo comum.
- **4K/60:** na recusa definitiva da sessão (já sem EIS e sem assinatura), troca para a maior taxa aceita (4K/30) com aviso, em vez de deixar só a prévia com o cartão.
- Nova Live: gerados 3 arquivos de teste (Google Motion Photo, trailer Samsung SEF, combinado) para descobrir qual a Galeria reconhece antes de implementar a captura; fora do Git (scratchpad).

---

# 0.28.0 — pendentes da reformulação + vídeo escuro na tele (Claude; branch `claude/nova-interface`; APK de teste build90 entregue)
- **[Baixar ZIP com app-release.apk — build90 (1090)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39174785768/artifacts/12153652995)**. Mesma assinatura permanente. Artefato 12153652995, 138663625 bytes; digest do ZIP sha256:f257c8949a18696439d5da85f2bf0b612b02eb3c9b089380ca8799cc3cbe62d9; expira 2026-11-17T23:10:05Z. [Actions 39174785768](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39174785768) com sucesso; publicação pulada (nenhuma release nova). Gatilho restaurado em 2f5e964 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Vídeo escuro em 3×/5×/10× (print do dono, FHD/60):** a 60 fps fixos cada quadro tem no máximo 1/60 s; a tele (f/2.4 e f/3.4) recebe 2–4× menos luz que a principal e, num quarto escuro, o ISO já bate no teto (diagnóstico: ISO 2781 na principal). O "Quadros automáticos com pouca luz" (15–30) só valia para 30 fps. Agora vale também para 60: faixa **15–60** quando a sessão oferece (S24 anuncia [15, 60]); com luz boa continua 60. 24 fps fica fixo. Chip mostra a faixa real ("15–60 fps alvo"). `ModeResolver.autoVideoFps(ranges, target)`. **Não é ganho de qualidade comprovado:** mais luz por quadro, ao custo de movimento menos fluido no escuro. Codex: se preferir outra política (ex.: tele no escuro usar recorte da principal), é aqui.
- **4K/60 recusado:** a sessão do S24 não oferece 60 em UHD (FPS da sessão sem [60, 60]); antes o app tentava sem EIS, sem assinatura e terminava em "não aceito". A recusa da sessão base é registrada (`NovaSettings.videoRefused`, por câmera/qualidade/fps e build do Android); o seletor de fps não oferece mais, e trocar de FHD/60 para 4K escolhe 30 com aviso.
- **Vídeo HDR (HLG 10-bit):** só quando `Recorder.getVideoCapabilities().supportedDynamicRanges` tem HLG para a qualidade escolhida (registrado no Diagnóstico › Vídeo: "Faixas dinâmicas anunciadas"). Opção "HDR vídeo" no painel de ferramentas e em Ajustes (depois de anunciado). Sem assinatura no vídeo com HDR (desenho GL 8-bit). Recusa → SDR com aviso (`videoHdrBlocked`). Não testado no aparelho.
- **Modo CINEMA** na faixa principal (Retrato · Foto · Vídeo · Cinema · Noturno · Pro): reaproveita a sessão do vídeo cinematográfico do Retrato (FHD/30, sem EIS, profundidade ao vivo), agora como modo próprio; liga o cinematográfico mesmo com a preferência do Retrato desligada (`NovaCamera.cinemaMode`). Enum `VIDEO_RETRATO` virou "CINEMA" (antes indisponível). Mesmo fallback de antes quando a máscara não está pronta.
- **Retrato:** desfoque mostrado como **abertura simulada** (f/16 → f/1.4; mesma intensidade 0–100%, só o rótulo). **Iluminação Natural / Fundo escuro** (painel de ferramentas): fundo ×0,42 (~−1,25 EV) pela mesma máscara do desfoque, só na foto final (`Engine.darkBackgroundInPlace`, `Portrait.darkenBackgroundTile`); a prévia NÃO mostra o escurecimento (avisado no toque).
- **Contorno ao toque (MagicTouch, Apache-2.0 confirmado no model card):** `assets/magic_touch.tflite` (6,2 MB). Uma inferência por toque na prévia reduzida a 512 px, thread própria; contorno some em ~1,6 s; sem contorno com cobertura <1% ou >60% (objeto pequeno/distante está fora do uso previsto). Modelo sai no onStop. Ajuste "Contorno ao tocar".
- **Bug (área Codex) corrigido:** `onTrimMemory` com `>= RUNNING_LOW` cancelava o acabamento do vídeo e o rastreio em UI_HIDDEN/BACKGROUND (abrir a galeria). Agora só RUNNING_LOW/CRITICAL e ≥ MODERATE.
- Fora: Nova Live e foto com movimento Samsung (aguarda teste do arquivo na Galeria).
- Testes: `CoreLogicTest` (Cinema, autoVideoFps 60), `DarkBackgroundTest`, `ContourMaskTest`.

---

# 0.27.4 — zoom do disparador em degraus (Claude; branch `claude/nova-interface`; APK de teste build89 entregue)
- **[Baixar ZIP com app-release.apk — build89 (1089)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39172294366/artifacts/12152274583)**. Mesma assinatura permanente. Artefato 12152274583, 132873407 bytes; digest do ZIP sha256:914bd8a7bf139e3476ee78755abaf5bdc6c7cb70b6c04c5b9a55238a786f0bad; expira 2026-11-17T22:35:46Z. [Actions 39172294366](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39172294366) com sucesso; publicação pulada (nenhuma release nova). Gatilho restaurado em c4af91c (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Teste do dono com 0.27.3:** barra, pinça e números (3/5/10) ok no vídeo; arrastar o disparador ainda "contava 1.1, 1.2…, só um pouco mais rápido". A causa não era vazão: o arraste do disparador era contínuo de propósito (curva exponencial de 0.27.1), e o dono quer a mesma resposta dos números.
- **Mudança:** cada ~26 dp (~4 mm) de arraste sobe/desce UM degrau de `ShutterZoom.ladder` (paradas reais + inteiros 1–10 + 15/20/30/50/100, dentro dos limites; inteiro a menos de 8% de uma parada real sai). Cada degrau chama `zoomTo(z, allowLensSwitch = false)`, a mesma animação do toque numa parada, com vibração curta. Zoom fora da escada (2,4×) vai para o vizinho (3× ou 2×). Não troca de câmera no meio do gesto (sem religar sessão).
- Barra e pinça continuam contínuas (não mudaram). Nenhum pixel/processamento alterado.
- Testes: `core/ShutterZoomTest` reescrito (degraus inteiros, vizinho, limites, sem degrau duplicado).

---

# 0.27.3 — zoom do vídeo sem degraus (Claude; branch `claude/nova-interface`; APK de teste build88 entregue)
- **[Baixar ZIP com app-release.apk — build88 (1088)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39170090511/artifacts/12152013924)**. Mesma assinatura permanente. Artefato 12152013924, 132871914 bytes; digest do ZIP sha256:a1bf46f580576a1b769bbe515d06d55215c614c85db2034372752ed9020196e4; expira 2026-11-17T22:05:34Z. [Actions 39170090511](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39170090511) com sucesso; publicação pulada (nenhuma release nova). Gatilho restaurado em 266a882 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).

- **Diagnóstico 0.27.2 + gravação de tela do dono ("modo vídeo ainda com gargalo"):** no vídeo (FHD/60, EIS ligada) cada pedido de zoom levava **125–153 ms** para ser confirmado (`aplicado em …`), contra 0–40 ms na foto. A vazão era "no máximo um pedido em voo, o próximo só após a confirmação" (`ZoomRequestGate`), então o zoom do vídeo andava em ~7 passos por segundo: 1,09 → 1,23 → 1,47 → 1,81… visível como degraus. A demora vem da estabilização eletrônica (a câmera confirma o pedido quadros depois).
- **Correção:** no vídeo, se o pedido anterior ainda não foi confirmado após 33 ms, o mais novo é enviado mesmo assim (a câmera descarta o antigo); a confirmação/cancelamento de um pedido substituído não libera a vazão (número de sequência). Foto mantém a regra antiga (confirmação rápida). Teste `videoZoomDoesNotWaitForSlowConfirmation`.
- Painéis Ferramentas/Mais modos fecham ao disparar ou começar a gravar (na gravação de tela o painel ficou aberto sobre a prévia).
- Esperado (não medido): zoom do vídeo acompanhando o dedo a ~30 passos/s; a latência própria da câmera (~130 ms com EIS) continua.

---

# 0.27.2 — pendentes da interface e da confiabilidade (Claude; branch `claude/nova-interface`; APK de teste build87 entregue)

- **[Baixar ZIP com app-release.apk — build87 (1087)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39167577996/artifacts/12151168880)**. Mesma assinatura permanente. Artefato 12151168880, 132871045 bytes; digest do ZIP sha256:bfcd81a8ab8e4635b4427881aaf4c1859ed526cf879960ddc5046af920f955bb; expira 2026-11-17T21:29:55Z. [Actions 39167577996](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39167577996) com sucesso; publicação pulada (nenhuma release nova). O build86 foi cancelado para incluir a correção do vigia. Gatilho restaurado em 98c9406 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Zoom do disparador preciso** (relato do dono: "no 3× não pula pro 4 ou 5, vai 3.1, 3.2…"): o alvo é calculado a partir do zoom do início do gesto, 110 dp = ×e (3× → 5× em ~1 cm), e gruda nas paradas reais da lente (±6%) com vibração curta (`core/ShutterZoom`, testes).
- **Foto protegida durante o processamento:** `processing/ProcessingService` (serviço curto em primeiro plano, Android 14+, notificação mínima "Finalizando foto") enquanto há foto na fila — abrir a galeria não deixa o sistema encerrar o app no meio. Cada sessão ganha a marca `interrupted` no início e a perde ao fim; se o processo morrer, na próxima abertura o original salvo vai para a galeria como `NOVA_<id>_recuperada.jpg`, uma vez, com aviso (`ProcessingQueue.recoverInterrupted`). AUTO não precisa (a foto já estava na galeria).
- **Miniatura imediata (fotografia progressiva, fase 1):** o quadro da câmera reduzido (~1/16, thread `nova-thumb`, ~30 ms) aparece na miniatura logo após a captura, com anel de progresso até o fim; tocar nela enquanto processa avisa que a galeria recebe ao terminar (a galeria ainda só recebe a foto final — sem publicação antecipada).
- **Toque do Retrato expira com o foco:** a prévia usa o ponto tocado só enquanto o ponto de foco do usuário vale (Ajustes › Manter foco por toque, padrão 4 s + retorno); depois volta ao modo "pessoa sem toque" e para de recalcular profundidade.
- **Rostos (etapa 2 da interface):** detecção de rostos do próprio sensor (modo simples, `STATISTICS_FACE_DETECT_MODE`), desenhada só com os cantos; some quando há um assunto escolhido. Ajuste "Mostrar rostos" (padrão ligado). Fora do Pro e do Noturno com exposição manual.
- **Provável causa raiz do P0 "prévia trava / Tom e Temperatura somem até reabrir o app":** o vigia da prévia (`NovaCamera.watchTick`) não sabia que o app tinha ido para segundo plano. Ao abrir a galeria (ou qualquer saída), o CameraX fecha a câmera de propósito; o vigia via "sem quadros", registrava prévia parada, reabria a câmera com o app escondido (sem efeito), repetia a cada ~11 s (os **trios** no StallLog, sempre com efeito=false análise=false, em FOTO/NOTURNO/RETRATO e na frontal depois de ver o Retrato na galeria) e, pela escalada, desligava o desfoque e depois Tom/Temperatura para a sessão. **Correção:** marca `visible` (onStart/onStop) — fora da tela o vigia não conta nem reage; ao voltar vale o prazo de primeiro quadro (10 s). Confirmar no aparelho: abrir a galeria várias vezes e voltar; o StallLog não deve ganhar entradas e Tom/Temperatura devem continuar.
- **Zoom no vídeo (diagnóstico 0.27.1):** cada comando de zoom leva ~120–150 ms até ser aplicado pela câmera (HAL), mais a suavização própria do vídeo (110–200 ms, de propósito, para a imagem não tremer). A sequência 2,73 → 2,56 em passos de 0,01–0,03 é o dedo derivando com a curva antiga do disparador (240 dp por ×e); em 0.27.2 o disparador usa 110 dp e encaixa nas paradas. A latência da câmera não é do app.
- Não verificado no aparelho: posição dos rostos na frontal e com zoom (usa a mesma transformação do toque), aparência da notificação do serviço.

---

# 0.27.1 — zoom arrastando o botão de disparo (Claude; branch `claude/nova-interface`; APK de teste build85 entregue)

- **[Baixar ZIP com app-release.apk — build85 (1085)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39162809572/artifacts/12149497794)**. Mesma assinatura permanente. Artefato 12149497794, 132857718 bytes; digest do ZIP sha256:4287c9d1382141b9b2a3d1ef8ea764a73e8e3f8dd8709dd064254b5733872f0a; expira 2026-11-17T20:25:36Z. [Actions 39162809572](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39162809572) com sucesso; publicação pulada (nenhuma release nova). Gatilho restaurado em 4d46379 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- Pedido do dono: arrastar o disparador para cima aproxima, para baixo afasta, em todos os modos (antes só durante a gravação rápida). Soltar depois de arrastar não fotografa; toque simples fotografa como antes; segurar parado continua iniciando a gravação rápida. Mesma curva da gravação rápida (240 dp ≈ ×e) e mesmos limites de zoom da câmera. Teste `ShutterZoomTest`.

---

# 0.27.0 — Interface Nova, etapa 1 (Claude; branch `claude/nova-interface`; APK de teste build84 entregue)

- **[Baixar ZIP com app-release.apk — build84 (1084)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39159177607/artifacts/12148223515)**. Mesma assinatura permanente. Artefato 12148223515, 132857618 bytes; digest do ZIP sha256:46b0487c4cd3379eeeac2102611db00f86a688f78b4bda6a46a9d01fa0c6f070; expira 2026-11-17T19:40:18Z. [Actions 39159177607](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39159177607) com sucesso; **publicação pulada** (condição do passo estendida a este branch só no commit do gatilho f5180f3; o run 83 sem essa condição foi cancelado antes de publicar — nenhuma release nova). Gatilho restaurado em eba9f72 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Proposta aprovada pelo dono** (canvas de mockups, 8 telas). Etapa 1 = organização da tela principal, sem mexer na câmera nem no processamento.
- **Topo enxuto:** só flash (esquerda) e Ferramentas (direita); no vídeo, resolução e fps também à direita. Centro livre (câmera frontal do S24 fica no meio).
- **Painel Ferramentas** (`ui/ToolsPanel.kt`): Timer, Proporção, Resolução, HDR, RAW, Mais modos, Ajustes. São os **mesmos botões** de antes (mesma ação/estado); só mudaram de lugar e ganharam rótulo. O que não se aplica ao modo/lente some.
- **Faixa de modos:** Retrato · Foto · Vídeo · Noturno · Pro (+ MAIS / modo extra ativo). Noturno e Pro saíram do MAIS. "Cinema" ainda não é modo (o cinematográfico continua em Ajustes › Vídeo).
- Ícones novos desenhados no mesmo padrão (`TOOLS`, `MODES`). Render da tela (Robolectric, `MainScreenRenderTest`) ganhou o caso "ferramentas".
- Limitação conhecida do render de teste (já existia antes): a barra de zoom aparece sobre a faixa de modos no PNG; no aparelho ela fica na faixa entre a prévia e os controles.

---

# 0.26.15 — travamento da prévia ao abrir o Retrato: vigia espera a carga do modelo (Claude; APK de teste build82 entregue)

- **[Baixar ZIP com app-release.apk — build82 (1082)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39155946626/artifacts/12147286475)**. Mesma assinatura permanente. Artefato 12147286475, 132854397 bytes; digest do ZIP sha256:8b276082e602fc8b140d790b5ea2a86827df71dcc80da4a0bc672b46eab7aaee; expira 2026-11-17T18:57:49Z. [Actions 39155946626](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39155946626) com sucesso; publicação skipped. Fonte do app 42f2f4e (CI 39154941357 aprovado) + docs; fonte do APK 6d1140d; gatilho restaurado em 05990ca (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Sintoma (relato do dono + StallLog 0.26.8–0.26.13):** ao abrir o Retrato com desfoque ao vivo, a prévia para; o vigia reabre a câmera, desliga a análise do desfoque e, na 2ª parada em 1 min, desliga Tom/Temperatura **na sessão inteira** (`portraitPreviewFailed`) — eles só voltam reabrindo o app. Registro "+5004 ms prévia sem resultados; reabrindo" logo após ligar = ~2 s de quadros + 3 s (`STALL_PREVIEW_MS`).
- **Hipótese (não confirmada):** a primeira carga da profundidade (ncnn Vulkan: instância + pipelines, 2,2–3,3 s medidos) começa ~2 s após ligar, quando a análise ao vivo pede o modelo; com a GPU ocupada a prévia GL deixa de consumir quadros e o HAL para de entregar resultados. MediaPipe roda em CPU (`Delegate.CPU`), então não é ele.
- **Mudança:** `core/ModelLoadWindow` marca a carga do modelo de profundidade (`DepthEstimator.Session.open`). Durante a carga e 2 s depois, o vigia (`NovaCamera.watchTick`) **espera em vez de reabrir** (limite 10 s; câmera travada de verdade ainda é recuperada). Sem efeito na foto.
- **Medição para confirmar/descartar:** diagnóstico ganha "Carga de modelos × prévia: N cargas · M com prévia sem quadros · S s sem quadros durante carga/carência"; cada linha do StallLog ganha "modelos: … carregando há X ms / última carga terminou há Y ms". Se M = 0 e as paradas continuarem, a causa é outra.
- **Resultado 0.26.15 (build82):** traseira — abrir direto no Retrato + 3 trocas Foto↔Retrato: **nenhuma parada**. Carga da profundidade na prévia: 1616 ms com **0 s sem quadros** → **hipótese da carga descartada** (a prévia não parou durante a carga; o vigia não precisou esperar). O ramo de espera fica (inofensivo, só atua durante carga); a medição fica.
- **Paradas restantes: câmera frontal** (lente 1), +86,9 s e +117,5 s, ambas com efeito=false análise=false e 85–115 s após qualquer carga — coincidem com os Retratos frontais (#3/#4). Mesmo padrão de 0.26.13/0.26.14 (efeito=false análise=false, em sequência de 11 s). Nova hipótese (não verificada): após a captura JPEG na frontal a prévia não volta a entregar resultados e só um rebind recupera. Próxima medição: registrar no StallLog o tempo desde a última captura e a câmera.
- Retrato: mediana **5,0 s** (4 fotos); IA dispensada sem carregar (ok); térmico 0, bateria 33,9–35,7 °C (dono: "não está esquentando como antes").
- Observação: depois de um toque, o toque fica valendo até trocar de modo, então a prévia volta a calcular profundidade (0 quadros "só pessoa" neste teste; `nova-portrait-m` 35,7% numa janela de 10 s com reenquadramento). Possível ajuste futuro: o toque expirar após alguns segundos parado.

---

# 0.26.14 — bateria: última etapa (Claude; APK de teste build81 entregue)

- **[Baixar ZIP com app-release.apk — build81 (1081)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39154343920/artifacts/12146122862)**. Mesma assinatura permanente. Artefato 12146122862, 132852886 bytes; digest do ZIP sha256:d27f7797728786f1fc606c3537afcdccd66d805b4421119a9cb3068553acf2b5; expira 2026-11-17T18:35:18Z. [Actions 39154343920](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39154343920) com sucesso; publicação skipped. Fonte do app 6fd2815 (CI 39154336359 aprovado); fonte do APK a41ba57; gatilho restaurado em dd465f1 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Diagnóstico 0.26.13:** thread da análise **33% → 8%** (espera ativa do OpenMP confirmada e corrigida). Retrato com desfoque parado **58,4%** (0.26.7: 128%; −55%), 71,9% logo após um toque; PSS 431–463 MiB. Restante não é mais a profundidade (~5% parado, ~15% após toque): ~46 pontos espalhados entre desenho do efeito GL (~9%), binder (~9%), fluxo de análise CameraX (~7–9%), MediaPipe (~8,5%) e interface (~8%) — custo estrutural de ter o desfoque ao vivo.
- **Profundidade menor só na prévia: descartada.** O modelo é exportado com entrada fixa 392 (`tools/depth/export_ncnn.py`, pnnx); exigiria segundo modelo (~+50 MB APK/memória, desfaz 0.26.12) para ganhar ≤4–5 pontos.
- **Mudanças (só a prévia e o trabalho inútil; foto final idêntica):**
  1. `core/LivePortraitPolicy.personOnly`: pessoa ≥ 8% da cena (sai abaixo de 5%) e nenhum toque → prévia usa só a máscara de pessoa, sem profundidade. Planta/objeto ou toque → profundidade como antes. Diagnóstico: "profundidade dispensada (pessoa sem toque): N quadros".
  2. `LivePortraitPolicy.lowPower` (economia de energia ou bateria ≤ 15% fora do carregador): análise a ≤ 3 Hz e profundidade só por mudança de cena ou a cada 60 s (normal 15 s).
  3. `core/DenoiseSkip` + `NcnnDenoiser`: depois de uma projeção que não coube no orçamento (12 MP: 192 × 353 ms ≈ 68 s > 60 s), o mesmo tamanho/orçamento é dispensado **sem carregar o modelo** por 30 min (antes: 2,2 s de carga + 3 blocos + ~44 MB em toda foto para desistir).
- Estimativa (não medida): Retrato com pessoa parado ~54% (−8%), após toque ~57% (−21%); por Retrato com IA ligada ~27% menos trabalho. Planta/objeto sem mudança.
- **Resultado 0.26.14 (diagnóstico build81; parte com câmera frontal, aparelho frio 30–32 °C, térmico 0 — não é A/B limpo):** Retrato com desfoque **33,7%** (0.26.13: 58,4%; 0.26.7: 128%). Profundidade dispensada em **323 de 325** quadros (pessoa sem toque); `nova-portrait-m` 8,3% → **2,3%** (≈ −6 pontos atribuíveis a esta mudança; o resto da queda vem de câmera frontal/aparelho frio: CameraX 6,8% → 2,0%, efeito GL 8,8% → 3,5%). IA: "dispensada sem carregar" na 2ª foto (confirmado). Retrato 7,4 s (profundidade recarregada, 2,4 s, após > 60 s sem uso) e **5,1 s**.
- Parada de prévia em +164 s (StallLog 15:48:10, efeito=false análise=false) logo após as fotos, quando a foto recarregou a profundidade — compatível com a hipótese do 0.26.15, mas a parada sem efeito/análise ativos indica que não é só o efeito GL. O 0.26.15 registra qual carga estava em curso.
- **Frente de bateria encerrada.**

---

# 0.26.13 — bateria: espera ativa do OpenMP desligada em toda inferência, não só no carregamento (Claude; APK de teste build80 entregue)

- **[Baixar ZIP com app-release.apk — build80 (1080)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39149596567/artifacts/12144698246)**. Mesma assinatura permanente. Artefato 12144698246, 132851566 bytes; digest do ZIP sha256:9eb9d15e885b17e519332438404364310eba65d6b97ab47b06ec2af8a986ad1b; expira 2026-11-17T17:34:28Z. [Actions 39149596567](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39149596567) com sucesso; publicação skipped. Fonte do app ba4abcc (CI 39149582242 aprovado); fonte do APK 70bd341; gatilho restaurado em e2cd0a9 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Diagnóstico 0.26.12 (o que melhorou, medido):** 1º Retrato com prévia ligada **10,7 s → 8,1 s**; profundidade "carga=0ms (modelo já carregado)" na 1ª foto — sessão compartilhada confirmada. **PSS 444–559 MiB** (era até ~760 MiB). Profundidade ao vivo: 27 cálculos em 165 s (0,16/s; era 0,25/s) mesmo com 30 s de reenquadramento; CPU própria da etapa 2,5% (era 6,3%).
- **O que não melhorou:** CPU do Retrato 74,7% (0.26.10: 65%). Não é A/B limpo (este teste incluiu 30 s em movimento e as 2 fotos dentro da janela; térmico 1; bateria 15%), mas a thread da análise subiu para 33% enquanto as etapas medidas nela somam 7,6% → ~25% em threads auxiliares OpenMP.
- **Causa provável (não confirmada no aparelho):** `ncnn::set_kmp_blocktime(0)` só era chamado em `nativeLoad`, e o blocktime do OpenMP vale para a **thread que chama**. Com a sessão compartilhada (0.26.12), a inferência roda em threads que não carregaram o modelo (análise ao vivo ou fila de fotos) com a espera ativa padrão (~200 ms girando por trecho paralelo). As threads do pool OpenMP são reaproveitadas entre threads chamadoras e mantêm o nome de quem as criou, por isso o custo da foto também pode aparecer em "nova-portrait-m".
- **Correção:** `set_kmp_blocktime(0)` no início de cada `nativeRun` (profundidade e NAFNet). Resultado da rede idêntico.

---

# 0.26.12 — bateria/tempo: um só modelo de profundidade para a prévia e a foto (Claude; APK de teste build79 entregue; inclui 0.26.11)

- **[Baixar ZIP com app-release.apk — build79 (1079)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39147041905/artifacts/12143679020)**. Mesma assinatura permanente. Artefato 12143679020, 132851523 bytes; digest do ZIP sha256:3d9c5824e18963d0a6e7c6fdf3e9fdf8c78e405f4dcfdaf08b2ba980c49c3433; expira 2026-11-17T17:00:34Z. [Actions 39147041905](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39147041905) com sucesso; publicação skipped. Fonte do app 67fc611 (CI 39146832594 aprovado; 0.26.11 c4fd20f CI 39146478679 aprovado); fonte do APK 698a9a6; gatilho restaurado em db184b2 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Diagnóstico 0.26.10 + relato do dono (1º Retrato com prévia do desfoque ligada, 2º sem):** 1º Retrato 10,7 s com carga de profundidade 3277 ms; 2º 7,3 s com 635 ms (reaproveitado). Causa: a prévia ao vivo abria a **sua própria** sessão de Depth Anything e a foto outra — modelo duas vezes na memória (PSS ~760 MB) e o 1º Retrato pagava a carga mesmo com o modelo já carregado pela prévia.
- **Correção:** `DepthEstimator.shared(context)` — a análise ao vivo e a foto usam a mesma sessão. A prévia não fecha mais a sessão; ao sair do Retrato agenda a liberação por ociosidade (60 s, `ProcessingQueue.scheduleDepthRelease`); calor SEVERO libera na hora. `Session.run`/`close` sincronizados: sessão liberada no meio devolve null (a prévia busca de novo no próximo cálculo).
- Esperado (não comprovado): 1º Retrato com prévia ligada ~2,5 s mais rápido e uma cópia a menos do modelo na memória. Sem mudança de imagem.

---

# 0.26.11 — bateria: profundidade ao vivo só quando a cena muda (Claude; fonte em validação)

- **Diagnóstico 0.26.10 (prévia do desfoque ativa, parado, térmico 0):** Retrato **95% → 65%** do processo; thread da análise **60% → 27%** (fim da espera ativa do OpenMP confirmado). Por etapa (CPU da própria thread): cópia 1,4%, segmentação 0,9%, **profundidade 6,3% + ~17% em threads auxiliares OpenMP** (27,3% − 10,7% das etapas), máscara 2,1%. Profundidade: 23 cálculos em 93 s, ~0,9 s de CPU e 0,87 s de parede cada.
- **Correção:** na prévia da foto, a profundidade só é recalculada quando o enquadramento mudou desde o último cálculo (luminância média por bloco > 0,045, mesma régua do vigia do cinematográfico), quando o toque mudou, ou a cada 15 s (`core/DepthRefreshPolicy`). O cinematográfico não muda (grava a máscara; Codex). Câmera parada → quase nenhum cálculo de profundidade; ao reenquadrar, o próximo cálculo segue as regras de antes.
- Esperado (não comprovado): Retrato parado com desfoque perto de ~40%; o resto é câmera/compositor/rastreamento. Restante da análise sem profundidade ≈ 5%.

---

# 0.26.10 — bateria: análise ao vivo do Retrato medida por etapa; ncnn sem espera ativa (Claude; APK de teste build78 entregue)

- **[Baixar ZIP com app-release.apk — build78 (1078)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39144239874/artifacts/12143221335)**. Mesma assinatura permanente. Artefato 12143221335, 132851127 bytes; digest do ZIP sha256:5891955c7d02e153e79d95d2d418c8247f8554b8f913f88a2f2eb88c758c5f37; expira 2026-11-17T16:25:13Z. [Actions 39144239874](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39144239874) com sucesso; publicação skipped. Fonte do app eb9b71a (CI 39143628291 aprovado); fonte do APK 93ef47b; gatilho restaurado em 25ef88e (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Diagnóstico 0.26.9 (prévia do desfoque ATIVA, 30 quadros/s, térmico 0):** Retrato 95% → **thread da análise ("nova-portrait-m") 60%**; MediaPipe ("Thread-#") caiu de ~75% para **6%** (modelo leve confirmado); compositor GL 5%. Retrato sem a prévia: 12%. **Profundidade reaproveitada confirmada:** 2º e 3º Retratos com 628–676 ms (antes 2,3–2,5 s); mediana do Retrato 6,2 s.
- **Suspeita principal dos 60%:** a profundidade ao vivo (Depth Anything, ncnn com 4 threads OpenMP, que herdam o nome da thread da análise). O OpenMP deixa as threads **girando ~200 ms** depois de cada trecho paralelo; com inferências espaçadas isso é CPU desperdiçada. Correção sem efeito no resultado: `ncnn::set_kmp_blocktime(0)` na profundidade e no NAFNet.
- **Medição por etapa:** novo bloco "Análise ao vivo do Retrato — CPU por etapa" (cópia/escala/giro, segmentação, profundidade, plano/envio), em CPU da própria thread (`core/LiveAnalysisCpu`). A diferença para o total da thread no bloco "CPU por thread" = threads auxiliares (OpenMP).
- **Bug relatado (Tom/Temperatura somem até reabrir o app):** não é da interface. A prévia trava ao abrir o Retrato (0.26.8 e 0.26.9, efeito e análise ativos) e a recuperação desliga a prévia do desfoque e depois o efeito de Tom/Temperatura **para a sessão inteira** ("pausados nesta sessão"). Causa raiz = travamento da prévia (P0 do Codex). Registrado para o Codex.

---

# 0.26.9 — bateria: profundidade não é mais descartada ao sair para a galeria (Claude; APK de teste build77 entregue)

- **[Baixar ZIP com app-release.apk — build77 (1077)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39141521326/artifacts/12142415588)**. Mesma assinatura permanente. Artefato 12142415588, 132853716 bytes; digest do ZIP sha256:6f53cd030da8d0c88b37b20157a4ef496e631b2bd0050700ed7cd722d3806688; expira 2026-11-17T15:46:18Z. [Actions 39141521326](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39141521326) com sucesso; publicação skipped. Fonte do app a0f4c4a (CI 39140925891 aprovado); fonte do APK bd1c03f; gatilho restaurado em a600428 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Leitura do diagnóstico 0.26.8:** Retrato parado **12,1%** (era 128%), Foto 12,9% (era 26%). **Não é A/B limpo:** a prévia do desfoque **não estava rodando** (compositor 0 quadros; prévia travou ao abrir o Retrato e a recuperação desligou efeito e análise), aparelho frio (térmico 0, era 2) e prévia a 15 quadros/s (era 30; cena mais escura, ISO 486 — a "perda estimada" de 751 conta isso, não é perda real). Mostra que **sem a prévia do desfoque** Retrato ≈ Foto; o ganho do modelo leve **com** a prévia ativa ainda precisa ser medido.
- **Bug achado:** profundidade recarregou nos 2 Retratos seguidos (carga 2,5 s e 2,3 s). Causa: `onTrimMemory` tratava `TRIM_MEMORY_UI_HIDDEN` (20) como pressão de memória (`level >= RUNNING_LOW`) — isso chega toda vez que a pessoa sai para a galeria — e liberava o modelo (o diagnóstico mostra "Rastreamento: Memória limitada"). Agora o modelo de profundidade só sai por 60 s sem uso, calor SEVERO, `RUNNING_CRITICAL` ou `COMPLETE`.
- **Para o Codex (não alterado aqui):** o mesmo `level >= RUNNING_LOW` também para o rastreamento e **cancela o acabamento de vídeo** quando a pessoa só sai para a galeria (UI_HIDDEN). Provável bug; avaliar.
- Tempos (0.26.8, frio, 4 núcleos): Retrato 1× 7,5 s e 6,8 s (antes ~9,3–12,5 s), HDR 6,1 s.

---

# 0.26.8 — bateria: causa dos 128–155% de CPU da prévia do Retrato e correção (Claude; APK de teste build76 entregue)

- **[Baixar ZIP com app-release.apk — build76 (1076)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39138561980/artifacts/12140374817)**. Mesma assinatura permanente. Artefato 12140374817, 132853213 bytes; digest do ZIP sha256:beccb8682601389cfca34ae3c97fffc8c38c02074a6e337a717d4c10d3bc6f14; expira 2026-11-17T15:07:16Z. [Actions 39138561980](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39138561980) com sucesso; publicação skipped. Fonte do app 4ba0c51 (CI 39137984831 aprovado); fonte do APK 05ee274; gatilho restaurado em f7e6db0 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Evidência (diagnóstico 0.26.7, bloco "CPU por thread", aparelho parado):** Foto 26%; Câmera inativa 5%; **Retrato 128%**, dos quais **"Thread-#" 74–78%** (thread nativa da MediaPipe), análise ao vivo ~30% (pool sem nome), `mediapipe_gl_ru` ~6%. O excedente do Retrato é a segmentação de pessoa da prévia ao vivo.
- **Causa:** a prévia usava o mesmo modelo multiclasse da foto final (`selfie_multiclass_256x256`, 16 MB, 6 classes) a 3–10 análises/s. Medido no servidor (LiteRT, 256×256): **77 ms de CPU por quadro**; o modelo leve de pessoa × fundo (`selfie_segmenter`, 250 KB, Apache-2.0) gasta **3,6 ms (21× menos)**.
- **Correção:** prévia do Retrato (foto) usa o modelo leve (`PersonSegmenter.Live(light = true)`, máscara única de pessoa). **Foto final e vídeo cinematográfico continuam com o multiclasse** (o cinematográfico grava a máscara; é área de imagem/Codex). Custo: a borda da máscara *na prévia* pode ser menos fina; a foto não muda.
- **Diagnóstico mais claro:** executores do app ganharam nome (nova-ui-io, nova-camera-io, nova-portrait-mask, nova-video-insp, nova-timelapse) e o bloco "CPU por thread" registra o topo da pilha das threads "Thread-N".
- **Esperado, não comprovado:** Retrato parado de ~128% para perto do Foto (~30–40%). Confirmar com novo diagnóstico (1 min Retrato parado).

---

# 0.26.7 — bateria/CPU: modelo de profundidade mantido entre fotos, CPU por thread no diagnóstico, cópias reduzidas da prévia (Claude; APK de teste build75 entregue)

- **[Baixar ZIP com app-release.apk — build75 (1075)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39136130725/artifacts/12139614731)**. Mesma assinatura permanente. Artefato 12139614731, 132640136 bytes; digest do ZIP sha256:d96ff445b76e0fc84ed99a6a5b0b78113eba17c5ddd68e51b393cdd74ed75424; expira 2026-11-17T14:35:06Z. [Actions 39136130725](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39136130725) com sucesso; publicação skipped. Fonte do app 5a3038f (CI 39135581746 aprovado); fonte do APK c52a6e6; gatilho restaurado em f173471 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- **Divisão de trabalho (dono, 18/10):** Claude cuida de **bateria/CPU/consumo**; Codex cuida da **qualidade de imagem**. Não editar os dois ao mesmo tempo no branch.
- **Profundidade:** o modelo era carregado e descartado a cada Retrato (diagnóstico 0.26.4: carga 2482–3558 ms para ~1,4 s de inferência). Agora fica carregado e é liberado após 60 s sem uso, com calor SEVERO, ou sob pressão de memória (`trimModelMemory`). Diagnóstico mostra "carga=0ms (modelo já carregado)" quando reaproveita. Esperado: −2,5 a −3,5 s por Retrato a partir do segundo. Não altera pixels.
- **CPU por thread:** o total do processo (Retrato parado: 155%) não diz quem consome. Novo bloco no diagnóstico, "CPU por thread", lê /proc/self/task a cada 10 s e soma por nome (pools agrupados), por atividade (`core/ThreadCpu.kt`, `ThreadCpuTest`). É o dado para a próxima correção.
- **Cópias da prévia:** contraluz automática (~1,5×/s) e auxílios Pro (2×/s) usavam `preview.bitmap`, cópia da prévia inteira na thread da interface. Passam a usar `PreviewFrameReader` (≤ 320 px, assíncrono, já usado pelo rastreamento). Mesma medida (64 e 240 px).
- **Não comprovado:** ganho de bateria ou CPU no S24. Próximo passo: diagnóstico com a prévia do Retrato e da Foto parada ~1 min cada, sem processar, para ler o bloco "CPU por thread".

---

# 0.26.6 — alinhamento e super-resolução em vários núcleos (Claude; APK de teste build74 entregue)

- **[Baixar ZIP com app-release.apk — build74 (1074)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39133451951/artifacts/12138453579)**. Mesma assinatura permanente, instala sobre a anterior. Artefato 12138453579, 132634522 bytes; digest do ZIP sha256:4be52c2aa1eea6613c4b8fae28824025459e013534309d67fbf49f733f376f27; expira 2026-11-17T14:00:31Z. [Actions 39133451951](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39133451951) concluído com sucesso (testes, lint, assembleRelease, assinatura, upload); publicação skipped. Fonte do app 01b5bd7 (CI 39133124284 aprovado); fonte do APK 9513b5e; gatilho original restaurado em 732a180 (blob a82bc113b2de207b7a3e9a9660cc19af520167ff).
- Até agora todo o processamento da foto rodava em **um núcleo**. Alinhamento (cada quadro contra a referência) e super-resolução (blocos da saída) são trabalhos independentes: passam a usar até **4 núcleos** (`core/Workers.kt`, `processing/Parallel.kt`). Calor ≥ MODERADO: 2; ≥ SEVERO ou economia de energia: 1, como antes. O diagnóstico registra "núcleos de processamento: N".
- **Mesma foto, pixel a pixel:** leituras do JPEG continuam em série e na ordem das faixas (cache intacto); só o cálculo de cada bloco é paralelo, e a gravação volta na mesma ordem. `ParallelEngineTest` compara 1×4 núcleos: campos de alinhamento, quadros usados e pixels idênticos. Nenhum limiar, quadro, resolução, tom ou algoritmo mudou.
- Medido no servidor (Kotlin, 4 núcleos, 6 quadros, alta resolução 2×): 640×480 572→293 ms; 2000×1500 **7,2→2,3 s (3,2×)**. **Não é medida do S24.** Lá alinhamento (7,5–8,5 s) + SR (13–13,7 s) eram ~21 s no diagnóstico 0.26.4; a expectativa é cair a uma fração disso, a confirmar no PhotoTrace.
- Custo: potência de pico maior durante menos tempo; energia total não comprovada. Memória: lotes de 2×núcleos blocos (não a fileira inteira) para não pesar a 48 MP.

---

# 0.26.5 — APK de teste entregue: assunto conectado e referência noturna

- **[Baixar ZIP com app-release.apk — build73 (1073)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39131104555/artifacts/12138172823)**. Mesma assinatura permanente, instalação sobre a anterior. Artefato12138172823, 132629039 bytes; SHA-256 doZIP 9dc817152193b544f0e18231a41b3c3e30b2280b0e1b8c61441412335576ce71; expira2026-11-17T13:28:31Z. Nenhum release criado.
- [Actions39131104555](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39131104555), job117686274567: **301 aprovados / 5 dispensados / 0 falhas**, lint, JPEG sintético200MP, modelos NAFNet/DepthAnything, assembleRelease, profundidade offline=True, assinatura/versionCode e upload aprovados. Publicação skipped. Fonteapp4344b8ef3188471df9c005021767088e24173c2e/CI39130691263; fonteAPKe2b10b947632bfaf3eec986d2ba709885b04b8b3; gatilho restauradof3c8fcd02ded88f7afdda1d74d93c3f5c2924f8f/blob a82bc113b2de207b7a3e9a9660cc19af520167ff. Nenhum app alterado após aprovação.
- Retrato de objeto seleciona um componente conectado perto do centro/toque; pessoas/objeto segurado preservados. Mediana3×3 evita perder estrutura2px no toque sem deixar outlier definir plano. Referência compensa ruído correlacionado e exige margem para trocar por quadro mais ruidoso, mantendo possibilidade de borda realmente melhor. Sem filtro/sharpen novo, mudança de tomNoturno/WB, exposição, quadros/resolução ou fusão/SR.
- Regressão anterior39130459743: três falhas reproduzidas. Depois: ilha1→0, alvofino0,333→0,667 e referência limpa escolhida; testes de pessoa/bordas/fusão/SR aprovados. Resultados sintéticos, **não melhoria física comprovada nas fotos doS24**. Score3×768×576 custou27,539ms noCI fonte e116,050ms noassinado; variação de servidor, sem conclusão de autonomia ou latência total.
- Diagnóstico agora imprime referência/ruído/custo, origem/faixa/cobertura da máscara e sigmaMerge separado doalinhamento; rejeição não é anunciada como movimento comprovado. Fotos declaradas0.26.3 e diagnóstico0.26.4 analisados separadamente em [NOTURNO_RETRATO_0265.md](pendente/NOTURNO_RETRATO_0265.md). Pessoais foraGit. Handoff e mensagem para Claude atualizados.
- **ValidaçãoS24 pendente:** textura/grão/máscara real, recortes nativos, tempos, PSS/térmica e prévia. Continuam abertos rejeição53–60%na fusãoNoturno5x, HDR/highlights, foco/óptica confirmada, custo deSR/depth/desfoque/perfil, NAFNet abortando, congelamentos e escala energética. Não declarar qualidade premium ou economia de bateria concluídas.

---

# 0.26.5 — seleção de objeto e referência noturna (fonte aprovada; APK pendente)

- Base c128f496/CI39127776999 aprovada. Análise individual das três fotos em NOTURNO_RETRATO_0265.md; fotos declaradas0.26.3, diagnóstico recebido0.26.4, sem associação automática entre versões. Pessoais foraGit.
- Regressões7dd0829/CI39130019486 reproduziram duas falhas: ilha desconectada vira assunto e quadro com ruído correlacionado ganha preferência por falsa nitidez. Teste adicional da estrutura fina fortalecido antes do fix; consultar seu run separadamente.
- Objeto sem pessoa: uma semente elegível mais próxima do toque/centro, mantendo componente inteiro; pessoas conservam sementes múltiplas. Toque usa mediana3×3 contra outlier, com teste de estrutura fina. Sem modelo/inferência nova.
- Referência: estima ruído correlacionado, desconta energia no agregado e exige ganho adicional para trocar por quadro mais ruidoso. Não filtra nem aumenta nitidez dos pixels; testes preservam preferência por bordas reais quando o ganho é suficiente. TomNoturno/WB/exposição/quadros/resolução/fusão/SR preservados.
- Fonte final4344b8ef3188471df9c005021767088e24173c2e aprovada noCI39130691263 em compilação/testes/lint. Ilha1→0, estrutura fina0,333→0,667, referência limpa escolhida; cenários sintéticos, não validaçãoS24. Score3×768×576=27,539ms nohost. Nenhum APK0.26.5 confirmado nem ganho físico de qualidade, tempo ou autonomia declarado. Congelamentos, rejeição53–60%noNoturno5x, HDR/highlights e demais gargalos seguem abertos.

---

# 0.26.4 — APK de teste entregue: eficiência tonal e metadados da captura

- **[Baixar ZIP com app-release.apk — build72 (1072)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39127091262/artifacts/12137010846)**. Mesma assinatura permanente, instalação sobre a anterior. Artefato12137010846, 132627982 bytes; SHA-256 doZIP 6e926a82da4b8416857580d20b68adbe99c76e1bee108166c2291be0393d8907; expira2026-11-17T12:30:40Z. Nenhum release criado.
- [Actions39127091262](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39127091262), job117674387380: **295 aprovados / 5 dispensados / 0 falhas**, lint, JPEG sintético200MP, modelos NAFNet/DepthAnything, assembleRelease, profundidade offline=True, continuidade da assinatura/versionCode e upload aprovados. Publicação skipped. Fonteapp142c648dde009250527b3525d807e4625078f8c2/CI39126725397; fonteAPKe8ae0037892a171fa74d24a47edb9e5ce02e86cf; gatilho restaurado8842d6faafe2472e5ef6c8fb73c6581caf863b34/blob a82bc113b2de207b7a3e9a9660cc19af520167ff. Nenhum app alterado após aprovação.
- ISO/exposiçãoJPEG por quadro e referência correta no acabamento, fontesCamera2/JPEG distintas; compatibilidade focal não vira ID confirmado. Coordenadas bilineares e faixa64linhas reutilizadas emKotlin, planos neutros sem cópia integral, Food/cancelamento/proteção de cor preservados. Cronômetros de plano/perfil/estilo. Testes exigem mesmos pixels; tom Noturno, oito quadros, resolução, modelos e recursos preservados.
- Experimento tonalJNI descartado por falta de vantagem consistente; bibliotecas nativas anteriores intactas. Três ensaios Kotlin com cinco pares mostraram medianas129,884→110,713ms,154,589→125,825ms e105,011→83,409ms. São microbenchmarks no servidor, **não velocidade total/energia/PSS noS24**. Não há novo filtro de ruído/nitidez nem detalhe físico novo comprovado nesta etapa.
- Análise individual das três fotos e diagnóstico físico0.26.3 em [QUALIDADE_0263_BASELINE.md](pendente/QUALIDADE_0263_BASELINE.md). Cópias1280×960 semEXIF foraGit. Baseline49MP8/8≈40–42s, Noturno5/5≈15,27s; focais compatíveis, ID não confirmado. **P0 física segue aberta**: testarAPK72, textura/grão/foco/cores, tempos, PSS/térmica e prévia. Congelamentos, evolução fina de qualidade e escala energética ainda pendentes. Handoff e mensagem para Claude atualizados.

---

# 0.26.4 — qualidade fotográfica: exposição da referência e eficiência tonal (APK de teste 72 em geração)

- Base cf6f617/CI39123476501 aprovada antes das mudanças. Baseline físico e análise individual das três fotos em [QUALIDADE_0263_BASELINE.md](pendente/QUALIDADE_0263_BASELINE.md); fotos pessoais1280×960 semEXIF preservadas foraGit.
- Diagnóstico do dono: 49MP com8/8 em39,89–41,52s; Noturno5/5 em15,27s. CompatibilidadeJPEG18,6mm com5x, ID não confirmado. Nada disso prova ganho controlado de qualidade/autonomia.
- ISO/exposição do JPEG passam a aparecer por quadro com origem explícita e dadosCamera2 separados. Acabamento usa metadados da referência escolhida, sem herdar ISO da primeira imagem quando ela muda. Compatibilidade não vira confirmação óptica.
- Port tonalJNI experimental foi testado e descartado antes do APK: CI39126084412 passou, mas cinco pares não mostraram vantagem (medianas Kotlin133,877ms/JNI162,440ms). Biblioteca nativa voltou ao conteúdo anterior; kernelsSR/alinhamento/detalhes existentes preservados.
- Fonte96848c1 aprovada noCI39126506961 em compilação/testes/lint: coordenadas bilinearesKotlin e faixa64linhas reutilizável, pixels exatos contra fórmula anterior. Cinco pares nohost: mediana129,884→110,713ms, quatro favoráveis; não é ganho físicoS24. Cronometra plano tonal/perfil/estilo.
- Fonte final142c648dde009250527b3525d807e4625078f8c2 aprovada noCI39126725397: compilação/testes/lint, incluindo plano neutro/Food/cancelamento. Segundo ensaiohost: mediana154,589→125,825ms, quatro de cinco pares favoráveis. APK de teste build72 em geração noActions39127091262/job117674387380 (versionCode1072 esperado), fonteAPKe8ae0037892a171fa74d24a47edb9e5ce02e86cf. Ainda não confirmado. Gatilho original já restaurado8842d6faafe2472e5ef6c8fb73c6581caf863b34; publicação deve ser skipped. Não declarar ganho físico nem correção geral de prévia.

---

# 0.26.3 — APK de teste entregue: ruído temporal e óptica auditável

- **[Baixar ZIP com APK assinado — build 71 (1071)](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39122820061/artifacts/12134983449)**. Extrair e instalar app-release.apk. Artefato 12134983449, 132625602 bytes, SHA-256 do ZIP cb1cb622e0b162bd12c8c8ba1e02f7834ad2af26a79f8e253a6ac84157ee5f70; expira 2026-11-17T11:28:36Z. Mesma assinatura permanente; nenhum release criado.
- Actions39122820061/job117661716743 aprovado: **289 testes aprovados / 5 dispensados / 0 falhas**, lint, JPEG sintético200MP, NAFNet, Depth Anything, assembleRelease, pesos offline/profundidade=True, certificado-versionCode e upload. Publicação skipped. Fonteapp7f6de132258a33827ae819e67e667a0d1237f4a1 (CI39122555216 aprovado), fonteAPKa150588a5cae3849eddb3c678fd259ede5cdc26f. Gatilho já restauradofa14f24431363d36c4ba8ea7f12074b6d422afb0/bloba82bc113b2de207b7a3e9a9660cc19af520167ff.
- Corrige plano ISO/exposição que ficaria subexposto e subestimação de ruído correlacionado na fusão Night/ZoomISO≥800. Regressão na base rejeitava4/4 quadros estáticos (RMS11,65 inalterado); corrigida aproveita4/4 e RMS6,27. Testes de cor, contraste, padrões e movimento passaram. **Resultado sintético, não qualidade física validada no S24.** Tom/BOOST_EV/WB do Noturno, SR e kernels rápidos0.26.2 preservados; sem novo modelo/filtro espacial pesado.
- CaptureOptics por foto/quadro distingue alvo5x, físico confirmado pelo resultado, compatibilidade pela focal JPEG, conflito e desconhecido. Não força lente em baixa luz sem validação. Fonte recebida: Foto5x e Noturno apoiado; arquivos1280×960 semEXIF não provam lente antiga. Não presumir altaresolução nem desempenho real.
- P0 continua aberta: comparar originais e diagnóstico no S24, tempo total/captura/fusão/acabamento, memória e térmica. Aproveitar mais quadros pode exigir mais fusão; não declarar 60–79s resolvidos nem melhora de autonomia. Roteiro/causas/limites em P0_NOTURNO_0262.md; handoff para Claude atualizado. Estados pendentes históricos abaixo foram superados por esta entrega.

---

# 0.26.3 — fonte aprovada; APK71 em geração

- Fonteapp7f6de132258a33827ae819e67e667a0d1237f4a1 aprovada no Actions39122555216, óptica39122412407 também aprovada: assembleDebug/testDebugUnitTest/lintDebug. Sem alteração deapp posterior. Fix de ruído/exposição5a9f660/39122118028 aprovado, falhas reproduzidas39121910927 preservadas.
- Build assinado real71 (1071 esperado), Actions39122820061, fonteAPKa150588a5cae3849eddb3c678fd259ede5cdc26f. Gatilho original já restauradofa14f24431363d36c4ba8ea7f12074b6d422afb0/bloba82bc113b2de207b7a3e9a9660cc19af520167ff. APK ainda não confirmado; publicação deve ser skipped. Não criar outro build/release.
- Teste físico S24 ainda pendente: sem conclusão de melhoria real de ruído/textura, óptica, velocidade, memória/térmica ou bateria. Nenhuma mudança de tom do Noturno/BOOST/WB, assinatura ou main. Detalhes da etapa abaixo e emP0_NOTURNO_0262.md; atualizações posteriores substituem estados pendentes históricos.

---

# 0.26.3 — ruído temporal, exposição segura e óptica da captura

- Feedback real da0.26.2: duas fotos1280×960 sem EXIF,5x e Noturno; não permitem confirmar lente/ISO retroativamente. Configuração de modo/apoio ainda em esclarecimento. Base79b2c328f58678741c5f3df0b9fcbacec2b807a2, CI39113294053 aprovado antes de mudar app. Gradle offline local indisponível; Actions executa validação.
- Regressões da base39121910927 reproduziram duas falhas: plano noturno ISO800/100ms subexposto e cena sintética estática com ruído correlacionado rejeitando4/4 quadros úteis, RMS11,65→11,65. Fonte corrigida5a9f660/CI39122118028 aprovada em compilação/testes/lint:4/4 aceitos, RMS11,65→6,27 nesse cenário. Não é mediçãoS24 nem prova da causa exata das fotos.
- Estimativa limitada por diferenças1/2/4px só quando detecta platô compatível com correlação; correção na fusão do Noturno e ZoomISO≥800. Testes de ruído independente, rampas/padrões, movimento, contraste e RGB. Sem blur espacial novo, sem modelo/inferência extra; SR/HDR e kernels rápidos preservados. Não muda curva de tom, BOOST_EV, WB ou clareamento do Noturno. Plano que não consegue luz prevista mantém automático; rejeição Camera2 também restaura AE antes de capturar.
- CaptureOptics registra por quadro/foto alvo, rota, resultado físico correlacionado por timestamp, focal do JPEG e ISO/exposição/AF/AE/AWB. Distingue ID confirmado, compatibilidade de focal, conflito e desconhecido. Pin/zoom da interface/focal35mm isolada não provam óptica5x. Rota automática não foi forçada em baixa luz sem evidência. Dados da prévia não substituem prova da captura; cache limitado16, sem espera extra.
- Fonte final deapp7f6de132258a33827ae819e67e667a0d1237f4a1, CI39122555216 pendente; óptica intermediatecff1719/39122412407 pendente. Nenhum APK novo confirmado. Próximo: verificar fonte final, corrigir falhas concretas se houver, gerar APK assinado para teste pelo fluxo existente sem release automático, restaurar gatilho. Comparação física de qualidade/latência/temperatura ainda pendente. Detalhes emP0_NOTURNO_0262.md. P0 energética e aquisição7–9s seguem abertas.

---

# 0.26.2 — APK de teste entregue; P0 física ainda aberta

- **[Baixar ZIP com APK assinado](https://github.com/chrystianmax73-dev/nova-camera/actions/runs/39089635870/artifacts/12122029001): versão0.26.2/build69 (1069).** Artefato12122029001,132619077 bytes; sha256 do ZIP ba236736aac0688b8fd9f6eb317fb945f610a4c0ad6baa63568ed3f380d9ec80; expira2026-11-17T02:26:55Z. Extrair e instalar app-release.apk. Mesma chave permanente; nenhum release criado.
- Actions39089635870/job117563423216 aprovado: **277 testes aprovados/5 dispensados/0 falhas**, lint, JPEG sintético200MP, NAFNet, Depth Anything, assembleRelease, pesos offline/profundidade=True, certificado/versionCode/upload. Publicação skipped. Fonteappbeb201fd4fb661b93aaaf7f7c7ad35a4b4badf9b (CI39089312391 aprovado), fonteAPK3cad7b111e164e7b395cfc111b26dc342be79b34. Gatilho original já restaurado0b82310fb281f03b044a440529aee3c228ac5439/bloba82bc113b2de207b7a3e9a9660cc19af520167ff.
- Núcleos nativos preservam pixels nos testes host; SR deixa de reter~83,3MiB de mapas não consultados na geometria do baseline. Não reduz2×→1,5× automaticamente: se não couber, preserva original e informa impossibilidade da alta resolução. AF exige sucesso real, rajada tem proteção limitada contra vigia, callbacks antigos isolados. Tempos de acabamento e memória/JPEG mais detalhados. Funções e código existentes preservados.
- **Sem ganho físico S24 comprovado ainda.** Não encerrada P0 de aquisição7,4–9,1s/congelamentos fora da rajada/temperatura/memória/qualidade. Potência não confiável até confirmar escala, sem multiplicação1000 por magnitude. Evidências antes/depois do microbenchmark Linux e protocolo em P0_ETAPA2_VALIDACAO.md. Handoff completo e texto para Claude atualizados. Fotos e diagnóstico completo anunciados ainda não anexados nesta etapa. Estados pendentes abaixo são histórico.

---

# 0.26.2 — foco honesto e proteção limitada da rajada

- Foco por toque só fica marcado como confirmado se CameraX retornar sucesso AF. Antes, bastava o callback existir ou solicitar FLAG_AF. Razões distintas para AF dispensado (manual/bloqueio/ponto confirmado/OEM/contínuo) e pedido não suportado/rejeitado. Mantém foco automático e não inventa foco no ponto.
- Vigia não reabre a câmera durante pausa legítima da aquisição: janela limitada por pedido (10s ou exposição longa+3s) e total (30s ou orçamento da rajada, máximo180s). Pedidos não renovam o prazo total; liberação após aquisição/pause/release/bind. Não desliga a recuperação de falha real. Testes de oito entregas, ausência de entrega, limite total e exposição longa.
- Callbacks/timeoutAF antigos não alteram captura nova: identidade do PhotoTrace protege vigia, restauração de foco/EV/zoom e estado do obturador. Troca intencional de modo não é bloqueada pelo vigia.
- Diagnóstico de STILL_CAPTURE via callback de sessão registra lógico, físico anunciado/solicitado, focal, AF e timestamp quando HAL entrega; EXIF da referência independente, sem confundir com óptica da prévia. Não obriga lente não suportada. Congelamentos fora da rajada ainda requerem registro físico.
- Alta qualidade com estado severo é adiada explicitamente antes da rajada; se aquecer durante aquisição, preserva original em vez de processar só1 dos8 e reduzir qualidade. Noturno fora deste perfil mantém algoritmo/tom. Listas JPEG da captura liberadas após transferência à fila/coleta. Interface avisa “Foto capturada · processando” ao terminar aquisição.
- APK0.26.2/build69 em geração no Actions39089635870; fonteAPK3cad7b1, gatilho já restaurado0b82310/bloba82bc113. Nenhum APK confirmado e nenhum release publicado.
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


