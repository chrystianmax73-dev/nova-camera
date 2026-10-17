# Nova Camera — estado real dos recursos

Legenda da coluna **Validação**:
- **Testes**: coberto por teste unitário (lógica pura) ou pelo teste de fumaça da interface (Robolectric).
- **Compilação**: compila, passa no lint e é usado pelo app, mas o comportamento depende da câmera física.
- **Aparelho**: só pode ser confirmado no S24 Ultra / S25 Ultra. **Nada nesta versão foi testado em aparelho.**


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
taxa variável, quadros B e arquivo curto sem taxa mensurável. Execução deste commit pendente no CI.
**Compilação:** pendente no CI deste commit. **Aparelho:** não validado; não foi inspecionado um
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

