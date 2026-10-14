# Nova Camera 📷
**Câmera Android premium e privada — fotografia computacional, retrato, HDR, zoom e vídeo.**

**Repositório:** https://github.com/chrystianmax73-dev/nova-camera  
**Situação em 14/10/2026:** protótipo experimental, NÃO validado em dispositivos; nenhuma promessa de ganho de qualidade sobre a câmera Samsung.  
**Dispositivos de referência:** Galaxy S24 Ultra e Galaxy S25 Ultra, Android atual de cada aparelho.  
**Visibilidade:** manter PRIVADO durante desenvolvimento; publicação futura somente após auditoria, testes e licenciamento.  
**Nome comercial:** **Nova Camera**. Identidade visual independente, sem se passar por Samsung, Apple, Google ou Vivo.

## 1. Visão do produto

Construir **um aplicativo de câmera realmente funcional, fluido e confiável**, que extraia resultados realistas e naturais do hardware, com pós-processamento opcional local. Aprender com a experiência e princípios de Samsung Galaxy S Ultra, Pixel, iPhone Pro e vivo X300 Pro/ZEISS — **sem copiar código, algoritmos proprietários, ícones, marcas ou interface literalmente**.

**Pilares, em ordem:**
1. Captura confiável (o botão sempre tira a foto, grava e salva na galeria).
2. Precisão: cores de pele, contraste, foco, detalhes reais, bordas e baixas luzes.
3. Vídeo com zoom durante a gravação: transições suaves, estabilização, áudio e consistência da exposição.
4. Eficiência: não sacrificar bateria, temperatura, duração da gravação ou responsividade.
5. Design premium e controles ergonômicos, avançados quando necessário e simples por padrão.
6. Privacidade: processamento e mídia **no próprio celular**, sem conta, anúncios ou telemetria remota.

**Importante:** ampliar pixels com IA não recupera detalhes que o sensor não capturou. Não inventar placas, letras, traços faciais ou objetos e afirmar que são fiéis. Se houver aprimoramento generativo opcional no futuro, identificá-lo claramente e preservar o original.

## 2. Resultado desejado e expectativas

- Em zoom **10x**: usar qualidade da câmera Samsung nativa como **referência comparativa**, não como garantia. Estudar fusão multiquadro, estabilização, controle de ruído, foco e escolha da lente efetivamente exposta às APIs.
- Em **20x e 30x**: experimentar super-resolução e detectar quando NÃO há informação útil; permitir desligar refinamento.
- Em **vídeo com zoom**: reduzir oscilações e saltos de foco/cor/exposição ao ampliar, controlar zoom por gestos e por botões; privilegiar imagem consistente sobre números altos.
- Em **retrato**: bokeh natural regulável antes e, idealmente, depois da foto; cabelo, óculos, orelhas, objetos finos e bordas sem recortes grosseiros.
- Em **contraluz**: proteger céu e luzes, preservar rosto natural, evitar HDR exagerado/halos; adaptar EV/foco e HDR real quando disponível.
- Em **noturno**: equilibrar tempo de exposição, movimento e ruído sem produzir aspecto de pintura.
- Em **vídeo**: processamento moderado em captura; tratamentos pesados (estabilização extra, redução de ruído temporal, correção de cores) **após finalizar** e apenas sob demanda. Preservar original e permitir comparação lado a lado.

## 3. Modos e funções — especificação

### FOTO (prioridade P0)
- Fotos JPEG locais, visualização contínua, toque para foco/medição, exposição manual via gesto/slider e indicação do estado.
- Alternar frontal/traseira, proporção 4:3 / 16:9 / 1:1 e tela cheia se suportado, temporizador, grade, linha do horizonte, flash/lanterna conforme câmera, resolução disponível e indicador de HDR.
- Botão obturador tátil, feedback discreto, miniatura de galeria, gesto de pinça, trocar lentes apenas quando suportado e sem travar.
- Preservar metadados úteis (orientação, EXIF) sem gravar localização por padrão.

### RETRATO (P1)
- Tentar extensão OEM BOKEH (CameraX Extensions) se disponível, sem pressupor suporte em todos os dispositivos/lentes.
- Fornecer intensidade de desfoque **somente se** o fabricante expuser o controle; não mostrar um slider falso/inoperante.
- Depois evoluir para segmentação e mapa de profundidade local, desfoque gradual com correções de borda, rostos e objetos.
- Testes: cabelo, luz de fundo, roupas, pessoas/animais, transparências, múltiplas pessoas, movimento, pouca luz.

### HDR / CONTRALUZ (P1)
- Extensão HDR do fabricante quando disponível; fallback de foto normal indicando restrição.
- Futuro: bracketing/fusão de exposições se suportados; detectar fantasmas e movimentos.
- Comparar recuperação das sombras com preservação dos realces; tons de pele sempre prioritários.

### NOTURNO (P1)
- Extensão NIGHT do OEM se exposta; fallback seguro.
- Futuro: multiquadro adaptativo com detecção de movimento, redução temporal de ruído e balanço de branco.
- Diferenciar cenas estáticas de pessoas/animais em movimento; evitar falsos detalhes.

### ZOOM / TELE (P1 + pesquisa P2)
- Controles de 0,6x, 1x, 3x, 5x, 10x, 20x, 30x **apenas quando suportados**, sem inventar acesso a zoom óptico onde não houver.
- Gestos graduais com limites reais reportados por CameraControl/Camera2; minimizar saltos e pulos.
- Experimentos 10x foto primeiro; 20x/30x depois. Identificar lente física realmente acessível; API pública pode impedir 50/200 MP ou RAW de certos módulos.
- Guardar imagens originais e métricas para comparações. Não forçar super-resolução ao visualizar preview.

### VÍDEO (P0)
- Gravar localmente em MP4 com microfone autorizado; permitir negar mic e gravar sem áudio de forma explícita.
- Disponibilizar 1080p e 4K, 24/30/60 fps e HDR **somente após enumeração das combinações compatíveis**. Não prometer 4K60 em qualquer câmera/zoom.
- Zoom durante vídeo, foco contínuo, AE estável, controle de exposição, anti-flicker, suporte a orientação, tamanho da gravação/tempo decorrido.
- Interrupções: chamadas, app em segundo plano, bloqueio, falta de espaço e aquecimento devem finalizar arquivos com integridade.
- Pós-processamento após gravação será assíncrono no próprio aparelho (tarefa controlada, cancelável e com indicação de progresso), opcional para não bloquear a câmera.

### CINEMA / PRO / EXTRAS (P2-P3)
- Cinema: troca de foco com transição suave e simulação de profundidade de vídeo somente com desempenho/qualidade comprovados.
- Pro foto: ISO, tempo de exposição, foco manual, WB, EV, histograma, foco peaking, RAW/DNG se suportado.
- Pro vídeo: bitrate, fps, codec/10-bit/log se disponível publicamente, estabilização e microfone.
- Panorama, timelapse, slow motion, macro, documentos e dual view: avaliar caso a caso, sem inflar interface nem gerar modos sem suporte.

## 4. Design e experiência — orientação visual

**Identidade:** nome Nova Camera. Dark premium: preto azulado #070D19, superfícies #142338, azul elétrico/ciano #48BFFF e #84DCFF, branco suave #EDF7FF. Hierarquia tipográfica limpa, discreta, sem copiar One UI ou iOS. Ícone único inspirado em diafragma/objetiva, com geometria própria, sem marcas Apple/Samsung. O ícone vetorial atual é um placeholder funcional; aprimorar preservando a identidade.

**Tela principal:**
- Preview do sensor ocupa o máximo de espaço com proporção correta, sem recortes inesperados; HUD em sobreposição translúcida e ícones de alto contraste.
- Barra superior: flash, HDR, timer, proporção, resolução, grade, ajustes. Ocultar funções não suportadas e mostrar explicação acessível.
- Área central: retículo de toque para foco, indicador de exposição/AE-AF lock, controles de EV sob demanda.
- Barra de zoom: 0,6x/1x/3x/5x/10x e progresso fluido por gesto; destaque da distância ativa; 20x/30x conforme exposição da API e qualidade.
- Barra inferior: alternância de modos FOTO, RETRATO, VÍDEO, NOITE, PRO etc.; botão capturar/gravar central proeminente; trocar câmera; abrir galeria.
- Tela de edição local pós-captura: **Original vs Aprimorado**, intensidade, desfazer e salvar cópia; nunca sobrescrever silenciosamente.
- Animações rápidas e sutis (objetivo: interface 60 fps quando o hardware comportar), feedback háptico configurável, suporte a leitor de tela e alvos de toque adequados.
- Layout adaptar-se a orientações e recortes de tela; **não ficar travado em retrato para sempre**. Preview responsivo a vídeo horizontal.
- Configurações: formato, resolução, HDR, som do obturador respeitando leis locais, bateria/performance, processar em segundo plano quando habilitado, diretório/galeria, privacidade e diagnóstico.

**Regra de UX:** não imitar qualidade inexistente. Se uma configuração for incompatível, apresentar a opção indisponível com motivo; jamais exibir controles decorativos que não fazem nada.

## 5. Arquitetura técnica

- Kotlin nativo, Android SDK, CameraX para Preview/ImageCapture/VideoCapture e Camera2 para descoberta de capacidades e controles de baixo nível, conforme suporte público.
- CameraX Extensions OEM para Bokeh/HDR/Noite **quando** existirem; fallback normal sem travamento.
- Evoluir gradualmente para UI Jetpack Compose ou componentes equivalentes com estado unidirecional, preservando câmera funcional durante migração.
- Separação sugerida: camera (hardware/sessão), capture (foto/vídeo), processing (pipeline local), diagnostics, permissions, ui, settings, gallery; logs técnicos sem dados sensíveis.
- Processamento pesado local com GPU/NPU apenas onde houver APIs/operadores compatíveis; fallback CPU e limites térmicos. Não baixar modelos enormes silenciosamente.
- NUNCA embarcar keys, senhas, dados privados, fotos ou certificados em Git. Sem internet/permissão de rede desnecessária.
- **Tamanho-alvo:** 50–90 MB para APK instalável, apenas meta de otimização; medir de verdade a cada build. Modelos IA podem ultrapassar.
- **Bateria:** idle preview sem pós-processamento pesado; intensidade por modo Econômico/Equilibrado/Máximo; monitorar térmica do SO; reduzir qualidade/profundidade de processamento sob aquecimento ou poupança. Nunca alegar consumo menor sem medições.
- QA: testes unitários de lógica + testes instrumentados onde viável, logcat, diagnósticos exportáveis pelo usuário sem auto-upload.

## 6. Estado do repositório (não confundir proposta e implementação)

A base atual (v0.3.0 experimental) tem: tela de câmera com CameraX, captura JPEG, vídeo MP4, zoom via CameraControl, foco por toque, alternância frontal/traseira, checagem de extensões OEM, slider Bokeh se disponível, indicadores térmico/bateria, diagnóstico Camera2, ícone vetorial e workflows GitHub Actions.

**Ainda não foi testada em S24 Ultra/S25 Ultra. Não existe pós-processamento super-resolução, fusão multiquadro própria, IA de retrato ou edição pós-vídeo.** Design atual é protótipo e precisa ser redesenhado com qualidade premium.

**Registro de compilação:** os runs 1–4 do GitHub Actions falharam. O run 38562463474 (14/10/2026) ainda falhava em compileDebugKotlin: o import de `ImageCaptureException` foi adicionado, mas o código continuava referenciando `ImageCapture.ImageCaptureException` (classe inexistente). Corrigido na branch `ccr-7443eaac-tqygyg`, junto com: rotação correta de foto/vídeo com tela travada em retrato, gravação finalizada ao sair do app (`onStop`), zoom por pinça (também durante a gravação), tempo decorrido da gravação, bloqueio do diagnóstico durante gravação e áudio desligado com segurança se o microfone for revogado. CI agora roda também testes unitários (`ZoomPresetsTest`) e Android lint. Status de cada run: ver Actions; não presumir APK sem Release publicado.

## 7. Fluxo GitHub / distribuição privada

- Repositório PRIVATE. Branch principal main; alterações maiores preferencialmente em branch/PR, com commits pequenos e verificáveis.
- Workflow Android - compilar sem guardar APK: CI de compile, não publica APK nem Actions artifacts.
- Workflow Nova Camera - APK assinado: disparo manual, requer Secrets NOVA_KEYSTORE_BASE64, NOVA_KEYSTORE_PASSWORD, NOVA_KEY_ALIAS e NOVA_KEY_PASSWORD; publica APK de teste como GitHub Release no repositório privado e mantém apenas as duas prévias mais recentes.
- **ATENÇÃO:** Releases ocupam armazenamento mesmo sem Actions artifacts. Manter poucos releases e não versionar APKs no Git.
- A chave de assinatura Android precisa ser **persistente**. **Não inventar/recriar a cada build, nem pedir senha ou chave no chat.** Se os Secrets não estiverem configurados, orientar o proprietário a criar e guardar a keystore com backup seguro e cadastrar no GitHub UI. Não publicar APK de teste não assinado como se fosse versão definitiva.
- Guia de assinatura: docs/RELEASES.md. Roteiro técnico: docs/ROADMAP.md. Instruções para agente: docs/CLAUDE_HANDOFF.md.
- Código aberto futuro: revisar origens de terceiros, licenças (inclusive GPL quando aplicável), marcas, segurança, documentação e licença escolhida pelo dono. Não mudar o repositório para PUBLIC sem autorização expressa.

## 8. Matriz de validação (dois aparelhos, mesmos cenários)

| Cenário | S24 Ultra | S25 Ultra | Critério |
|---|---|---|---|
| Instalar/abrir/permissões | PENDENTE | PENDENTE | Sem crash, avisos claros |
| Foto 1x JPEG/galeria | PENDENTE | PENDENTE | Arquivo íntegro, orientação correta |
| Vídeo 1080p com áudio | PENDENTE | PENDENTE | Salvo, reproduzível, áudio sincronizado |
| Zoom durante gravação 1x→5x→10x | PENDENTE | PENDENTE | Sem travar; anotar mudanças na lente |
| 4K e demais resoluções | PENDENTE | PENDENTE | Habilitar só formatos suportados |
| Retrato (cabelo/óculos) | PENDENTE | PENDENTE | Sem recorte incorreto gritante |
| HDR contra sol e janela | PENDENTE | PENDENTE | Tons de pele e céu naturais |
| Noturno estático e pessoa em movimento | PENDENTE | PENDENTE | Nitidez/ruído sem borrões artificiais |
| Térmico, bateria e 5–10 min vídeo | PENDENTE | PENDENTE | Sem degradação severa/crash |
| Troca de câmera, rotação e falta espaço | PENDENTE | PENDENTE | Feedback correto e arquivos íntegros |

Comparação justa com câmera Samsung: mesma cena, luz, enquadramento, lente/fator de zoom e movimento; anotar EXIF/tempo; avaliar nitidez real, ruído, halos, bordas, precisão de cor e estabilização. Registrar desempenho/consumo com medições, não promessas.

## 9. Ordem de trabalho para o próximo agente (Claude)

1. Clonar/ler o repositório privado por **conexão GitHub autorizada** (link sozinho não concede acesso a repositório privado).
2. Conferir status de CI e corrigir erros reais de Gradle/Kotlin sem remover funcionalidades. Rodar CI até verde.
3. Auditar permissões, camera binding e fluxo de gravação (incluindo stop, retorno da activity, pausa e áudio negado).
4. Preparar primeiro APK de teste por Release privado **somente depois** da chave de assinatura persistente estar cadastrada. Caso isso bloqueie, documentar passo exato que o proprietário deve executar; não falsificar resultado.
5. Fazer primeiro ciclo de diagnóstico em S24 Ultra, depois S25 Ultra; não afirmar testes físicos sem relatórios.
6. Em seguida aperfeiçoar UI premium e controles essenciais, priorizando estabilidade, retrato/HDR, zoom de vídeo e bateria; planejar super-resolução avançada só após benchmarks.
7. Ao final de cada entrega: listar arquivos/commits, URL do Actions e do APK real (se existir), funcionalidades comprovadas, falhas, teste que o proprietário deve realizar e próximos passos.

**Primeira entrega mínima:** build Android verde + APK instalável assinado (se assinatura cadastrada), foto salva, vídeo salvo e zoom de vídeo funcional nas condições suportadas; diagnóstico para cada aparelho. Só então declarar MVP.

## 10. Restrições e princípios de desenvolvimento

Não modificar projetos não relacionados (especialmente NEXO). Não usar credenciais no código. Não criar pagamentos, backend ou serviços pagos. Não usar servidor para tratar fotos/vídeos. Não prometer 200 MP/RAW/HDR10/4K60 em lentes inacessíveis via Android, nem cópia exata da câmera OEM. Evitar refatorações de alto risco sem testes. Garantir original sempre disponível quando houver processamento. Solicitar autorização antes de tornar público, gerar cobrança, apagar releases fora da rotina prevista ou mudar assinatura.
