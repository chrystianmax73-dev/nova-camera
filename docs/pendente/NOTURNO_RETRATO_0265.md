# Noturno 3x/5x e Retrato 5x — análise e referência antes das mudanças

Base c128f496950e4ad7f0d972c1b14dc35149ca0639, branch codex/video-estabilidade-0-20. CI39127776999 aprovado antes de modificar o aplicativo. A0.26.4/build72/1072 permanece preservada. Gradleoffline local indisponível (command not found); usar Actions conforme autorização. Assinatura, main, modelos e fotos pessoais preservados.

## Imagens fornecidas

Três fotos identificadas pelo dono como0.26.3:139082=Noturno3x,139081=Noturno5x,139078=Retrato5x. Arquivos recebidos960×1280, semEXIF; originais recebidos não alterados nem enviados aoGit/Actions. SHA256:

- 139082:42b56c20778beb33aa2999f5d8c8b2e14cf7a5d6957ba92da28b422d2c328130
- 139081:0b028c9017b1b86243a0fb4f596f149f59a393868194dd91eff05ac4c419f959
- 139078:313125e3eb609a252aaf7ac6759c1abe3f9e8ad36708785fad91939ff2843c72

**Noturno3x:** parede e tecido exibem grão; estampas são legíveis, mas as tramas finas ficam suaves. Contraste contido e pouca separação tonal percebida. Enquadramento/resolução recebida impedem medir detalhe óptico ou atribuir suavidade aNR versus foco/redução da imagem. Não usar ganho de brilho para aparentar detalhe.

**Noturno5x:** aproximação permite ler melhor flores e borboletas, mas costuras e trama continuam pouco resolvidas; parede tem grão e sombras laterais são mais densas. Não há fantasma grande inequívoco nessa cópia. Focal da marca d'água não confirma ID físico. SemJPEG nativo/burst e diagnóstico deste disparo, não separar limitação do sensor, foco, alinhamento e suavização por suposição. O diagnóstico anterior mostrava5/5 noNoturno, não prova os quadros destas fotos.

**Retrato5x:** filtro e parte da torneira se destacam; fundo possui desfoque, porém vãos, curvas e reflexos metálicos são difíceis. Altas luzes fortes no arco e janela e regiões de sombra profundas; JPEG processado não prova se havia informação recuperável antes de saturar. Desfoque óptico e computacional podem coexistir. Não afirmar erro de máscara em todo contorno claro sem mapa de profundidade/máscara. Não adicionar nitidez a reflexos para fingir textura do metal.

Diagnóstico específico foi solicitado em paralelo, sem bloquear auditoria independente. Ainda não recebido neste registro. As novas fotos são da0.26.3, portanto não validam0.26.4 nem próximos ajustes.

## Hipóteses verificáveis no código

1. DepthPortrait.grow inicia todas as regiões elegíveis na área central ou no disco do toque; objetos desconectados dentro dessa área podem virar assunto simultaneamente. Para objeto único, isso contradiz a intenção de manter só o que está conectado ao alvo. Pessoas precisam conservar sementes múltiplas para membros/objeto segurado.
2. Mediana5×5 no toque pode descartar uma estrutura de dois pixels de largura, mesmo quando tocada corretamente, escolhendo o fundo. É limitação da seleção no mapa, distinta da capacidade do modelo de resolver metal/folhas. Avaliar vizinhança menor com proteção contra pixel isolado.
3. FrameQuality desconta ruído de cada gradiente e corta negativos antes de acumular: mesmo ruído puro produz energia positiva. Além disso, não usa a estimativa correlacionada já disponível noNoturno. Pode preferir quadro mais ruidoso como referência. A causa deve ser reproduzida antes de mudar a seleção; preservar foco, tom e fusão.

Commit7dd0829 adiciona regressões de hipóteses, sem mudar app. ConsultarCI e preservar falhas reproduzidas. Testes sintéticos não provam que sejam a causa exata das três imagens. Não alterar contraste/curva Noturna, WB, ISO manual, intensidade, resolução ou número de quadros para esconder os sintomas. Ajustes devem permanecer pequenos, sem novos modelos ou inferência, com comparação e custos medidos noCI e depois noS24.


## Diagnóstico adicional recebido: 0.26.4 (1072)

O dono respondeu com diagnóstico explicitamente0.26.4; manter separado das imagens inicialmente declaradas0.26.3. Há compatibilidade de conteúdo/valores na marca d'água, mas não associação provada porEXIF. Não usar este texto para inventar validação da nova0.26.5.

- #1 Foto5,06x alta resolução:8/8,ISO174/16,667ms, rota solicitada0@7, JPEG18,6mm compatível5x, ID não confirmado. Aquisição1,795s, alinhamento8,544s, SR13,721s, acabamento12,184s, total38,807s;49MP. PerfilContraste4,389s e estilo2,290s são parte importante do acabamento; não desativar escolhas do usuário silenciosamente.
- #2 Retrato5x:8/8,ISO136/16,949ms, JPEG18,6mm, AFsolicitado aceito; ID não confirmado. Aquisição4,963s, alinhamento7,493s, SR12,957s, segmentação0,758s, profundidade5,735s(carga3,577/inferência2,128), refino2,398s, acabamento12,737s, total49,012s. Plano tonal0,175s/tons0,0001s (neutro dispensado), perfilContraste5,009s, desfoque6,579s. Assunto automático sem pessoa, pelo centro. Não há máscara exportada para comprovar erro específico no metal.
- #3/#4 Noturno5x:3/3,ISO2056/41,667ms, JPEG18,6mm, totais14,740/13,016s. Rejeição de contribuições0,600/0,528 apesar de quadros globais utilizáveis. Isso não é prova de movimento; ruído, interpolação, cor/exposição e regiões sem correspondência também contam. Sigma de alinhamento0,00870 não equivale ao sigma usado noMerge RGB, que o diagnóstico antigo não imprime.
- #5 Noturno5x:5/5, mesmaISO/exposição, total19,170s, fusão6,954s, rejeição0,160. #6 Noturno3x:3/3,ISO1870/33,333ms, JPEG7,9mm, total11,933s, alinhamento2,139s/fusão4,002s/rejeição0,0508. Não comparar comoensaio3xvs5x: enquadramento/cena e condições diferem.
- NAFNet abortou0/192blocos após2,349s; térmica1→2 durante carga. PSS antes/depois423949/625126KiB, não pico nemmemória sódo modelo. Não alegar que houve redução porIA. Investigar prechecagem térmica e liberação após carga em próxima etapa, sem aumentar orçamento para71s.
- PSS amostradoRetratoaté624,5MiB,CPU149,4%, bateriaaté40,8°C, parte da sessão carregando. Sem potência confiável ou economia demonstrada. Prévia continua sem resultados mesmo semGL/análise. ErrosJava são históricos0.20.0, não novos crashes0.26.4.

## Regressão antes do aplicativo

Fonte7dd0829, CI39130019486:303totais,2falhas,5dispensados. Falha real: máscara central=1,0 e ilha desconectada=1,0. Referência: detalhe limpo0,00040736, ruidoso0,00707443, escolhendoíndice1. Teste fino foi fortalecido em e1e2831 antes do fix: exigir que o objeto tocado seja majoritariamente assunto, não apenas um valor intermediário causado por suavização de fundo. ConsultarCI39130459743 e preservar sua evidência.

Correção atual mantém seleção de pessoas e objeto segurado, limita sementes só para objetos, usa mediana3×3 no toque (pixel isolado não decide plano, estrutura2px pode sobreviver). Referência soma energia antes de subtrair variância, usa estimativa correlacionada e penalidade conservadora para troca por quadro mais ruidoso. Não é filtro espacial/temporal novo; pixels, tom, captura, número de quadros e fusão permanecem. Diagnóstico imprime índice/ruído/custo da escolha, sigmaMerge0..255 separado do alinhamento0..1, cobertura/faixa relativa da máscara, sem vender cobertura como precisão. Mensagem de rejeição passa a “contribuições inconsistentes”, sem afirmar movimento como causa comprovada.

Validação do fix pendente. Teste de outlier verifica faixa de foco, porque uma máscara suavizada pode preencher umpixel mesmo quando seu valor de profundidade foi rejeitado; cobertura pontual não prova o plano escolhido. Não reduzir tolerâncias para ocultar falha. Próximo: conferirCIs da fonte atual, corrigir causas, comparar máscaras e referência sintéticas, custos nohost e depois novas fotosS24; somente então APKassinado. Congelamentos, rejeição alta e qualidade HDR permanecem abertos.


## Resultado dos testes do código

Regressão fortalecida e1e2831/CI39130459743 confirmou303totais/3falhas/5dispensados: máscara do objeto fino0,3333; ilha1,0; referência ruidosa escolhida. Fonte inicial51e6d63/CI39130473446 falhou na chamada Kotlin do parâmetroseed; correção explícitaa2206e5 preservada. CIs39130495636 e39130577011 passaram nas três causas, mas falharam no teste novo de outlier que confundia cobertura suavizada com o plano; a verificação passou a medir a faixa de foco correta em4344b8e, sem alteração do algoritmo para esse teste e sem reduzir tolerâncias das três regressões.

Fonte final **4344b8ef3188471df9c005021767088e24173c2e**, CI **39130691263**, job117685060813, aprovado em assembleDebug/testDebugUnitTest/lintDebug. Resultados reais do servidor: centro1,0/ilha0,0; estrutura fina0,6667/vizinho0,0; referência limpa escolhida(índice0), energia limpa0,00039181/ruidosa0,00048692 apóscompensação. Bordas de verdade ainda podem substituir referência mais limpa. Regressões existentes de pessoa/objeto segurado, bordas, ruído/fusão, SR e orientação passaram. São cenários sintéticos, não máscaras verdadeiras destas fotos.

Custo medido do score em três planos768×576:27,539ms nesta execução; anteriores44,168/79,023ms. Variação de servidor, não mediçãoS24 nem comparação de autonomia. Sem modelo/threads/GPU novos. Acrescenta trabalho de estimação na seleção reduzida; medir custo real no PhotoTrace e não chamar custo de zero. Tom, fusão, quadros e resolução mantidos. Buildassinado ainda precisa ser confirmado; nenhuma melhoria física declarada.
