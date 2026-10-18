# Nova Camera 0.8.1 — validação fotográfica

Base preservada: Claude `f74394cc47353f7aa1cc90c62d8280f2463357cf`, branch `ccr-7443eaac-tqygyg`.
CI prévio aprovado: run 38634560810. APK de referência: `nova-test-10` (0.8.0).
Isso comprova compilação anterior, não o último estado funcional no celular.
Branch de trabalho: `codex/photo-quality-validation`; main e a branch Claude não são alteradas.

## Correções

- Janela de alinhamento sem suporte nas bordas: antes o leitor limitava a origem e deslocava um bloco inteiro. **Revisto na 0.9.1:** a primeira correção devolvia a referência para o bloco inteiro, e na foto real da cadeira (dossiê) isso deixava quadrados de tom diferente nos blocos da borda (salto na divisa dos blocos 2,19× o salto vizinho; 0.8: 1,21×). Agora só os pixels cujas amostras caem fora do quadro recebem a referência (1,20×); teste `borderTilesDoNotLeaveToneSquares`.
- Refino subpixel HDR mede erro com luminância normalizada pela curva de exposição já existente.
- Pesos HDR seguem o campo local de alinhamento, em vez de apenas o deslocamento central.
- Consistência HDR confere crominância nas regiões bem medidas, rejeitando objetos com luminosidade semelhante e cor diferente. **Revisto na 0.9.1:** remover só a contribuição deixa a referência sozinha no formato do objeto rejeitado, com o tom de outra mistura (buraco de tom médio 2,7 níveis na cena sintética). Voltou a substituição da 0.7 pela previsão da referência na exposição daquele quadro (curva medida, sem detalhe inventado): 1,8 níveis.
- Noturno escolhe referência por energia de bordas com desconto de ruído e proteção de clipping, somente com exposição/orientação/recorte iguais. Margem de 10% evita trocas por diferenças pequenas. É uma heurística, ainda precisa de calibração no aparelho; HDR mantém a exposição normal como referência.
- Originais duplicados desligados por padrão (pedido do usuário: uma única imagem final na galeria). Consequência conhecida: o JPEG da câmera só sobrevive nas quatro últimas sessões privadas; quem quiser o original permanente liga "Guardar originais". Preferências explicitamente salvas são respeitadas. Original × Nova continua pelo toque longo na miniatura; até quatro sessões privadas, removidas conforme novas sessões entram. Nenhuma tela de revisão automática.
- Diagnóstico inclui valores reportados pela prévia: câmera lógica/física, focal, zoom, ISO, exposição, foco, AF/AE/AWB, ganhos, OIS/EIS e resolução configurada. Não são metadados da exposição JPEG e podem ser os últimos valores recebidos antes de sair para Configurações.
- Assinatura visual e recursos existentes preservados. Ncnn opcional, desligado por padrão.

## Limites

Não há aparelho conectado nem fotos pareadas Nova/Samsung nesta sessão. Não afirmar superioridade, ganho de nitidez, latência ou menor temperatura.
Movimento em regiões totalmente saturadas continua sem observação direta: HDR usa confiança da vizinhança. Rotação, paralaxe e objetos móveis ainda exigem testes reais. Não há correção universal de fantasmas nem acesso garantido à tele física em todo firmware.
Amostras pessoais do dossiê anterior não estão disponíveis neste workspace; não foram reenviadas ou versionadas. A leitura por regiões preserva a arquitetura de memória do Claude; a seleção de referência acrescenta análise reduzida de cada JPEG.

## Referências externas: estudo parcial e reproduzível

Consultadas as páginas indicadas em 14/10/2026. A extração textual não expôs os arquivos individuais das galerias. As observações abaixo são síntese da análise publicada, **não inspeção visual independente dos JPEGs**. Não foram baixadas nem redistribuídas imagens. Completar inspeção em 100% e registrar índices/URLs individuais antes de alegar estudo completo das galerias.

| Referência | Grupos representativos a inspecionar, sem selecionar só os melhores | Limitações descritas na review |
|---|---|---|
| [vivo X300 Pro](https://www.gsmarena.com/vivo_x300_pro-review-2896p5.php) | 1x/2x diurno, retratos, tele 3,5x/7x/10x, selfie, noturno 1x/2x e tele | Texturas diurnas naturais; selfie perde detalhe com pouca luz; 10x diurno tem nitidez reforçada; tele 7x noturna suave, luz âmbar saturada. Não tomar resolução nominal como detalhe real. |
| [Pixel 9 Pro](https://www.gsmarena.com/google_pixel_9_pro-review-2745p5.php) | 1x padrão/50MP, retrato, tele 5x/10x/30x, selfie, interior/noturno | Contraste pode escurecer sombras; 50MP frequentemente mais suave; tele noturna apresenta textura excessivamente reinterpretada; WB varia entre fotos. Descartar Zoom Enhance como meta de fidelidade. |
| [iPhone 17 Pro Max](https://www.gsmarena.com/apple_iphone_17_pro_max-review-2884p6.php) | 1x 12/24/48MP, pessoas, tele 4x/8x, ultrawide interior, selfie, noturno | Texturas digitais e rostos suavizados/aquecidos; tele 4x/8x noturna suave/ruidosa; bom contraste e cor não comprovam microdetalhe. Comparar saídas com mesmo tamanho de exibição. |

Para cada galeria selecionar primeiro, meio e último exemplo de cada grupo disponível; registrar cena, modo/zoom/resolução, exposição conhecida ou ausente, detalhe, textura, ruído, contraste, cor, sombra/realce e artefatos. Incluir ruas, vegetação/grama, céu/contraluz, pele/retrato, letras, interiores e luz artificial conforme presentes. Não atribuir cenas ausentes à galeria, nem inferir algoritmos proprietários. Referência qualitativa entre cenas diferentes não é benchmark Nova × Samsung.

## Protocolo no S24 Ultra e S25 Ultra

1. Instalar por cima da 0.8.0, sem desinstalar. Confirmar preferências e todos os modos; assinatura NOVA CAMERA foto/vídeo quando ativada.
2. Desligar Guardar originais; fazer Foto/HDR/Noturno/Retrato/Food, cancelar uma tarefa e repetir com armazenamento cheio. Conferir uma foto final ou fallback íntegro; revisão opcional nas sessões recentes. Ativar originais e conferir a pasta separada.
3. Sem carregador, mesma luz/distância/posição/zoom e proporção: três pares alternando ordem Nova/Samsung. Tripé e mão livre separados. Repetir 1x/5x/10x, noite estática e com movimento, selfie em luz baixa, pele sob luz mista, céu/contraluz, grama e objetos com letras. Sem filtros de beleza.
4. Exportar diagnóstico após cada grupo; conferir físico/focal/AF/AE/AWB/OIS e EXIF/dimensões da foto. 10x não significa lente óptica 10x. Registar troca para câmera principal em pouca luz, se ocorrer; não forçar tele sem medir resultado.
5. Medir nitidez em borda inclinada (MTF50, se alvo disponível), legibilidade das letras sem reconstrução, ruído em área lisa (desvio padrão de luminância/crominância), detalhe na grama/pele e clipping por canal. Avaliar halos, duplicações e cor em recortes idênticos a 100% e no mesmo tamanho de saída. Gradiente alto sozinho pode ser ruído/sharpening.
6. Latência: tempo obturador → foto final em 10 repetições, mediana/p95. Memória: pico PSS via adb/dumpsys, incluindo rajada/revisão. Temperatura: estado térmico Android e temperatura de bateria início/fim, ambiente, carga e 5–10 min de vídeo. Temperatura da bateria não equivale à temperatura do sensor/SoC.
7. Aceitar apenas ganho repetível sem novos artefatos ou perda de naturalidade. Manter ncnn desligado; teste A/B separado antes de aprovar uso. Limites aceitáveis de tempo/memória devem ser definidos a partir da 0.8.0 no mesmo aparelho.

Guardar fotos e medições localmente. Relatório ao repo apenas com métricas anônimas, condições e hashes opcionais; nenhuma foto pessoal.

## Build

O workflow da branch executa testes e lint antes do release, usa os mesmos Secrets da assinatura permanente e compara certificado SHA-256 e versionCode com nova-test-10 antes de publicar. Não remove releases existentes. APK disponível só após workflow aprovado e asset publicado; CI/sintéticos não comprovam qualidade no telefone.
