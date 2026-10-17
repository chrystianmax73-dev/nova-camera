# Handoff — Nova Camera (continuidade)

- **Repositório:** https://github.com/chrystianmax73-dev/nova-camera (público, GPL-3.0-or-later)
- **Branch:** `ccr-7443eaac-tqygyg` (PR #1, rascunho). Último commit: ver `git log -1` (versão 0.20.0).
- **Aparelho-alvo:** Galaxy S24 Ultra (SM-S928B). App 100% offline (sem permissão INTERNET).
- **Distribuição:** GitHub Actions → Releases `nova-test-N` (workflow `nova-release.yml`), APK assinado
  com a keystore permanente dos GitHub Secrets `NOVA_KEYSTORE_*`.

## Regras que não podem ser quebradas

1. Não criar outra keystore, não mexer na assinatura, nunca pedir ou expor chaves/senhas.
2. Não enviar fotos, vídeos ou arquivos pessoais do dono para o repositório.
3. Não adicionar permissão INTERNET nem dependência que exija rede.
4. Não copiar código proprietário (GCam, Adobe, Samsung). Código aberto só com licença compatível
   com GPL-3 (GPL, Apache-2.0, MIT, BSD) e registrado em `docs/THIRD_PARTY.md`.
5. Tom do Noturno: não mudar (decisão do dono). `keepOriginals` desligado por padrão.
6. Não declarar recurso "validado" sem teste real ou foto do aparelho. `docs/STATUS.md` separa
   **Testes** / **Compilação** / **Aparelho**.
7. Não reescrever componentes que funcionam. Mudar só o necessário.

## Arquitetura (onde mexer)

- `camera/NovaCamera.kt` — controlador CameraX/Camera2: modos, zoom, foco/exposição, captura,
  rajadas (`captureForProcessing`), super-resolução (troca de zoom para a lente sem recorte).
- `processing/ProcessingQueue.kt` + `processing/Engine.kt` — processamento em segundo plano
  (fusão, HDR, Noturno, SR, tom, retrato, estilo).
- `core/` — lógica pura e testável (políticas, tom, SR em `core/imaging/SuperRes.kt`).
- `MainActivity.kt` + `ui/ViewfinderOverlay.kt` — gestos do visor, anel de foco, auxílios Pro.
- Testes: `app/src/test/...` (JVM/Robolectric). Rodar: `gradle --offline :app:testDebugUnitTest`.

## Já existia antes desta etapa (não refazer)

- Foco por toque com anel, confirmação por cor (sucesso / falha / bloqueado), toque longo = AE/AF
  bloqueados (AWB opcional), arrastar = EV, retorno ao automático configurável (4 s / 8 s / até novo
  toque), ponto do toque usado como assunto do Retrato, ponto respeitado na captura nativa 200 MP.
- Noturno com foco travado no centro antes da rajada; exposição longa por quadro pelo giroscópio.
- Super-resolução multiquadro no 10× (8 quadros, lente 5× sem recorte) e alta resolução 2× na tele
  3×/5× (opções ligadas por padrão). Validado em foto real: texto da mangueira a 10× legível, no nível
  da câmera Samsung no detalhe.
- Auxílios Pro: histograma, alerta de estouro, focus peaking, nível (sem fluxo extra da câmera).
- CrashLog (Ajustes › Diagnóstico).

## Concluído nesta etapa (0.20.0)

Arquivos: `core/FocusPolicy.kt` (novo), `camera/NovaCamera.kt`, `camera/CameraTypes.kt`
(`onFocusCleared`), `MainActivity.kt`, `test/.../core/FocusPolicyTest.kt` (novo), `docs/STATUS.md`,
`app/build.gradle.kts` (0.20.0).

1. Antes do disparo, se o AF do ponto tocado já voltou ao contínuo, o foco é refeito no ponto
   (`FocusPolicy.Target.POINT`, espera máx. 0,7 s; Noturno 1,2 s).
2. Super-resolução e rajadas multiquadro na tele (≥ 2,5×) travam o foco no centro antes da rajada,
   como o Noturno já fazia. Na SR o foco é feito no recorte do 10× **antes** de abrir o zoom.
3. O retorno do AF ao contínuo é adiado enquanto `capturing` (nunca muda o foco no meio da rajada).
4. Mudança de zoom ≥ 15% depois do toque descarta o ponto (`dropPointIfReframed`), some o anel.
   Toque longo (bloqueio) é mantido.

**Testes:** `FocusPolicyTest` (6 casos) e suíte completa de unidade — ver resultado do CI do commit.
**Não validado no aparelho:** tempo real do AF na tele, comportamento do AF do HAL Samsung durante
`setZoomRatio` da SR (assumido: em AF_MODE_AUTO travado a lente não se move).

## Pendências (em ordem)

1. **Validar no aparelho (dono):** tocar num objeto a 5×/10×, esperar > 4 s, disparar → deve focar
   no objeto. Pinçar depois do toque → anel some. Noturno a 5× com toque → foco no ponto.
   Se o foco falhar na SR, conferir em Ajustes › Diagnóstico a linha `foco … D, AF …` (telemetria).
2. **Video Pro fecha o app:** aguardando o relatório do CrashLog (Ajustes › Diagnóstico) do dono.
   Não "consertar" no escuro.
3. **Noturno com super-resolução no 10×:** hoje a SR só roda em Foto/HDR/Zoom (`srFactor` exclui
   NIGHT). Proposta: permitir SR no Noturno só com `NightExposure.steady` e quadros ≤ 1/15 s; tomar
   cuidado com memória (8 quadros longos) e com `ThermalPolicy`. Exige teste com fotos reais.
4. **Cor amarela puxando para âmbar** na comparação com a Samsung (mangueira 10×: tom 43° × 51°).
   Antes de mexer, confirmar com o dono se Tom/Temperatura estavam em 0.
5. Item "céu/pele separados" não iniciado. Calibração de tom pede 4 pares Nova × Samsung da mesma cena.

## Como continuar sem perder trabalho

```
git fetch origin ccr-7443eaac-tqygyg && git checkout ccr-7443eaac-tqygyg
gradle --offline :app:testDebugUnitTest      # tudo deve passar antes de mudar algo
```
- Commits pequenos, mensagem em português, sempre na mesma branch; atualizar `versionName` e uma
  seção nova no topo de `docs/STATUS.md` a cada versão.
- O release sai sozinho pelo workflow ao enviar; dar ao dono o link `releases/tag/nova-test-N`.
