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
