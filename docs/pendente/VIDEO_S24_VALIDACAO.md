# Validação física de vídeo — S24 Ultra, após 0.21.1/build 60

Esta lista ainda não foi executada na versão nova. Testes JVM, consulta de capacidades e desenho
na textura não confirmam a qualidade final do MP4 nem substituem estes testes.

1. Ativar “Assinatura Nova Camera” e “Também nos vídeos”. Fazer uma gravação de controle em
   Full HD/60, com boa luz, e conferir o vídeo reproduzido: marca legível, áudio, orientação,
   fluidez e sincronismo. Repetir com assinatura desligada.
2. Na traseira, selecionar 4K/60 e gravar em 1× e 5×, por pelo menos dez segundos. Anotar se
   EIS foi recusada e retirada. A sessão é consultada na câmera lógica; isso não prova que a
   tele física 7 consiga a mesma combinação após o zoom. Conferir o ID físico no diagnóstico.
3. Se a combinação for recusada, conferir se a prévia continua disponível e se o obturador fica
   bloqueado. A alternativa de 30 fps na mesma resolução só deve se aplicar após confirmação.
   Testar cancelar e aceitar. Nunca aceitar Full HD como evidência de 4K.
4. Após cada arquivo, ler “Vídeo (configuração, efeitos e arquivo final)” no diagnóstico:
   tamanho real, MIME do codec e FPS médios calculados pelos timestamps das amostras do MP4.
   4K esperado: 3840×2160 ou 2160×3840; Full HD: 1920×1080 ou 1080×1920. Registrar a taxa
   medida, mesmo quando diferente do alvo, e repetir com pouca luz sem pressupor 60 fps reais.
5. Reproduzir início, meio e fim de cada vídeo para confirmar a assinatura nos pixels. A linha
   “desenhos da assinatura na textura” é evidência parcial do renderizador, não do arquivo final.
   Repetir frontal com espelho ligado/desligado e aparelho nas orientações vertical/horizontal.
6. Repetir vídeo normal e Vídeo Pro. Fora de gravação, alternar 1080p/4K, 30/60, frontal/traseira
   e zoom 1×/5×. Nenhuma troca deve gerar ciclo de reabertura, tela preta persistente ou crash.
7. Gravar, parar, recomeçar; gravar e enviar o app ao fundo; retornar. Se ocorrer interrupção
   da câmera, conferir finalização/reprodução do arquivo antes de qualquer recuperação.
8. Conferir gravação rápida de Foto/Retrato (segurar, soltar e bloquear pelo arraste), fotos,
   Retrato e Noturno. Registrar temperatura e tempo de sessão, sem atribuir falhas ao calor
   apenas por a bateria estar morna.

Não enviar mídias pessoais ao repositório. Para investigação, bastam inicialmente o diagnóstico
local e a descrição do resultado; qualquer arquivo de teste compartilhado deve ser voluntário.

Pós-processamento opcional, rastreamento e profundidade cinematográfica permanecem próximos
passos. O primeiro alvo do efeito será Full HD/30, depois da estabilidade física da captura.
