# Nova Camera — estado real dos recursos

Legenda da coluna **Validação**:
- **Testes**: coberto por teste unitário (lógica pura) ou pelo teste de fumaça da interface (Robolectric).
- **Compilação**: compila, passa no lint e é usado pelo app, mas o comportamento depende da câmera física.
- **Aparelho**: só pode ser confirmado no S24 Ultra / S25 Ultra. **Nada nesta versão foi testado em aparelho.**


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
