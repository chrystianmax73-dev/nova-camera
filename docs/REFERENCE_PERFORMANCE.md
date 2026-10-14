# Nova Camera 0.8.2 — referências, desempenho e validação

Base: 0.8.1 `dac9d0502500cb5cd7a2eae7750e1788a1332d6d`, derivada integralmente do trabalho Claude. Nova branch: `codex/reference-performance`. Main, branch Claude, 0.8.1, assinatura visual e Secrets permanentes preservados.

## Referências: estado da inspeção

Prioridade principal ainda **bloqueada para análise visual independente**. As três páginas e galerias complementares foram exploradas pelo navegador de pesquisa; imagens abertas retornaram somente marcadores `ImageDisplayed`, sem pixels. O terminal não consegue conectar ao proxy configurado (curl erro 7, porta 8080), inclusive com política de rede liberada. Não desligar o proxy nem fingir ter inspecionado JPEGs. Nenhuma imagem foi incorporada ao repositório, redistribuída ou usada para treinamento.

Páginas consultadas em 14/10/2026:
- [vivo X300 Pro](https://www.gsmarena.com/vivo_x300_pro-review-2896p5.php), [galeria hands-on](https://www.gsmarena.com/vivo_x300_and_x300_pro_hands_on-review-2892p4.php) e [galeria de viagem](https://www.gsmarena.com/field_report_took_the_vivo_x300_pro_on_a_trip_to_spain_and_came_back_impressed-news-70429.php).
- [Pixel 9 Pro](https://www.gsmarena.com/google_pixel_9_pro-review-2745p5.php).
- [iPhone 17 Pro Max](https://www.gsmarena.com/apple_iphone_17_pro_max-review-2884p6.php).

A busca por imagens também retornou exemplos de fóruns, anúncios de lentes externas e outros aparelhos. Foram excluídos do corpus GSMArena: autoria/captura/tratamento não equivalentes. Imagens de hardware/interface das reviews não são fotografias produzidas pelas câmeras.

### Síntese das análises publicadas — não observações visuais nossas

| Fonte | Qualidades relatadas e limites | Consequência técnica conservadora |
|---|---|---|
| vivo | Boa textura e amplitude tonal; selfie perde detalhe em pouca luz; 10x recebe sharpening; luz âmbar noturna pode ficar saturada. Galeria de viagem informa que os patos publicados são poucos acertos entre várias falhas de foco. | Avaliar repetibilidade e foco, não só os melhores exemplos; preservar cores e não aumentar saturação. |
| Pixel | Boa pele e detalhe; sombras podem ficar escuras pelo contraste; resolução máxima pode ficar suave; textura tele noturna excessivamente reinterpretada e WB variável. | Separar detalhe real de ruído/sharpening, manter letras autênticas; não usar Zoom Enhance como alvo de fidelidade. |
| iPhone | Boa cor/contraste/amplitude tonal; algumas texturas digitais e pele suavizada/aquecida; tele noturna suave e granulada. | Não perseguir megapixels nominais ou suavização de rosto. Conferir sombras, clipping e grão na mesma escala de exibição. |

Esses relatos orientam restrições, mas não justificam recalibrar tons do motor sem JPEGs e fotos controladas. Food, Retrato, HDR, selfies e parâmetros de cor/tom não foram recalibrados nesta entrega.

### Corpus a completar assim que pixels estiverem acessíveis

Selecionar variados exemplos dentro de cada grupo disponível, pelo início/meio/fim das galerias; incluir resultados médios e falhas. Não exigir quantidades iguais por marca. Registrar URL individual, índice, modo, zoom/resolução, cena e limites (miniatura/recompressão, exposição/firmware desconhecidos, sujeito em movimento, processamento posterior). Examinar saída inteira e recortes a 100%, respeitando tamanhos de saída diferentes.

Grupos: diurno 1x, vegetação/grama/texturas, céu/contraluz, ruas/objetos/letras, interiores, retrato/pele, selfies, tele nativa e zoom ampliado, noite estática/movimento/luzes artificiais. Usar 3,5x/7x/10x do vivo, 5x/10x/30x do Pixel e 4x/8x do iPhone conforme disponíveis, sem equivalência ótica presumida. Cenas distintas são referências qualitativas, nunca comparação direta entre marcas ou contra Nova.

## Correções implementadas

1. Nova sessão após onStop → onResume pede câmera padrão e zoom 1x. O bind sincroniza valor físico solicitado, curva e seleção visual. Callback de requisição anterior é invalidado por geração; reconfiguração dentro da mesma sessão continua preservando zoom. Retorno de Galeria/Configurações também inicia nova sessão.
2. Em foto, com luz suficiente estimada por ISO <800 e exposição <20 ms, a parada de zoom pode preferir tele nativa **se exposta como câmera separada e se nenhuma câmera lógica cobrir o alvo**. Vídeo/baixa luz e câmeras lógicas mantêm a política estável. Os limiares são heurísticos e precisam de medição no firmware; nenhuma promessa de tele 5x física forçada no S24 Ultra.
3. Medição por toque recua para AE quando AF+AE não é suportado (ex.: foco fixo). Requisição de foco que falha não fica marcada como bloqueada; callback de câmera antiga é ignorado. Não altera o tom noturno nem inventa detalhes.
4. Rajada noturna limita exposição acumulada a 1,2 s, com mínimo de um quadro e máximo térmico existente. Overhead CameraX não entra nesse cálculo; não é prazo total do obturador. Fusão/rejeição/fallback da 0.8.1 preservados.
5. NAFNet permanece opt-in/offline, sem rede por foto. Cache sincronizado de até 60 s de inatividade; libera em pressão de memória, calor ou IA desligada. Toda inferência e descarte compartilham o mesmo monitor; descarte da fila roda fora da UI.
6. Pools CPU/Vulkan reutilizados entre Extractors; CPU limitada a duas threads. Mesma versão ncnn, pesos, forma de entrada 288x288, precisão de armazenamento e mistura de 75%. Não há downscale oculto, mudança de modelo ou super-resolução generativa.
7. Custo protegido: CPU acima de 1 MP dispensada; orçamento heurístico de 8 s total e 6 s de inferência, verificado entre blocos. Primeiro bloco projeta custo e pode dispensar IA antes de alocar buffer final inteiro. Cancelamento/calor/custo/falha conserva bmp integral anterior à IA, sem mosaico parcialmente processado. Uma chamada JNI não é preemptível; inicialização/primeiro bloco podem ultrapassar orçamento. Projeção inclui preparação inicial de pipelines e pode ser conservadora demais. Esse fallback mantém o cache para medir de novo já aquecido na próxima foto; só falha de carga/inferência entra em cooldown.
8. Diagnóstico separa carga, primeira inferência (inclui preparação Vulkan), soma de inferência, total, backend, reutilização e blocos. PSS início/fim não é pico de memória; temperatura de bateria não é temperatura do sensor/SoC. Medições só existirão quando a IA rodar num aparelho.
9. Texto da opção IA mais claro, sem redesign. Salvamento, MediaStore, comparação, duplicações e integração com a galeria mantidos; código de MediaWriter, MirroredPhotoSaver e ReviewActivity sem alterações.

## Comprovação e pendências

Testes de regressão adicionados para callback de zoom obsoleto, tele exposta/câmera lógica, orçamento de IA e limite de exposição da rajada. Rodar suíte existente, lint e build assinado. Workflow exige pesos NAFNet dentro do APK e compara certificado SHA-256/versionCode com nova-test-11; não gera chave nem apaga releases.

Sem S24 Ultra conectado nesta sessão. JNI/Vulkan, autofoco físico, seleção efetiva 5x/10x e latência/temperatura não podem ser comprovados por testes JVM. Não declarar ganho de velocidade, qualidade ou superioridade frente à Samsung antes de medições. Mesmo carga/inferência separadas são instrumentação, não resultados medidos.

## Próximo teste real no S24 Ultra (depois S25 Ultra)

- Instalar por cima da 0.8.1. Conferir uma única imagem final e galeria em Foto/HDR/Noturno/Retrato/Food/selfies, sem mudar preferência de originais.
- Ir a 10x, sair para Home e reabrir; voltar de Galeria/Configurações; confirmar botão 1x e enquadramento físico. Repetir após alternar frontal e durante interrupção de vídeo. Troca de modo na mesma sessão deve preservar o zoom.
- Três pares por cena com ordem Nova/Samsung alternada, mesma luz/posição/zoom/distância/proporção: rua e letras, grama/folhas, céu/contraluz, retrato, interior, noite estática/movimento e selfie em pouca luz. 5x/10x em tripé e mão livre separados. Registrar EXIF/dimensões, diagnóstico da lente/AF/AE/AWB/OIS, foco próximo/distante e repetibilidade.
- IA desligada primeiro. Depois comparar ligada/desligada com a mesma cena. Medir primeira foto fria, segunda/terceira durante cache e foto após 60 s; exportar diagnóstico. Avaliar grão, microdetalhe/pele/letras e latência mediana/p95 em dez repetições, sem filtros de beleza.
- Medir pico PSS externamente com adb/dumpsys, estado térmico e temperatura de bateria início/fim, ambiente/carga, dez fotos e 5–10 min de vídeo. Testar economia de energia, calor e pouca memória: fallback deve guardar o resultado Nova completo com interface responsiva.
- Referências externas podem guiar a avaliação; aceitar melhoria apenas se repetível nos pares Nova/Samsung, sem aumento de artefatos ou perda de naturalidade. Fotos pessoais e imagens externas ficam fora do repo.
