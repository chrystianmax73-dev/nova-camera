# Nova Camera — estado real dos recursos

Legenda da coluna **Validação**:
- **Testes**: coberto por teste unitário (lógica pura) ou pelo teste de fumaça da interface (Robolectric).
- **Compilação**: compila, passa no lint e é usado pelo app, mas o comportamento depende da câmera física.
- **Aparelho**: só pode ser confirmado no S24 Ultra / S25 Ultra. **Nada nesta versão foi testado em aparelho.**


## 0.5 — dossiê de testes de 14/10/2026 (S24 Ultra)

O que o dossiê mostrou e o que foi feito. As fotos do dossiê chegaram reduzidas (1152×1536, sem EXIF), por isso não serviram para comparar nitidez fina; a origem dos dois vídeos não está confirmada.

| Problema relatado / observado | Causa identificada | Correção | Validação | No aparelho |
|---|---|---|---|---|
| Zoom lento e "engasgando" ao tocar 3×/5×/10× | A 0.4 enviava um `setZoomRatio` **por quadro de tela** e convergia exponencialmente: requisições enfileiradas na câmera e chegada lenta | Curva com duração limitada (foto ≤ 320 ms para 1→10×; vídeo mais suave, ≤ 900 ms), ease-out em escala logarítmica, interrompível; **no máximo uma requisição em voo** (a interface acompanha a curva, a câmera recebe só o valor mais recente) | Testes (`ZoomEngineTest`: duração, monotonia, interrupção, sem empilhar) | Medir no Diagnóstico › Registro de zoom (latência e quadros perdidos) |
| Qual lente está ativa no 3×/5×/10× | Não era lido | `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` + `CONTROL_ZOOM_RATIO` de cada resultado; aviso discreto "Tele 5×" ao trocar; registro com horário | Testes (telemetria) | Confirmar se o firmware Samsung informa a lente física |
| Perda de definição/estabilidade em 5× e 10× | 10× é recorte do sensor 5×; com pouca luz o firmware pode manter outra lente e recortar. Parte disso é processamento proprietário inacessível | OIS ligada explicitamente quando a lente anuncia; a lente ativa agora aparece, para separar "recorte" de "óptica" | Compilação | Comparar 5×/10× com boa luz e com pouca luz, olhando o aviso de lente |
| Exposição saltando durante o zoom no vídeo (133446) | Medição no quadro inteiro: áreas claras saem do enquadramento ao aproximar | Vídeo sem ponto escolhido mede pela região central (opção "Exposição estável no zoom") | Compilação | Repetir 1×→10× com janela clara na borda |
| Tela preta ao ajustar ISO no Pro | A régua começava em AUTO e o primeiro arraste saltava para **ISO 50** mantendo o tempo do automático: até −5 EV (imagem preta) | Régua abre no valor que o automático usa; botão AUTO separado; medidor "±EV em relação ao automático"; sem `SENSOR_FRAME_DURATION` forçado; vigia volta ao automático se o sensor parar de entregar quadros | Testes (medidor −5 EV, posição da régua) | Ajustar ISO/velocidade em ambiente interno |
| Selfie espelhada errada | A 0.4 gravava o espelho só como orientação EXIF, ignorada por muitas galerias | Recorte + rotação + espelho **nos pixels**, mesma resolução, EXIF técnico copiado (uma recompressão JPEG 95) | Teste em pixels reais (`MirroredPhotoSaverTest`) | Abrir na Galeria Samsung |
| Resoluções reais / 200 MP | — | Opção "Resolução máxima" usa a maior saída que o aparelho libera ao app; o valor real (MP) aparece no botão. Diagnóstico mostra tamanhos de alta resolução e o modo de resolução máxima do sensor. **200 MP só se o aparelho liberar; o modo de resolução máxima do sensor exige sessão Camera2 dedicada, ainda não implementada** | Compilação | Ler o diagnóstico e o MP real no botão |
| Proporções | — | 4:3, 16:9, **1:1** e tela cheia (foto igual ao visor) | Compilação | — |
| Vídeo: resolução e FPS | — | Resolução (Automática/Máxima/4K/FHD/HD) e 24/30/60 fps **só entre os que a câmera aceita**; recuo com aviso | Compilação | Testar 4K60 e 24 fps |
| Modos adicionais | — | Painel MAIS: **Pro, Vídeo Pro, Noturno, Câmera lenta** reais; **RAW (DNG)+JPEG** no Pro quando suportado; Panorâmica, Timelapse, Food e Vídeo Retrato marcados "em breve" e não capturam | Testes (render) + Compilação | Câmera lenta 120/240 fps; DNG abre em editor |

**Não implementado nesta versão:** Retrato próprio (cabelo/transparências exigem segmentação e matting locais — próximo grande bloco), Panorâmica com stitching, Timelapse, Food, Vídeo Retrato, super-resolução 20×/30× e o modo de resolução máxima do sensor (200 MP via Camera2).

## 0.4 — base

### Implementado nesta versão

| Recurso | Como funciona | Validação | Pendente no aparelho |
|---|---|---|---|
| Nova interface | Visor 4:3 / 16:9 / tela cheia; barra superior com ícones vetoriais; barra de zoom compacta; seletor de modos em texto; obturador animado; áreas seguras (status, navegação, recorte) respeitadas | Testes (render dos modos Foto, Vídeo, Pro e Retrato indisponível) | Fluidez a 120 Hz, legibilidade sob sol |
| Foto igual ao visor (WYSIWYG) | `ViewPort` do PreviewView aplicado a todos os casos de uso | Compilação | Comparar enquadramento do visor com a foto salva |
| Controles giram com o aparelho | Interface travada em retrato (sessão não reinicia); ícones/textos giram; orientação real gravada no arquivo | Testes (mapeamento de rotação) | Foto/vídeo na horizontal |
| Foco por toque | `FocusMeteringAction` AF+AE no ponto. Após 4 s (configurável) o AF volta ao contínuo e **a medição de exposição permanece no ponto** (sem salto de exposição); 20 s depois, sem EV manual, retorna ao automático completo | Compilação | Velocidade, oscilação e acompanhamento de assunto |
| Bloqueio AE/AF | Toque longo: AF travado + `CONTROL_AE_LOCK` (e `AWB_LOCK`, opcional) via Camera2; selo "AE/AF BLOQUEADO ✕" para desbloquear; funciona durante gravação | Compilação | Confirmar que a exposição não muda ao reenquadrar |
| Exposição por gesto | Arrastar na horizontal do usuário (corrigido para aparelho deitado) ajusta EV dentro da faixa real; régua com sol ao lado do retículo; ~80 dp = 1 EV | Testes (conversão EV/passo/limites) | Sensação do gesto |
| Prioridade a rostos | `CONTROL_SCENE_MODE_FACE_PRIORITY` quando a câmera anuncia; desativada enquanto há ponto manual | Compilação | Verificar se o firmware Samsung honra o modo |
| Selfie como visualizada | Padrão ativo. Inversão gravada como orientação EXIF (sem recodificar, sem perda); opção para desativar | Compilação | Abrir na Galeria Samsung e em outro visualizador |
| Vídeo frontal espelhado | `MirrorMode` configurável separadamente | Compilação | Conferir arquivo |
| Zoom suave | Animação logarítmica por quadro; mais lenta no vídeo; pinça e arraste na barra passam pelo mesmo filtro; paradas só as alcançáveis | Testes (curvas e mapeamento) | Saltos na troca física de lentes durante gravação |
| Troca de câmera física | Se outra câmera exposta cobrir melhor a parada (ex.: ultra-angular separada), reabre nela; fora da gravação | Testes (seleção) | No S24U a câmera lógica cobre 0,6–10× (visto no print), então não deve haver troca |
| Modos OEM | Retrato/Noite/HDR usam extensão do fabricante **só se disponível**; caso contrário o modo aparece esmaecido com explicação e **não captura** | Testes (resolução de modos) | Ler o diagnóstico de cada aparelho |
| Noite básica (própria) | Sem extensão Noite: permite exposições mais longas (faixa de fps de AE com mínimo < 15). **Não há fusão multiquadro** | Testes (escolha da faixa) | Ganho real em pouca luz versus Foto |
| Pro | ISO, velocidade, foco manual, balanço de branco e EV reais via Camera2; parâmetros não suportados ficam desativados; mostra os valores do automático | Testes (escalas) + Compilação | Exposições longas, foco mínimo |
| Vídeo | Qualidade automática/4K/FHD/HD conforme suporte; 60 fps quando a combinação é aceita (senão recua para 30 com aviso); estabilização anunciada; pausa/retomada; aviso de pouco espaço | Compilação | 4K60, estabilização, 5–10 min contínuos |
| Flash | Automático/ligado/desligado; flash de tela para selfie; lanterna no vídeo | Compilação | Exposição com flash |
| Temperatura e energia | Eventos do Android (sem polling). Selo só em aquecimento **severo**, com contexto "carregando". Economia de energia ou aquecimento severo: 4K cede a Full HD; crítico: finaliza a gravação com integridade. Pausa a câmera após inatividade (padrão 2 min) | Testes (política térmica) | Medições com e sem carregador |
| Diagnóstico | Lentes físicas e focais equivalentes, extensões Camera2 (API 31+) e CameraX, faixas de ISO/exposição/EV/fps, estabilização, qualidades de vídeo, estado térmico | Compilação | Enviar relatório de cada aparelho |
| Configurações | Foto, Vídeo, Foco e exposição, Energia, Interface, Diagnóstico e privacidade | Compilação | — |

## Ainda NÃO implementado (não há botão fingindo que existe)

- Retrato próprio (segmentação/profundidade locais e desfoque gradual).
- HDR por fusão de múltiplas exposições e detecção de fantasmas.
- Noite multiquadro (alinhamento + redução de ruído temporal).
- Super-resolução 10×/20×/30×; pelo print do S24 Ultra, a câmera exposta vai até algo entre 10× e 20×.
- Editor pós-captura com comparação Original × Aprimorado; pós-processamento de vídeo.
- RAW/DNG, histograma, focus peaking, indicação visual de rostos detectados.

Ordem proposta: (1) confirmar no diagnóstico quais extensões Camera2 o S24U/S25U expõem — se BOKEH/NIGHT/HDR existirem
via Camera2 mesmo sem CameraX, implementar a sessão de extensão Camera2; (2) HDR por bracketing; (3) Noite multiquadro;
(4) Retrato com segmentação local; (5) editor com original preservado.

## Roteiro de teste físico — 0.5 (S24 Ultra, depois S25 Ultra)

1. Zoom em foto: tocar 1→3→5→10→3→1 rápido. Deve responder de imediato, sem engasgo. Observar o aviso "Tele 3×/5×".
2. Mesmo percurso gravando vídeo 1080p30 e 4K30. Depois: Configurações › Relatório técnico › compartilhar (inclui o **Registro de zoom**: latências, trocas de lente e quadros perdidos).
3. Vídeo com janela clara na borda: aproximar 1×→10×; a exposição deve variar menos que no 133446.
4. Pro: mover ISO em ambiente interno — a imagem não deve ficar preta; observar o medidor de EV; tocar AUTO para voltar.
5. Selfie com objeto à direita: continua à direita na Galeria.
6. Resolução: tocar no botão "12 MP" e anotar o valor máximo que aparece; capturar e conferir as dimensões.
7. MAIS › Câmera lenta: gravar e conferir a reprodução lenta; Vídeo Pro com ISO manual.
8. Pro › RAW: conferir que um .dng aparece junto do .jpg.

## Roteiro de teste físico — 0.4 (S24 Ultra, depois S25 Ultra)

Teste **sem carregador** primeiro e anote se estava carregando.

1. Instalar por cima da versão anterior (mesma assinatura) e abrir. Conferir que nada fica sob a barra de status ou de navegação.
2. Configurações › Diagnóstico › Compartilhar: enviar o texto.
3. Foto 1×: tocar num objeto próximo e depois num distante (o foco muda?). Arrastar na horizontal (exposição muda e para nos limites?).
4. Toque longo: aparece "AE/AF BLOQUEADO"; reenquadrar para algo claro/escuro — a exposição não deve mudar. Tocar no selo para desbloquear.
5. Selfie com texto/objeto à direita: na Galeria deve continuar à direita. Repetir com a opção desligada.
6. Fotos na horizontal (traseira e frontal): orientação correta na Galeria.
7. Proporção 4:3, 16:9 e Tela: a foto salva tem exatamente o enquadramento do visor?
8. Vídeo 30 s: zoom 1→3→5→10 pela barra e pela pinça; anotar saltos na troca de lente. Pausar/retomar. Sair do app gravando: arquivo deve abrir.
9. Modos Retrato/Noite: aparecem disponíveis ou esmaecidos? Se Noite básica, comparar com Foto em ambiente escuro.
10. Pro: fixar ISO e velocidade; foco manual perto/infinito.
11. Gravar 5–10 min em 4K: anotar se surge o selo de aquecimento e se estava carregando.

Formato de retorno: modelo, item, resultado (ok/falha), observação e, se houver, o relatório de diagnóstico. Não é necessário enviar fotos pessoais.
