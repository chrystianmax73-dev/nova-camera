# Handoff objetivo para Claude — Nova Camera

**Repo privado:** https://github.com/chrystianmax73-dev/nova-camera

Leia PRIMEIRO o README.md por completo, depois docs/ROADMAP.md, docs/RELEASES.md e o código existente. O projeto deve evoluir sem regressões.

## Tarefa imediata
1. Confirme acesso autorizado ao repo privado; caso não tenha, peça que o proprietário conecte seu GitHub.
2. Inspecione Actions. Run 38560342521 falhou em compileDebugKotlin por import ausente de ImageCaptureException; correção foi adicionada juntamente com este handoff, verificar novo run antes de concluir.
3. Corrija outras falhas reais até build verde. Não diga que há APK pronto sem um arquivo publicado e verificável.
4. Revise robustez de fotos, vídeos com áudio opcional, zoom durante gravação, foco e diagnósticos.
5. Prepare primeiro release PRIVADO com assinatura Android PERSISTENTE a partir de GitHub Secrets. Não recrie a chave e não peça segredos pelo chat.
6. O dono testará manualmente S24 Ultra e S25 Ultra. Forneça passos de teste e formato de retorno com logs sem informações pessoais.
7. Evolua interface premium Nova Camera, zoom fotográfico/vídeo, HDR/contraluz, retrato e consumo térmico, seguindo a ordem de prioridade do README.

## Entrega que conta como concluída
Links dos commits e CI verde; APK assinado de fato disponível (se Secrets configurados); matriz de funcionalidades implementadas vs apenas planejadas; limitações reais verificadas nos dispositivos. Sem afirmações de superioridade frente à Samsung sem comparação controlada.

## Janela de trabalho
Para uma primeira sessão curta priorize build verde + captura básica, não motor IA final. Primeiro build pode levar vários minutos, e testes físicos exigem usuário e celular. É preferível entregar uma base consistente a marcar 20 recursos como prontos sem validação.
