# Nova Camera 0.4 — estado real dos recursos

Legenda da coluna **Validação**:
- **Testes**: coberto por teste unitário (lógica pura) ou pelo teste de fumaça da interface (Robolectric).
- **Compilação**: compila, passa no lint e é usado pelo app, mas o comportamento depende da câmera física.
- **Aparelho**: só pode ser confirmado no S24 Ultra / S25 Ultra. **Nada nesta versão foi testado em aparelho.**

## Implementado nesta versão

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

## Roteiro de teste físico (S24 Ultra, depois S25 Ultra)

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
