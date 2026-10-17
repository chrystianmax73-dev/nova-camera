# Rastreamento — referência e validação no S24 Ultra

Este roteiro ainda não foi executado nesta etapa. Testes sintéticos/JVM não medem autonomia,
latência da HAL, ocupação da GPU ou precisão em pessoas/plantas reais.

## Referência antes e depois

- Mesmo aparelho, lente/zoom, orientação, brilho da tela, resolução/FPS, áudio, assinatura,
  estabilização, luz e sequência de movimentos. Sem carregador; estado térmico e bateria iniciais
  semelhantes. Registrar versão/build e configurações, sem enviar mídias pessoais ao Git.
- Controle: rastreamento desligado. Teste: rastreamento ligado e assunto selecionado. Pelo menos
  três minutos por sessão e repetir em condições equivalentes, aguardando esfriar entre sessões.
- Começar FHD/30; depois FHD/60 e 4K nas combinações já aceitas. Não reduzir qualidade para
  produzir um resultado favorável. Comparar também câmera aberta sem assunto selecionado.
- Diagnóstico: CPU do processo em equivalentes de um núcleo (pode exceder 100%), PSS,
  cadência/perdas estimadas de CaptureResult, potência do aparelho na bateria, temperatura/estado
  térmico e margem prevista do Android. Leituras ausentes permanecem ausentes. GPU não medida;
  latência de cópia da prévia não representa sua utilização. FPS real do MP4 é outra medição.
- A energia de 0.21.1 relatada anteriormente (FHD perto de 60 fps, ~7460 mW e até 40,3 °C) é
  contexto histórico; não constitui referência controlada para afirmar melhora nesta versão.

## Rastreamento e regressões

1. Ativar a opção de rastreamento, tocar assunto com textura e mover devagar câmera/objeto.
   O retículo deve acompanhar pixels do assunto, distinguindo rastrear de um ponto fixo de AF.
2. Repetir pessoa, planta, objeto e animal com fundo diferente e com padrões repetidos. Cobrir
   o assunto, tirá-lo de quadro e testar área sem textura. Na perda/ambiguidade, voltar ao
   automático; não manter retículo fingindo confiança nem saltar para outro objeto parecido.
3. Frente/trás, aparelho vertical/horizontal; tocar esquerda/direita para conferir espelho e
   transformação da região de AF/AE. Foco físico depende de suporte contínuo/regiões da lente.
4. Toque longo deve continuar bloqueando foco/AE; foco manual do Pro não é substituído pelo
   rastreamento. Voltar ao automático deve liberar regiões e encerrar análise.
5. Alterar zoom/lente, qualidade, modo, minimizar app, pausar a câmera e encerrar vídeo. Não
   aplicar resultado antigo à sessão nova, empilhar tarefas ou deixar análise/modelos ativos.
6. Verificar MP4: resolução, FPS real, marca, áudio/sincronismo e reprodução; comparar com
   controle sem rastreamento. Testar gesto Foto/Retrato e fotos/HDR/Noturno/SR existentes.
7. Em calor severo, apenas o rastreamento deverá parar com aviso; qualidade selecionada não
   muda silenciosamente. A proteção crítica de gravação do aplicativo permanece vigente.

Profundidade cinematográfica e pós-processamento ainda são etapas posteriores. A presença de
retículo acompanhado não prova segmentação, desfoque ou aprimoramento no MP4.
