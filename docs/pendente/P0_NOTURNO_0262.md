# Feedback físico 0.26.2: óptica 5x e ruído noturno

Base HEAD79b2c328f58678741c5f3df0b9fcbacec2b807a2, CI39113294053 aprovado antes de modificar o app. Gradle offline local tentou executar e retornou command not found; validação real por Actions autorizada. Mesma branch codex/video-estabilidade-0-20, main/chave permanente preservadas.

O dono confirmou a versão0.26.2 e forneceu duas fotos reais: enquadramento5x e Noturno. Arquivos recebidos1280×960 sem EXIF; referências visuais de pouca luminosidade/granulação na5x e granulação/cor na noturna. Lentes e enquadramentos diferentes: não são ensaio A/B. Nenhuma imagem pessoal enviada ao Git. Em esclarecimento posterior, o dono confirmou Foto na5x e Noturno apoiado. Alta resolução não foi explicitamente confirmada na pergunta separada; conferir dimensão/plano no diagnóstico.

Prioridades: confirmar5x óptico versus3x recortado; reduzir ruído sem apagar textura; aproveitar quadros/ISO; preservar atmosfera; foco/nitidez naturais; evitar60–79s. Não declarar alcance físico dessas metas apenas por testes sintéticos.

## Causas reproduzidas em teste real antes da correção

Commit de regressão b6a3823877d0ffdea887e9047061d71d05244194, CI39121910927:284 totais,2 falhas,5 dispensados. Falhas esperadas preservadas, sem modificar app nesse commit:

- NightExposure aceitou plano ISO800/100ms para AE6400/40ms, apesar de não conseguir a luz prevista dentro dos limites. Nova regra recusa plano subexposto e mantém AE; BOOST_EV0,5, curva/tom/clareamento/WB existentes não mudam.
- Cena estática sintética com ruído correlacionado2×2: sigma estimado0,5/255, ruído para alinhamento0,002;0/4 quadros adicionais aproveitados, RMS11,650→11,650. Diferença entre vizinhos dentro de blocos subestima esse tipo de ruído e o confunde com movimento. Isso não prova que seja exatamente o ruído das fotos do dono.

## Correção temporal e resultado medido no servidor

Fonte5a9f66012216206b51cea029bfcb6883561df415, CI39122118028 aprovado em assembleDebug/testDebugUnitTest/lintDebug. Mesmo teste: sigma11,097/255 e0,043518 para alinhamento;4/4 quadros adicionais aproveitados; RMS11,650→6,266. Sem filtros espaciais de suavização na saída.

Estimador adicional usa diferenças em1/2/4px e só aumenta a estimativa quando há crescimento de1→2 e platô de2→4. Rampas/bordas periódicas e ruído independente têm testes para não ganharem tolerância arbitrária. Testes de movimento sem fantasma/halo, contraste e médias RGB passaram. São cenários sintéticos, não conclusão sobre texturas reais do S24.

Ativado somente na fusão temporal do Noturno e Zoom com ISO≥800; alta resolução/SR/HDR e kernels nativos0.26.2 preservados. Sem novos modelos, sem processamento de profundidade, sem novos quadros ou alteração de resolução. Amostras adicionais limitadas a cerca de32mil diferenças por distância, fora da UI. Não há melhoria energética ou tempo total físico comprovado. Aguardar comparações por etapa em aparelho.

## Auditoria da óptica do quadro

A seleção física antiga preferia nativa só com luz suficiente; em Retrato/Vídeo usa lógica por estabilidade. Portanto “5x” na interface representa enquadramento e não confirma óptica5x. Não forcei uma lente em baixa luz sem medir qualidade/compatibilidade, nem reativei rotas que já travaram.

CaptureOptics correlaciona resultado STILL_CAPTURE ao JPEG por timestamp exato, cache máximo16 entradas. Registra alvo, rota solicitada, físico informado, focal do JPEG, compatibilidade pela óptica anunciada e ISO/exposição/AF/AE/AWB do resultado. ID só confirmado quando evidência compatível do próprio quadro está disponível; JPEG sem resultado pode indicar compatibilidade, não confirmação. Focal35mm sozinha pode incluir recorte e não prova lente. Contradição JPEG/resultado vira conflito explícito. Pin solicitado não é prova por si só.

Evidência por quadro e referência selecionada guardadas em sessão/diagnóstico e descrição EXIF da foto processada. Não substitui tags ópticas por dados da prévia. Dados NAI da primeira captura deixam de herdar ID físico não verificado da prévia. Zoom solicitado congelado no início, evitando que mudança de sessão altere metadados da foto antiga. Cache não bloqueia a captura esperando resultados ausentes; nesse caso a incerteza permanece explícita.

Plano manual recusado pela câmera volta a AE antes de disparar; não é anunciado como aplicado só porque o futuro terminou. Instrumentação separa cópia/liberação do JPEG do custo de EXIF/metadados. Sem novos atrasos programados ou rajadas paralelas.

## Validação pendente no S24

- Repetir Foto5x e Noturno apoiado, com mesma cena/luz/temperatura inicial similares; registrar alta resolução e plano efetivo.
- Guardar JPEG original como arquivo/ZIP e diagnóstico inteiro. Imagens enviadas aqui não preservam EXIF: não é possível confirmar retroativamente a lente das duas fotos só por elas.
- Conferir alvo/óptica/ID confirmado ou compatível, ISO/exposição efetivos e pedidos, quantos quadros foram aceitos e rejeitados, foco e retorno da prévia.
- Comparar grão, textura, bordas, pele, folhas, cor ambiente, fantasmas e halos no mesmo tamanho; não transformar noite em dia.
- Comparar captura/alinhamento/fusão/acabamento/gravação/total, PSS/CPU e térmica. Meta de não repetir60–79s ainda não verificada; desempenho da0.26.2 preservado em código, sem promessa física.
- Regressão em Foto/Retrato/Noturno, lentes3x/5x/10x, frontal, assinatura, vídeo comum e perfil cinematográfico autorizado. P0 não encerrada sem teste físico.
