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
