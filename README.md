# Island (Dynamic Island para Android)

App Kotlin + Jetpack Compose que desenha uma "ilha" estilo iPhone por cima do furinho da câmera.
Lê notificações do sistema (acesso a notificações) e mostra: música (com controles), chamadas (atender/recusar),
timers, downloads/progresso e avisos normais (expande 5 s e recolhe). Duas atividades = ilha dividida.

## Gerar o APK

### Opção A — GitHub (sem instalar nada)
1. Crie um repositório novo no GitHub e suba TODO o conteúdo desta pasta (inclusive `.github/`).
2. Aba **Actions** → "Build APK" → roda sozinho (ou **Run workflow**).
3. Quando terminar, abra a execução → **Artifacts** → baixe `Island-debug-apk`, extraia e instale o `app-debug.apk`.

### Opção B — Android Studio
Abra a pasta, espere o sync e use **Build → Build APK(s)**. O arquivo sai em `app/build/outputs/apk/debug/`.

## Instalar no Galaxy S25 FE
1. Instale o APK (permita "instalar apps desconhecidos" para o navegador/gerenciador de arquivos).
2. Abra o app e siga os 4 passos na tela (configurações restritas → sobreposição → acesso a notificações → bateria).
3. Ajuste a "Altura da ilha" até cobrir o furinho e use os botões de teste.
