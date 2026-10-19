# Auditoria — Noturno em 3×, 5× e 10× (S24 Ultra)

Claude, 19/10/2026, a partir do código em `claude/nova-interface` (0.29.7) e dos diagnósticos reais 0.29.1, 0.29.2 e 0.29.5 enviados pelo dono. **Medido** = número do diagnóstico do aparelho. **Hipótese** = não medido.

## 1. Caminho real da captura noturna

| Etapa | Onde | O que faz | Medido (0.29.5, 5×, bandejas) |
|---|---|---|---|
| Escolha da lente | `NovaCamera.bind` + `core/NightLensPolicy` (0.29.5) | ≥2,5×: lente física fixa (3× → ID 6; 5× e 10× → ID 7). Antes o firmware trocava 5× pela 3× ampliada no escuro | "rota solicitada=0@7", 18,6 mm/115 mm eq. |
| Plano de exposição | `core/NightExposure.plan` / `maxFrameNs` | Tempo por quadro limitado pelo tremido estimado (cresce com o zoom; OIS ajuda) | 5 quadros · 41,6 ms · ISO 2056 (teto em 5×) |
| AF | `FocusPolicy.beforeBurst` | AF no centro antes da rajada, travado | sucesso em ~750 ms |
| Captura | `NativePhotoCapture` (Camera2) | JPEG **qualidade 95** do ISP Samsung, 4080×3060 | 250 ms entre quadros (era ~1.000 ms na câmera lógica, 0.29.1) |
| Referência | `FrameQuality` | Escolhe o quadro de menor ruído/maior energia | ~330–455 ms |
| Original | `ProcessingQueue.run` | Decodifica e grava o quadro de referência (q95) | ~0,4 s |
| Alinhamento | `Engine.align` | Luma ½ resolução, campo 12×16 + subpixel, confiabilidade por par (ruído entre quadros, 0.29.2) | 1,5–1,9 s · heap até **391 MiB de 512** |
| Fusão | `Engine.mergeInto` / `Merge.mergeTile` | Blocos de 256 px, Catmull-Rom subpixel, rejeição 2σ–4,5σ, pontos isolados da referência (0.29.4) | **6,5–8,0 s, em série até 0.29.6**; rejeição 4,9% e 0,7%; 5/5 quadros |
| Acabamento | `ProcessingQueue.render` | Plano tonal + tom do Noturno + crominância de sombra por ISO + estilo. **Sem etapa de nitidez no Noturno** | 1,4–1,8 s |
| Gravação | `MediaWriter` | Base q95/98 (revisão/IA) e final JPEG | ~0,2–0,3 s cada |
| 10× | `Engine.superRes` (0.29.6) | Rajada sem recorte na tele 5×; montagem subpixel de todos os quadros (antes: recorte do ISP + fusão) | Ainda sem diagnóstico do aparelho |

Total medido: 13,5–15,8 s por foto 5×.

## 2. Causas: confirmadas × hipóteses

**Confirmadas (com número):**
1. Fusão rejeitava 58% da cena parada (0.29.1) porque o ruído medido vizinho a vizinho caía no piso no JPEG alisado da tele → corrigido em 0.29.2 (rejeição agora 0,7–4,9%).
2. Ponto isolado do ISP no quadro de referência sobrevivia à fusão → corrigido em 0.29.4 (teste sintético).
3. No escuro, 5× usava a lente 3× ampliada (69 mm f/2.4) → corrigido em 0.29.5 (lente 7 confirmada).
4. Fusão era o maior custo de tempo e rodava em um núcleo → paralelizada em 0.29.7 (abaixo).
5. Crashes nativos no Cinema (não no Noturno): tombstones de 09:20 e 09:36 abortam dentro de `ImageSegmenter.segment` (MediaPipe) e de `DepthEstimator.nativeRun` (ncnn, OpenMP), ~2 s após abrir a sessão. Causa interna dos `abort()` não identificada.

**Hipóteses (não medidas):**
- Veio de madeira e texto pequeno moles: alisamento + nitidez do ISP antes do app (JPEG q95). O app não recria o que nenhum quadro tem. Teste: comparar o "original" (quadro de referência, Galeria → Originais) com a final na mesma cena; se o original já é mole, é o ISP.
- Grão residual em cena escura: 5 quadros de 41,6 ms no teto de ISO; o limite é físico (√5 ≈ 2,2× menos ruído no máximo).
- Halos leves nas bordas: nitidez do ISP (o app não aplica nitidez no Noturno).

## 3. Perdas de informação no caminho
- **Uma geração extra de JPEG:** ISP (q95) → decodifica → processa → JPEG final. Inevitável sem RAW/YUV.
- **Sem redimensionamento** na fusão 3×/5× (resolução cheia; alinhamento em ½ só para medir).
- **10× antes de 0.29.6:** o ISP recortava e ampliava 1,96× antes do app — o detalhe perdido na ampliação não voltava. Agora a SR monta a partir de 5 quadros sem recorte.
- **Memória:** heap até 391/512 MiB no alinhamento — margem pequena para aumentar quadros ou resolução.

## 4. Propostas para o Codex (qualidade; não implementadas)
| # | Arquivo / função | Problema | Mudança proposta | Impacto esperado | Risco |
|---|---|---|---|---|---|
| A | `NovaCamera` (plano do Noturno) / `CaptureDecision.nightFrames` | Com a tele fixa a captura caiu para 250 ms/quadro; 5 quadros é o limite atual de ruído | 7–8 quadros na tele quando o giroscópio indica mão firme | Ruído √(8/5) ≈ 1,26× menor; +0,75 s de captura | Memória (391/512 MiB hoje) e tempo de fusão |
| B | Captura (`NativePhotoCapture`) | JPEG do ISP já alisado/nitidez | Rajada YUV (sem JPEG) na tele noturna, mesmo pipeline | Menos perda por compressão; o ISP ainda processa | Banda/memória; validar formato no S24 |
| C | `ProcessingQueue.render` (crominância por ISO) | Cor apagada em cena escura | Força da crominância também pelo ruído medido (não só ISO) | Cor mais viva onde a fusão já limpou | Volta de manchas de cor |
| D | RAW | Teto do JPEG | Fusão em RAW (proposta separada no STATUS 0.29.4) | Textura fina real | Semanas; cor/tom próprios |

## 5. Otimização implementada (escopo Claude) — 0.29.7
- **`Engine.mergeInto`:** leitura em série (o JPEG decodifica por faixas), cálculo dos blocos em paralelo via `Parallel` (respeita calor/economia: 1 núcleo quando quente), gravação em série na mesma ordem. `readAligned` foi dividido em `fetchAligned` (leitura) + `finishAligned` (cálculo) sem mudar contas.
- **Prova de que a imagem não muda:** `EngineAndroidTest.fusaoEmParaleloIgualAUmNucleo` compara pixel a pixel 1 núcleo × 4 núcleos (idênticos). Tempo no JVM: 653 → 428 ms (cena pequena; **não é medida do S24**).
- **Como medir no aparelho:** no diagnóstico, a diferença entre "fusão:" e "fusão temporal:" é o tempo da fusão (0.29.5: 8,0 s e 6,5 s).
- **Reverter:** voltar `mergeInto` ao laço `tiles` (commit único).

## 6. Conjunto de validação proposto
Mesmo lugar, mesma luz, tripé ou apoio, 3×/5×/10×, Noturno, com diagnóstico após cada foto:
1. Texto pequeno impresso (caixa, rótulo) a ~1,5 m.
2. Madeira com veio.
3. Tecido estampado e tecido liso.
4. Folhagem (planta) — detalhe fino e irregular.
5. Cena com sombra profunda e um ponto de luz.
6. Objeto a 30–50 cm na 5× (recuo de foco).
Comparar original × final de cada uma (detalhe, ruído, cor, artefatos) e os tempos/heap do diagnóstico.
