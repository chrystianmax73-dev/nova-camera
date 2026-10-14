# APKs privados sem acúmulo de artifacts

A validação por push apenas compila com Actions; **não faz upload do APK**. 
O workflow manual **Nova Camera - APK assinado** publica uma prévia em Releases do repositório privado, sem upload de Actions artifacts. Conserva as duas prévias mais recentes.

Releases **ocupam espaço** no GitHub; apenas evitamos armazenar artifacts separadamente.

## Assinatura permanente
Instale keytool em ambiente seguro e gere uma keystore, mantendo cópia privada:
```bash
keytool -genkeypair -keystore nova-upload-key.jks -alias nova -keyalg RSA -keysize 3072 -validity 10000
base64 -w 0 nova-upload-key.jks
```
Em **Settings > Secrets and variables > Actions**, registre:
- NOVA_KEYSTORE_BASE64: resultado base64 sem quebras de linha
- NOVA_KEYSTORE_PASSWORD: senha da keystore
- NOVA_KEY_ALIAS: nova
- NOVA_KEY_PASSWORD: senha da chave

**Nunca cole os segredos no chat nem no código. Não perca a keystore; a continuidade da assinatura depende dela.**

Execute Actions > Nova Camera - APK assinado > Run workflow.
O APK assinado estará em **Releases** do projeto privado após sucesso.
O processo automático não expõe o APK ao público enquanto o repositório permanecer privado.

No GitHub, configure **Settings > Actions > General > Retention** com período curto (por exemplo, 1 dia) se desejar limitar logs e execuções armazenadas. 
