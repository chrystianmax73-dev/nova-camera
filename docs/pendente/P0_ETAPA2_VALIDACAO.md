# P0 etapa 2 — evidências, limites e teste físico

## Evidência executada no servidor

Base remota e14795c8a94863ae044c7d7394a403b8665da13f, CI39087054435 aprovado antes de modificar o aplicativo. Baseline físico fornecido pelo dono em P0_ETAPA2.md. Não há S24 conectado nem fotos novas anexadas nesta etapa.

CI39088782041, fonte3bf88352c1ac7d000c438ce2475914178d799336: assembleDebug, testDebugUnitTest e lintDebug aprovados. O teste compila e executa os núcleos JNI no Linux e compara decisões/pixels exatamente com Kotlin: alinhamento/regiões/bordas, suavização/deslocamento fracionário, fusão de oito quadros com ruído/movimento/cores e detalhe/pele/margens. Não equivale a execução JNI arm64 no S24. C++ arm64 compilado pelo NDK no mesmo CI.

Benchmark executado nesse job, três operações por grupo após aquecer os dois caminhos:

| Operação | Kotlin (ms/grupo) | JNI (ms/grupo) |
|---|---:|---:|
| Busca de alinhamento | 229,113 | 158,048 |
| Fusão SR de bloco | 217,378 | 47,057 |
| Deslocamento SR | 36,927 | 29,190 |
| Detalhes | 53,707 | 28,172 |

São operações pequenas no servidor compartilhado, não tempos de uma fotografia, nem medidas de energia/GPU/autonomia. Não extrapolar os fatores para a captura de 49 MP. A mudança mantém ordem de acumulação, amostras, pesos e rejeição de movimento; não utiliza fast-math nem contração FMA. Kotlin permanece referência/fallback.

A primeira compilação39088522688 falhou porque as exceções C++ estavam desabilitadas. A opção -fexceptions foi aplicada só ao novo fonte para permitir converter bad_alloc em OutOfMemoryError. Não houve aprovação fictícia dessa tentativa.

## Memória e qualidade

Sete mapas float de 2040×1530 representam 87.393.600 bytes (~83,3 MiB). SR não os consultava, mas ficavam retidos enquanto sua saída era construída. Agora a confiabilidade continua medida para decidir quadros; os mapas não são guardados nesse caminho. Fusão comum/HDR continuam com mapas. Regressão adicional compara pixels SR e quadros aceitos com/sem retenção; consultar execução final.

Não alterar oito quadros, escala escolhida ou algoritmos para caber à força. Se, mesmo após liberar temporários, faltar memória para a escala, guardar original e informar que a alta resolução não foi produzida. Não entregar 28 MP como sucesso de uma seleção49 MP. As métricas novas registram heap Java/nativo por etapa (não PSS/pico) e contagem/tempo/cache de faixas JPEG. O PSS amostrado por SessionPerformance continua separado. Margem das faixas não foi reduzida às cegas.

Se a temperatura já estiver severa, adiar a captura de alta qualidade explicitamente antes dos oito pedidos; se entrar nesse estado depois da aquisição, original preservado em vez de processar só um quadro e anunciar alta resolução. Outros perfis e tom do Noturno preservados.

## Aquisição, foco e prévia: limites ainda abertos

Não há comprovação de que os7,4–9,1s venham de uma única espera da HAL/AF. A instrumentação distingue solicitação/início/entrega/cópia, timestamp entre buffers e AF. Falta o diagnóstico completo anunciado pelo dono. Não foram enfileirados oito pedidos CameraX em paralelo nem ativado ZSL: risco de memória, reprocessamento e histórico de travamento.

AF dispensado agora tem motivo, diferente de pedido não suportado; o ponto só fica confirmado com sucesso verdadeiro. Regiões AE não são confirmação de AF. Callback de STILL_CAPTURE registra a óptica da foto quando disponibilizada pela HAL; EXIF da referência não prova ID físico. Campo nulo significa não informado.

A proteção limitada da rajada evita que uma pausa legítima na prévia provoque reabertura durante JPEG. Sem entrega por10s (ou exposição longa+3s), ou ultrapassado orçamento total, o vigia volta a poder recuperar. Testes verificam término e prazo total não renovável. Congelamentos fora dessa janela, inclusive sem efeitos/análise, NÃO são considerados resolvidos; coletar sinal de resultados e contexto antes de atribuir ao GL.

## Protocolo no S24 Ultra

1. Guardar diagnóstico e JPEGs originais da0.25.3 localmente. Fotos pessoais não entram no Git. Manter configurações, mesma cena, luz, distância,5x e alta resolução; registrar assinatura/HDR/estabilização e câmera usada.
2. Testar fora do carregador, sem resfriamento artificial durante o ensaio, temperatura inicial semelhante e estado térmico registrado. Não comparar uma corrida perto do ar-condicionado a outra aquecida. Interromper o ensaio pesado se o Android indicar aquecimento severo.
3. Fazer pelo menos três Retratos e três Fotos em5x com alta resolução, aguardando terminar a fila entre disparos. Registrar cada uma, sem escolher só a mais rápida. Guardar JPEG final e original localmente.
4. Comparar aquisição dos oito quadros, pedido→início→entrega, intervalos dos buffers, alinhamento, SR, detalhe/tons/crominância/desfoque, codificação, total, dimensões, quadros aceitos, PSS/CPU, temperatura e avisos. Conferir preview e novo disparo após aquisição.
5. Comparar detalhes, ruído, folhas/cabelo/bordas, pele, halos, fantasmas e textura no mesmo tamanho de visualização. Um sintético e uma foto isolada não comprovam qualidade ou taxa de sucesso real.
6. Regressão: foco tocado e retorno ao automático em3x/5x/10x, Retrato frontal/traseiro, interrupção por sair/voltar, troca de modo/lente após rajada, vídeo comum/assinatura/cinema em perfil autorizado. Não reinterpretar sucesso de CI como validação física.

## Energia: como confirmar a escala

O app registra CURRENT_NOW, CURRENT_AVERAGE, CHARGE_COUNTER, ENERGY_COUNTER, tensão, carga, temperatura, modalidade e tempo. Mantém hipóteses de unidades API explícitas; nenhuma reescala1000 por magnitude. Potência confiável permanece indisponível sem calibração externa.

Em trechos contínuos sem carregar, cruzar tendência de NOW/AVERAGE com Δcarga/Δtempo e Δenergia/Δtempo quando os contadores forem suportados. Verificar monotonicidade, resolução e sinal; contador parado/ausente não é consumo zero. A queda percentual é grosseira e requer intervalo longo, não confirma sozinha a escala.

Se houver ADB autorizado pelo dono, coletar localmente `adb shell dumpsys battery` e `adb shell dumpsys batterystats --charged com.novacamera.s24` antes/depois de ensaios comparáveis. Não resetar BatteryStats, não solicitar root/permissão de sistema/segredos e não publicar logs pessoais. Bateria mede o aparelho inteiro; CPU e estimativas BatteryStats não isolam a energia do compositor. Ausência de medição GPU continua explícita.

Comparações separadas: vídeo comum, rastreamento, compositor sem máscara, profundidade ativa e acabamento posterior, mesma duração/FPS/resolução/luz/tela/conexão/temperatura inicial. Não declarar melhoria de autonomia antes dessa comparação.
