# Nova Adaptive Imaging — 0.8.3

Complemento da base Claude 0.8, sobre a validação 0.8.2, na branch codex/nova-adaptive-imaging. Não substitui alinhamento, fusão, NAFNet, retrato, controles ou galeria.

## Auditoria e complemento

Adaptive/Tone já decidiam a intensidade por estatísticas da cena; Align/Consistency/Fusion/Merge e Engine já alinhavam e fundiam HDR/noturno, com fallback; NcnnDenoiser já reutilizava o modelo offline com orçamento e política térmica. Os limites RGB de ToneMap já existiam. Esses recursos não são apresentados como novidades.

AdaptiveImaging acrescenta contexto por aparelho/lente e classificação heurística (equilibrada, pouca luz, HDR, verdes, zoom 5x/10x). ImagingFacts lê identificadores, focal de prévia e tamanho físico do sensor disponibilizados por Camera2; ISO/exposição vêm do JPEG capturado. Informação ausente permanece ausente. A lente informada na prévia não comprova qual lente produziu o JPEG.

CalibrationRegistry aceita apenas correspondência de lente/cena, evidência de medição e lente verificada na captura. O registro embarcado está vazio: nenhuma calibração óptica do S24/S25 foi inventada ou importada. A classificação prepara o roteamento; não liga um novo modelo nem muda automaticamente exposições/fusão por cena.

Na FOTO, quando o plano existente corrige o nível de preto, um complemento cromático reduz o desvio das proporções RGB nos tons saturados. Não aplica LUT externa, balanço de branco presumido, embelezamento, aumento geral de saturação ou geração de detalhes. Pixels originalmente dominados por verde mantêm exatamente o resultado anterior. Não há segmentação semântica de pele/cabelo neste complemento.

O plano atual é a referência A/B, na mesma amostra de pixels. Testa proteção em 100%, 50%, 25%: mantém luminância (tolerância 1,5/255), contagem de canais em 255 e textura de luminância (Laplaciano, mínimo 99,5% do legado). Se falhar, usa o plano anterior. Luminância/clipping e verdes têm ainda proteção por pixel em resolução cheia; o critério de textura é avaliado na prévia. Essas métricas limitam alterações, mas não provam ausência de artefatos perceptuais nem qualidade premium. O complemento atua em RGB JPEG já processado pelo fabricante, sem recuperar informação perdida.

Foto usa o complemento automaticamente, em segundo plano; desativar processamento continua funcionando. Retrato, HDR, Noturno, Food e outros modos mantêm o caminho anterior. Revisão opcional guarda contexto na sessão privada; sessões antigas mantêm o caminho legado. Uma única URI final continua sendo regravada. MediaWriter, MirroredPhotoSaver e a integração da galeria não foram alterados. Diagnóstico registra cena, proteção, procedência dos dados e ausência de calibração.

## As dez referências fornecidas

SHA256 dos dez arquivos conferidos com manifesto.json. Identificação dos aparelhos conforme pacote/usuário; EXIF não confirma fabricante/modelo, ISO ou focal. As referências são versões reduzidas, não RAWs; não permitem medir fidelidade colorimétrica sem alvo/cena real. Nenhum arquivo externo, ZIP, foto pessoal ou imagem gerada foi incorporado ao Git, APK ou treinamento.

| Referência | Critério observado e limitação |
| --- | --- |
| iPhone WhiteCorridor | Madeira, paredes claras e iluminação mista: preservar diferenças quente/frio. Sem alvo neutro não se pode atribuir a tonalidade ao balanço de branco. |
| iPhone BushWindow | Pele/cabelo e azul/vermelho intensos junto da janela: evitar clipping por canal; iluminação quente não é necessariamente dominante indevida. Letras pequenas limitadas pela resolução. |
| iPhone PoleHDR | Rosto em sombra e céu claro: manter gradação das nuvens; rosto ainda escuro mostra o compromisso do HDR. Não presumir que abrir todas as sombras melhora a cena. |
| Vivo Watch | Impressão e metal: preservar caracteres reais e reflexos; plano de foco e profundidade de campo limitam as bordas. |
| Vivo DuoCafeIndoor | Textura facial, tecido e luz quente; focos de luz e alimento em outro plano não têm igual detalhe. Sem RAW não separa suavização de foco/compressão. |
| Vivo BoysXmasTrees | Rostos, lã, casacos escuros e galhos sob iluminação mista; céu azul intenso não serve como alvo de cor. |
| Pixel PXL_20250808_092645260 | Grama/folhas em sol e sombra, céu uniforme; avaliar textura sem tornar sombras cinzas. Distância e redução JPEG limitam microdetalhe. |
| Pixel PXL_20250808_091042723 | Pele, tecido rosa, metal e sombra dura: contraste local sem contornos artificiais; transparência do tecido não é ruído. |
| Pixel PXL_20250808_094026787 | Pele em sol, tecido amarelo e pedra: preservar diferenças de textura e cor. Não é uma cena comparável aos retratos das outras marcas. |
| Vivo 10x_m | Telhas, postes e pessoa: há suavidade em detalhes distantes. Rodapé declara 242 mm equivalentes, f/2,67, 1/144 s, ISO 50; não é EXIF verificável nem calibração óptica Samsung. |

Não há uma cena noturna urbana ampla ou selfie escura dedicada nesse conjunto de dez. BoysXmasTrees/cafeteria são luz artificial/mista, não substituem a validação noturna. As referências anteriores adicionais não aumentam artificialmente a amostragem deste pacote.

## Validação e limites

AdaptiveImagingTest faz A/B sintético no código real: verdes, luminância, textura, clipping, proporções cromáticas e identidade dos modos especiais; verifica isolamento de calibrações sem evidência. Os testes existentes continuam cobrindo alinhamento/fusão/salvamento e interface. Isso é regressão computacional, não ensaio fotográfico no aparelho.

LocalAdaptiveValidation permite A/B opcional com fotos locais via NOVA_AB_RAW, arquivos .rgb (largura/altura int big-endian, bytes RGB). Não roda no CI sem arquivos. O ambiente local não dispõe de Gradle/Kotlin e o proxy de downloads não funciona. O CI compilou o mesmo core Kotlin; suas classes e runtime JVM foram recebidos e tiveram SHA256 conferido. O transporte não incluiu fotografias nem chave de assinatura. ReferenceAB.java executou o motor real localmente nas dez imagens: JPEG→RGB via Pillow, amostra de lado 1024 com interpolação bilinear, aplicação em resolução recebida (1536 no lado maior). Essa decodificação/interpolação não é a implementação Android de Bitmap e a medição não inclui captura, JPEG final, ncnn ou fusão de bursts.

Resultado A/B atual × complemento: dez saídas RGB exatamente iguais, zero verdes alterados e zero diferença de luminância. O motor anterior dispensou ajustes em oito fotos; Watch e 10x mantiveram o plano tonal anterior a 65%. Nenhuma exigiu proteção cromática nova (nível de preto planejado zero). Isso demonstra preservação deste conjunto, sem evidência de ganho fotográfico nele. O teste sintético de correção de preto mostrou redução de desvio nas proporções dos canais; a confirmação visual em capturas Nova reais permanece pendente.

CI do APK build16: 87 testes aprovados, quatro testes que requerem arquivos locais dispensados, lint e compilação assinada aprovados. Pesos NAFNet offline verificados; continuidade de certificado e versionCode 1013→1016 confirmados. APK: Nova-Camera-v0.8.3-build16.apk, commit de código 0ab9be2bf6d89884d3c2c9f8a146352020a816c4, SHA256 0467d533a7376b8aa3e94836ac24af274ee96d6e393a25b90fcaa47d6935889c. Alterações posteriores são relatório/automação, sem mudança no código embarcado.

Reprodução local: tools/reference_ab/ReferenceAB.java recebe uma pasta de .rgb em resolução cheia e opcionalmente outra com as amostras 1024 de mesmos nomes. Execute com Java 17+ e classpath contendo as classes do core compilado e kotlin-stdlib: `java --class-path 'core:core/*' tools/reference_ab/ReferenceAB.java /pasta/rgb_cheio /pasta/rgb_preview`. A ferramenta escreve apenas CSV de métricas; arquivos e resultados fotográficos permanecem fora do Git. O CSV resumido deste ensaio encontra-se em docs/NOVA_ADAPTIVE_REFERENCES_AB.csv.

No S24 Ultra, comparar 0.8.2 × 0.8.3 × Samsung no mesmo local/luz/enquadramento, pelo menos três capturas de cada: vegetação, retratos de diferentes tons de pele, interior quente, contraluz, letras/objetos, 5x/10x e noite com movimento. Conferir cor sem alvo não é medição de erro colorimétrico. Registrar latência captura→final, PSS/memória, estado térmico e temperatura em sequência longa, lente/exposição efetivamente usadas e recortes 100%. Não há afirmação de superioridade, temperatura reduzida ou nova calibração óptica comprovada antes desse ensaio.

Assinatura permanente vem dos Secrets existentes. O build16 foi comparado ao APK 0.8.2 build13. O CI seguinte usa build16 como referência de certificado/versionCode e verifica testes/lint e pesos offline antes de publicar outro APK de validação.
