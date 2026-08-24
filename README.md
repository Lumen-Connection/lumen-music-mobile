<div align="center">

<img src="app/src/main/res/drawable-nodpi/logo_lumen_music.png" width="120" alt="Lumen Music">

# Lumen Music Mobile

**Seu player de música local, agora no Android — e sincronizado com o PC.**

[English](#english) · [Português](#português)

</div>

---

## Português

O **Lumen Music Mobile** é a versão Android do [Lumen Music](https://github.com/Lumen-Connection/lumen-music),
o player de música desktop da Lumen Connection. Mesma biblioteca, mesmas playlists,
mesmas paletas — e um recurso que só existe aqui: **sincronização por Wi-Fi com o
seu computador**, que funciona como servidor e fonte da verdade da biblioteca.

Local-first, sem contas, sem anúncios e **sem telemetria de nenhum tipo**.

### Funcionalidades

- **Biblioteca local** — importe áudio do aparelho (`opus`, `webm`, `m4a`, `mp3`, `ogg`, `oga`, `flac`, `wav`, `aac`)
- **Playlists** com capa em gradiente ou imagem, mosaico 2×2, reordenação por arrastar e 6 modos de ordenação
- **Curtidas**, busca global que ignora acentos, e histórico de reprodução
- **Player completo** — fila de contexto + fila manual "a seguir", aleatório sem repetição, repetir uma/todas, e retomada de sessão exatamente de onde você parou
- **Reprodução em segundo plano** com notificação, tela de bloqueio e controles Bluetooth
- **Downloads do YouTube** e importação de playlists do Spotify e do YouTube
- **Sincronização com o desktop** pela rede local: metadados, playlists e os arquivos de áudio das playlists que você escolher
- **6 paletas** (Lumen, Vinil Quente, Oceano, Floresta, Roxo Noturno, Cinza Moderno) × claro/escuro/alto-contraste × 2 densidades, com opção de reduzir movimento
- **Português e inglês**

### Instalação

Baixe o APK mais recente em [Releases](https://github.com/Lumen-Connection/lumen-music-mobile/releases).
Escolha o arquivo da arquitetura do seu aparelho (`arm64-v8a` cobre praticamente
todos os celulares atuais); se estiver em dúvida, use o `universal`.

O app é distribuído fora da Play Store, então o Android vai pedir permissão para
instalar de fonte desconhecida — e o Play Protect pode exibir um aviso, normal
para aplicativos sideloaded.

### Sincronizar com o computador

1. No PC, abra o Lumen Music e ligue a sincronização (menu **Sincronizar**). Vai aparecer um PIN.
2. Deixe celular e PC na **mesma rede Wi-Fi**.
3. No celular, vá em **Sincronizar**, escolha o computador na lista e digite o PIN.
4. Pronto: as próximas sincronizações são automáticas.

Se o computador não aparecer na lista, informe o IP manualmente (o app mostra
como) — algumas redes bloqueiam a descoberta automática. Na primeira vez, o
Firewall do Windows vai pedir permissão: autorize para **redes privadas**.

### Compilar

Requer JDK 17 e o Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug     # APK de debug
./gradlew testDebugUnitTest # testes unitários
./gradlew assembleRelease   # APKs de release (não assinados sem keystore)
```

### Licença

GPL-3.0 — veja [LICENSE](LICENSE). O app usa NewPipe Extractor e ffmpeg, ambos GPL.

---

## English

**Lumen Music Mobile** is the Android version of [Lumen Music](https://github.com/Lumen-Connection/lumen-music),
Lumen Connection's desktop music player. Same library, same playlists, same
palettes — plus one feature that only exists here: **Wi-Fi sync with your
computer**, which acts as the server and source of truth for your library.

Local-first, no accounts, no ads and **no telemetry whatsoever**.

### Features

- **Local library** — import audio from your device (`opus`, `webm`, `m4a`, `mp3`, `ogg`, `oga`, `flac`, `wav`, `aac`)
- **Playlists** with gradient or image covers, 2×2 mosaics, drag-to-reorder and 6 sort modes
- **Liked songs**, accent-insensitive global search, and listening history
- **Full player** — context queue + manual "up next" queue, no-repeat shuffle, repeat one/all, and session restore right where you left off
- **Background playback** with notification, lockscreen and Bluetooth controls
- **YouTube downloads** plus Spotify and YouTube playlist import
- **Desktop sync** over your local network: metadata, playlists and the audio files of the playlists you pick
- **6 palettes** (Lumen, Warm Vinyl, Ocean, Forest, Night Purple, Modern Gray) × light/dark/high-contrast × 2 densities, with a reduce-motion option
- **Portuguese and English**

### Install

Grab the latest APK from [Releases](https://github.com/Lumen-Connection/lumen-music-mobile/releases).
Pick the one matching your device's architecture (`arm64-v8a` covers virtually
every current phone); when in doubt, use `universal`.

The app ships outside the Play Store, so Android will ask you to allow installs
from an unknown source — and Play Protect may show a warning, which is normal
for sideloaded apps.

### Sync with your computer

1. On your PC, open Lumen Music and turn syncing on (**Sync** menu). A PIN appears.
2. Keep phone and PC on the **same Wi-Fi network**.
3. On your phone, open **Sync**, pick the computer from the list and type the PIN.
4. Done — later syncs happen automatically.

If the computer doesn't show up, enter its IP manually (the app tells you how) —
some networks block automatic discovery. The first time, Windows Firewall will
ask for permission: allow it for **private networks**.

### Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug     # debug APK
./gradlew testDebugUnitTest # unit tests
./gradlew assembleRelease   # release APKs (unsigned without a keystore)
```

### License

GPL-3.0 — see [LICENSE](LICENSE). The app uses NewPipe Extractor and ffmpeg, both GPL.
