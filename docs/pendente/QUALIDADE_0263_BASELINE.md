# Qualidade fotográfica — referência física 0.26.3 (1071)

Base remota cf6f617cee143f50b79b25cfafbc9f02f3fe6420; CI39123476501 concluído com sucesso antes das alterações. Gradle local indisponível; validação autorizada pelo Actions. Não há aparelho conectado.

## Fotografias recebidas e limites

Três JPEGs enviados pelo dono, na ordem Foto3x, Foto5x e Noturno5x. Cópias recebidas1280×960, sem EXIF, originais preservados foraGit. SHA256:

- 139058: cd0c2f2c477111d2ebe9b5acca583535c36f8fdd959ae60e6a8dea362318dd8f
- 139057: 4e58170a31c22b76b011071ae6f12e78dead37aad602c8808d6bae5b3f7a35f6
- 139056: 0c2e589832c05ac5e983a4c2a5fc07669f14707c80e5ff8340f88fef2efb9f9f

3x: estampas da bolsa legíveis, cores contidas, tecido próximo mais suave. 5x: sombras mais fechadas e microtexturas menos legíveis. Noturno5x: mais visibilidade em sombras/cores, grão perceptível em parede/tecido. Planos de foco e enquadramentos diferentes impedem comparar nitidez diretamente. Não há prova visual suficiente para separar óptica, foco, fusão e suavização, nem avaliar49MP nessas cópias. Não foi observado artefato grande inequívoco de fusão; ausência nas cópias não exclui artefatos no original. Nenhuma edição dessas fotos é tratada como validação do pipeline da captura.

## Diagnóstico fornecido pelo dono

Não é possível associar univocamente os quatro registros às três imagens pelos arquivos semEXIF. Foto em alta resolução aparece como HDR no PhotoTrace, mas Job de custo é Zoom e o caminho é SR; não inferir bracketing a partir do rótulo.

| Registro | Aquisição desde toque | Alinhamento | SR/fusão | Acabamento | Total | Resultado |
|---|---:|---:|---:|---:|---:|---|
| #1 5x | 5,607s | 6,746s | 12,085s SR | 14,475s | 41,020s | 8/8,49MP |
| #2 Noturno5x apoiado | 4,492s | 2,878s | 4,892s fusão | 2,037s | 15,267s | 5/5 |
| #3 5x | 8,344s | 6,316s | 11,309s SR | 11,939s | 39,888s | 8/8,49MP |
| #4 3x | 7,252s | 6,828s | 12,754s SR | 12,422s | 41,517s | 8/8,49MP |

Tons isolados4,624–5,149s nas fotos49MP, detalhes0,546–0,620s. O restante do acabamento ainda não está inteiramente discriminado. Não atribuir tudo aos tons. ISO/exposição por quadro aparecem null no relatório óptico porque ele só imprime Sensor Camera2, embora o JPEG seja lido separadamente para NAI. Todas as focaisJPEG5x=18,6mm/115mm eq., compatíveis com ID7; 3x=7,9mm/69mm eq., compatíveis com ID6. ID físico não confirmado; resultadosSTILL não associados, não provar apenas pela focal/marca d'água. NAI finalISO1890/33,333ms não identifica a exposição de todas as fotos. Plano noturno solicitadoISO1573/1/13s não prova exposição efetiva.

NAFNet posterior abortou orçamento após3/192blocos, total3,039s; não é redução por IA concluída. GL sem máscara:20,8quadros/s, render wall médio1,218ms, sem uploads/readback; não medeGPU/energia. PSS observado até634,4MiB e CPU144,2% durante fotos; bateria35,4→38,5°C/máx38,6°C, térmico1. Comparação com0.25.3 não controlada, modos/cenas/estado diferentes: não concluir ganho percentual causal ou economia. Corrente continua sem escala confirmada; queda do contador implica1,423A sob unidadeAPI, não calibra firmware sozinha.

Congelamentos atuais fora da rajada persistem inclusive sem efeito/análise. Erros Java são históricos0.20.0; falha nativa17/10 não tem versão/stack suficiente para atribuir à0.26.3. Nenhuma correção geral de prévia está comprovada.

## Etapa limitada proposta

1. Mostrar ISO/exposiçãoJPEG efetivos por quadro, origem separada de Camera2, sem converter compatibilidade óptica em ID confirmado. Usar metadados da referência realmente escolhida no acabamento, sem herdar ISO de outra foto da rajada.
2. Portar apenas o laço tonal para a biblioteca nativa existente, fórmula/amostras/parâmetros/arredondamento iguais, sem modelos/threads novos; exigir comparação de pixels com Kotlin, incluindo bordas de blocos, tonsNoturno, altas luzes, pele, ruído e intensidade. Proteção cromática continua no Kotlin quando necessária.
3. Medir microbenchmark pareado noCI e registrar limites. Sem prometer redução física, detalhe novo ou autonomia. Preservar oito quadros, resolução, cor/tom e demais recursos; nova fotoS24 necessária.

Próximas etapas dependem da evidência: alinhar/fundir mais eficientemente, melhorar rejeição versus ruído sem fantasmas, investigar foco real e congelamentos. Não ampliar ruído ou clarearNoturno por tentativa. Comparar originais e versão anterior em cena/lente/foco/exposição/temperatura equivalentes,5repetições, com PhotoTrace e inspeção de recortes nativos. Não usar JPEG redimensionado para alegar detalhe49MP.


## Execução intermediária e decisão pendente

O CI39125696313 (fonte07ccf74) passou em compilação, testes e lint. O primeiro microbenchmark tonal NÃO mostrou ganho: três repetições no servidor, Kotlin237,256ms / JNI257,343ms. Isso não mede S24 e não justifica chamar a primeira implementação de mais rápida. Foram acrescentados reuso da geometria horizontal/bilinear, faixa64linhas e benchmark com aquecimento e cinco pares AB/BA, incluindo as cópiasJNI. A fontecc13e31 ainda precisa ser conferida. Se não houver benefício consistente, manter Kotlin ativo; não entregar caminho mais caro como otimização. Fórmulas e tolerância de igualdade dos testes não foram reduzidas.


## Estratégia nativa descartada antes do APK

A fontecc13e31 passou noCI39126084412. Cinco paresAB/BA com aquecimento: Kotlin[133,877;151,534;185,457;133,562;133,348]ms, JNI[162,440;110,115;203,691;187,889;94,701]ms; medianas133,877/162,440ms. Não houve vantagem consistente: port tonalJNI removido integralmente em96848c1; photo_kernels.cpp e PhotoKernels.kt restaurados ao conteúdo anterior da0.26.3. Os kernels nativos já existentes para alinhamento/SR/detalhes permanecem, sem alteração.

A nova hipótese é apenasKotlin: reutilizar índices/pesos bilineares horizontais e verticais, mantendo as operações/arredondamentos, e uma faixa64linhas reutilizável no Engine (Food mantém o caminho antigo). A faixa recebe coordenadas globais iguais; último trecho e cancelamento são testados. Referência anterior preservada para comparação exata e benchmark. Não há novo JNI, modelo, thread, filtro, LUT aproximada, nitidez ou ajuste de cor.

Na saída8160×6120, o buffer tonal passa de 768alocações256×256 (mais bordas) para um buffer8160×64 e um último trecho. Isto é contagem derivada do código, NÃO medição de alocações/PSS noS24. O custo dos pequenos vetores de coordenadas é adicional e entra no benchmark. Só os resultados doCI da fonte96848c1 ou posterior podem validar essa alternativa. Não tratar os testes da versão nativa descartada como validação dela.


## Alternativa Kotlin: primeiro resultado aprovado

Fonte96848c1, CI39126506961 aprovado em assembleDebug/testDebugUnitTest/lintDebug. Cinco pares de grupos3x após aquecimento, faixa8160×64, planos1024×768:

- Referência anterior: [126,659;141,645;127,637;140,052;129,884]ms; mediana129,884ms.
- Coordenadas reutilizadas: [104,304;151,087;110,713;109,444;116,103]ms; mediana110,713ms.

Quatro de cinco pares favorecem a alternativa; mediana cerca de14,8% menor nesse microbenchmark. Não mede tempo total49MP, CPU/energia/GPU noS24, captura, alinhamento, SR, PSS ou autonomia. Um par regrediu; há variabilidade do servidor. Testes exigiram os mesmos pixels, incluindo cores/altas luzes/Noturno/proteção cromática/costuras/Bitmap Android/cancelamento. Não é melhoria de detalhe capturado ou redução nova de ruído.

Fonte final142c648 acrescenta saída sem cópias para planos queToneMap já tratava como identidade, respeitando Food/cancelamento. Ainda consultar oCI39126725397 dessa fonte antes de gerarAPK. Comparação física permanece aberta.


## Fonte final aprovada

Fonte142c648dde009250527b3525d807e4625078f8c2, Actions39126725397/job117673304753: assembleDebug, testes e lint aprovados. Plano neutro/Food/cancelamento também passaram. Segundo ensaioAB/BA, grupos3x: referência[135,464;206,844;154,589;141,051;176,168]ms, coordenadas reutilizadas[125,825;126,244;158,417;119,638;116,313]ms; medianas154,589/125,825ms. Quatro de cinco pares favoráveis, cerca de18,6% nesse microbenchmark. Variabilidade entre runs preservada; não extrapolar para tempo total, energia ou S24. Nenhuma alteração de app posterior à aprovação.

Auditoria da árvore confirmou photo_kernels.cpp e PhotoKernels.kt idênticos à0.26.3. Não há alterações de manifesto/permissões/modelos/assinatura. A geração de APK de teste ainda precisa ser acompanhada até artefato real e verificação da chave.


## Roteiro físico da 0.26.4

Antes de instalar, se a0.26.3 ainda estiver no aparelho, registrar a referência nela; não desinstalar nem apagar dados para fazer downgrade depois. Preservar JPEGs originais e diagnósticos localmente. Atualizar com o APK assinado quando a entrega for confirmada.

Comparar Foto3x e5x com alta resolução, eNoturno5x apoiado. Mesma cena estática com tecidos/estampas, luz, distância, foco escolhido, resolução, brilho de tela e temperatura inicial semelhantes; aparelho fora do carregador. Fazer cinco capturas por condição, deixando retornar à temperatura inicial; separar primeiro uso de estado aquecido. Registrar dimensão, oito quadros/quadros usados, ISO/exposição de cada JPEG e eventual troca de referência. O ID físico pode continuar desconhecido: não transformar compatibilidade em confirmação.

Separar aquisição, alinhamento, SR/fusão, planejamento tonal, aplicação tonal, detalhes, crominância e gravação. Inspecionar recortes na mesma escala nativa, especialmente flores/borboletas/costuras, bordas de tecido, sombras e parede; comparar cor ambiente, grão, halos e fantasmas. Fotografias em enquadramentos diferentes não são comparação direta de resolução de detalhes. Anotar tempo de retorno da prévia e qualquer congelamento. Registrar PSS/CPU e temperatura, mas não tratar corrente sem escala confirmada como potência real. Sem esse ensaio, a otimização física e a evolução de qualidade fina permanecem abertas.


## Entrega para teste físico

APK0.26.4/build72/versionCode1072 confirmado noActions39127091262, artefato12137010846. Fonteapp142c648dde009250527b3525d807e4625078f8c2, fonteAPKe8ae0037892a171fa74d24a47edb9e5ce02e86cf, sem alterações deapp após aprovação. 295 testes aprovados/5 dispensados/0falhas, lint, JPEG200MP, modelos offline/profundidade=True, certificado/versionCode e upload aprovados; publicação skipped. Gatilho original restaurado. Link de download, digest doZIP e expiração no topoSTATUS/handoff.

Terceiro microbenchmark no run assinado: referência[98,945;102,381;105,011;114,873;108,557]ms, coordenadas reutilizadas[81,115;83,409;86,801;80,792;84,626]ms, medianas105,011/83,409ms. Todos os cinco pares desse run favoreceram reuso, sem extrapolação paraS24 ou autonomia. CIs anteriores e variação preservados acima. P0 física, congelamentos e qualidade fina não concluídos.
