# Nova Camera — roteiro de desenvolvimento

A meta é compatibilidade inicial com **Galaxy S24 Ultra e S25 Ultra**, não reprodução de código proprietário Samsung, Apple, Google ou vivo.

## Protótipo (v0.3)
- Captura local de fotos JPEG e vídeos com CameraX.
- Zoom em foto e durante vídeo, limitado pela API pública do dispositivo.
- Extensões OEM BOKEH (Retrato), HDR e NIGHT quando oferecidas à aplicação.
- Desfoque regulável somente se o dispositivo disponibilizar controle da extensão.
- Diagnóstico das capacidades Camera2 e sensores.
- Sinalização térmica, ajustes conservadores durante aquecimento.
- Sem nuvem, upload ou IA generativa.

## Para validar nos aparelhos
- Permissões, abertura, salvar foto e vídeo com áudio.
- Resoluções e IDs físicos; quais lentes 3x/5x são de fato acessíveis.
- Zoom durante gravação: transição entre módulos, estabilidade e limite.
- Retrato: bordas de cabelo, objetos transparentes, contraluz.
- HDR: exposição no rosto e céu simultaneamente.
- Aquecimento, bateria, interrupções, atividades em segundo plano.

## Evolução condicionada a métricas
1. Captura RAW/YUV quando disponível, fusão multiquadro e análise de movimento.
2. Super-resolução fotográfica 10x, 20x e 30x, respeitando fidelidade real.
3. Pós-processamento opcional de vídeo: estabilização, ruído temporal e cor.
4. Retrato proprietário, RAW, controles manuais, HDR10 e vídeo Pro quando compatíveis.
5. Design premium completo, gestos, acessibilidade e perfis fotográficos.

**Não garantido:** 200 MP, RAW em qualquer lente, 4K60 em todas as câmeras, vídeo retrato ou pipeline privado do fabricante.

## Privacidade e licença
Não adicionar mídia pessoal, arquivos de assinatura, segredos ou modelos grandes ao repositório.
Revisar dependências e licenças antes da abertura ao público.
