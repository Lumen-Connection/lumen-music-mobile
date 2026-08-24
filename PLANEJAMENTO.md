# Lumen Music Mobile — Planejamento e Handoff

> **Propósito deste arquivo**: documento de planejamento e de retomada. Se o agente que estava executando for interrompido (falta de crédito, sessão encerrada), qualquer outro agente de IA ou pessoa deve conseguir continuar o trabalho lendo este arquivo. **Mantenha a seção "Estado atual" atualizada ao final de cada etapa concluída.**

---

## 1. Contexto

O [Lumen Music](https://github.com/Lumen-Connection/lumen-music) é um player de música desktop **C++20 + Qt6 Widgets** (Windows-only, v2.0.1, AGPL-3.0) em `d:\HubLumen\lumen-music`: biblioteca local SQLite, playlists N:N com capas gradiente/imagem e mosaico 2×2, curtidas, fila dupla (contexto + manual "a seguir"), shuffle bag sem repetição, repeat off/all/one, restauração de sessão (autosave 30 s), busca global sem acento, download do YouTube via yt-dlp, import de playlist Spotify (scrape do embed + matching heurístico com checklist de aprovação) e YouTube, 6 paletas × dark/light × alto-contraste × 2 densidades, SMTC/media keys/tray, PT-BR + EN. **Zero telemetria, tudo local.**

Módulos do desktop (referência para portar semântica): `src/database/` (schema + migrator + helpers puros `position_gap.h`/`owner_decision.h`), `src/design/` (tokens, palettes, i18n), `src/player/` (`playbackengine.cpp` — fila dupla/shuffle/restore), `src/pages/` (as 7 telas + queue + import dialog), `src/widgets/` (`greeting.h`, `textutils.h`, `mediatools.h`, `coverwidget.h`).

**Objetivo**: criar o **Lumen Music Mobile** (Android) em `d:\HubLumen\LumenMusicMobile` com paridade de funcionalidades e UI com o desktop, **mais um recurso novo que o desktop não tem: sync de biblioteca via Wi-Fi local**, com o desktop atuando como servidor/fonte da verdade. O plano cobre **os dois repos** (servidor no lumen-music + cliente no mobile).

Ecossistema Lumen Connection: Hub (Next.js, `d:\HubLumen\HubdaLumenConnection`), lumen-stream-mobile (Kotlin/Compose, `d:\HubLumen\LumenStreamMobile` — **o molde deste projeto**, ler o `PLANEJAMENTO.md` de lá), lumen-stream (Rust desktop, só no GitHub). As pastas `d:\HubLumen\LumenStreamFiles` e `d:\HubLumen\LumenMusicFiles` são mídia do usuário, **não** são código.

## 2. Decisões acordadas com o usuário (entrevistas — NÃO reabrir sem necessidade)

| Tema | Decisão |
|---|---|
| Plataforma | **Android nativo: Kotlin + Jetpack Compose** (Material 3), molde do lumen-stream-mobile |
| Arquitetura | Igual ao molde: **sem ViewModel, sem DI framework** — `Graph.kt` service locator, composables coletam Flows do Room direto, Room como fonte única entre UI e services |
| Repo | Novo repo **`Lumen-Connection/lumen-music-mobile`**, código em `d:\HubLumen\LumenMusicMobile`, pacote `com.lumenconnection.music` |
| Alvos | **minSdk 26**, compileSdk/targetSdk 35, JVM 17, desugaring |
| Idiomas | **PT-BR (fonte) + EN** (`values/` PT-BR + `values-en/`), portando o dicionário de `src/design/i18n.cpp` |
| Distribuição | **APK direto via GitHub Releases** com ABI splits; CI igual ao molde (build em push/PR, APK assinado em tag `v*`) |
| Paridade | Funcionalidades e UI **perfeitamente iguais** ao desktop. Única divergência sancionada: navegação = **bottom nav (Início / Buscar / Biblioteca) + drawer** (Adicionar, Curtidas, Playlists, Sincronizar, Configurações); fila vira bottom-sheet |
| Downloads | **Paridade total**: YouTube via NewPipe Extractor + yt-dlp fallback, import de playlist Spotify e YouTube — mesmas libs/versões conhecidas-boas do lumen-stream-mobile |
| Player em background | **SIM, no MVP**: Media3 `MediaSessionService` (notificação, lockscreen, Bluetooth) — análogo do SMTC do desktop |
| Sync — escopo | Metadados da biblioteca inteira + playlists + estado de reprodução **sempre**; arquivos de áudio **por seleção de playlist** (+ opção "todas") |
| Sync — direção | **Desktop é a fonte da verdade**: biblioteca/arquivos só descem. Exceção: **estado leve sobe** (likes, play counts, playlists criadas no mobile) como merge aditivo |
| Sync — transporte | **Wi-Fi local (LAN)**, sem nuvem, sem conta |
| Pareamento | Desktop anuncia na rede; mobile lista servidores; 1º pareamento por **PIN** exibido no desktop (QR é polish) → token persistente; syncs seguintes automáticos |
| Escopo | Plano cobre **os dois repos**: servidor de sync em C++/Qt no lumen-music E cliente no mobile |

## 3. Arquitetura

### 3.1 Mobile — projeto Android Gradle (Kotlin DSL), módulo único `app/`, pacote raiz `com.lumenconnection.music`:

```
com.lumenconnection.music/
├─ Graph.kt        → service locator (db, settings), como no molde
├─ config/         → DataStore Preferences: paleta, modo (dark/light/HC), densidade, idioma,
│                    reduce-motion, servidor pareado {serverId, host, port, token, name}, lastSyncAt,
│                    flag "sincronizar áudio de todas as playlists"
├─ db/             → Room espelhando o schema do desktop (migrator.cpp, user_version 2) + colunas de sync
├─ player/         → PlaybackService (MediaSessionService/ExoPlayer) + PlayerController: fila dupla,
│                    shuffle bag, repeat, autosave 30 s — port de src/player/playbackengine.cpp
├─ extractor/      → NewPipeEngine + YtDlpEngine (copiar padrão/versões do lumen-stream-mobile)
├─ download/       → DownloadService (foreground) + HttpDownloader (resume .part + Range — reutilizar
│                    o do molde) + FriendlyError
├─ metadata/       → SpotifyMetadata (parseEmbedTracks puro/testável) + YouTubePlaylistImporter
│                    (flat-playlist) — port de src/pages/importplaylistdialog.cpp
├─ importer/       → matching heurístico Spotify→YouTube + checklist de aprovação
├─ sync/           → DiscoveryClient (UDP), SyncApi (OkHttp + DTOs kotlinx-serialization),
│                    PairingFlow, SyncEngine (push→pull→diff→downloads), SyncService (foreground dataSync)
├─ media/          → AudioStorage (paths app-scoped), CoverStore (capas importadas/sincadas)
└─ ui/             → AppNav (bottom nav + drawer), theme/ (port de tokens.cpp + palettes.cpp),
                     LumenComponents (TrackRow, PlaylistCard c/ gradiente+mosaico 2×2, SectionHeader,
                     EmptyState, Toast), e um arquivo por tela: HomeScreen, SearchScreen, LibraryScreen,
                     FolderDetailScreen, LikedScreen, AddMusicScreen, SyncScreen, SettingsScreen,
                     NowPlayingScreen, QueueSheet (ModalBottomSheet), PlayerBar (c/ vinil girando)
```

**Schema Room** (espelha `src/database/migrator.cpp` + bookkeeping de sync):

- `tracks(id PK, remoteId Long?, title, artist, filePath String?, durationMs, coverColor1, coverColor2, ownerPlaylistId?, liked, likedAt, addedAt, playCount, lastPlayedAt, missing, origin {LOCAL|SYNC|DOWNLOAD}, downloadState {NONE|PENDING|DOWNLOADING|DONE|FAILED}, fileSize, fileMtime, pendingPlayDelta Int, likeDirty Boolean)` — índice único parcial em `remoteId`
- `playlists(id PK, remoteId Long?, clientKey String?, name, coverColor1, coverColor2, coverImagePath, sortMode, createdAt, origin, syncFiles Boolean)` — `syncFiles` = "baixar áudio desta playlist"
- `playlist_tracks(playlistId, trackId, position, addedAt, PK composta)` — ordenação **gap-1024** (port de `position_gap.h` com testes)
- `playback_state(id=1, currentTrackId, positionMs, shuffle, repeatMode, contextIds JSON, userQueueIds JSON, contextIndex, contextName)` — 1:1 com o desktop

**Armazenamento de áudio**: arquivos vindos do sync/download vão para **app-scoped external** (`getExternalFilesDir(null)/music/<dirPlaylist>/`) — o app é dono dessas cópias, deleção espelhada é consistente e não requer permissão nenhuma. Import local do aparelho (paridade com AddMusic): SAF multi-pick + `takePersistableUriPermission`, tocando **por URI sem copiar** (espelha a regra inviolável do desktop: nunca mover/copiar arquivo do usuário) — `filePath` guarda o URI. Parse "Artist - Título" do nome do arquivo, como o desktop (sem leitura de tags na v1).

**Playback**: a fila dupla + shuffle bag + repeat vivem no `PlayerController`, **fora** do ExoPlayer — alimenta o player **um MediaItem por vez** e avança no `STATE_ENDED`. Isso reusa a semântica do desktop literalmente e evita brigar com a playlist interna do Media3. `play_count`/`last_played_at` com o mesmo gatilho do desktop (conferir em `playbackengine.cpp` na implementação); incrementos somam em `pendingPlayDelta` para o push.

**Paridade — o que é idêntico e o que se adapta por ser mobile**: todas as funcionalidades e o visual (paletas, componentes, telas, textos, comportamento) são réplicas do desktop. As únicas adaptações, todas inerentes à plataforma (não são licença criativa):
- Navegação: sidebar + splitter → **bottom nav + drawer** (decisão do usuário, §2); painel lateral de fila → **bottom-sheet**
- SMTC/media keys/tray do Windows → **MediaSession** (notificação/lockscreen/Bluetooth); tray não existe no Android
- Ícones Segoe MDL2 (fonte do Windows) → `material-icons-extended` equivalentes (monocromáticos, tingidos pelo tema — nunca emoji, lição do molde)
- Atalhos de teclado (Space/L/Q/Delete) não se aplicam a touch → os mesmos comandos vivem no menu de contexto/gestos; hover states viram estados de toque
- Drag-and-drop de arquivos na AddMusic → SAF multi-pick (mesma função)
- Redimensionar janela/breakpoint 470 px → layout responsivo Compose (grades adaptam colunas como as páginas do desktop fazem)

Tudo o mais — 6 paletas com os mesmos hex, saudação por hora, fila dupla, shuffle bag, gap-1024, 6 sort modes, capas gradiente/imagem/mosaico 2×2, curtidas, busca sem acento, restore de sessão, downloads YouTube, imports Spotify/YouTube com checklist de aprovação, edição título/artista só no DB, toasts, histórico de navegação — é **réplica fiel**, verificada pelo checklist tela-a-tela da fase 3.8.

**Tema**: port 1:1 de `src/design/palettes.cpp` + `tokens.cpp` — 6 paletas (lumen `#ff5722`/`#e64a19`, warm "Vinil Quente" `#e8a44a`, ocean "Oceano" `#4aa8e8`, forest "Floresta" `#6bcf7f`, purple "Roxo Noturno" `#c084fc`, gray "Cinza Moderno" `#e0e0e0`/`#2563eb`), cada uma com set dark E light explícitos, alto-contraste **derivado** (WCAG 4.5:1/3:1, port de `deriveHighContrast`/`ensureContrast`), 2 densidades, reduce-motion. `LumenColors`/`LumenDimens` via `staticCompositionLocalOf` como no molde. Ícones: `material-icons-extended` (o desktop usa Segoe MDL2, que não existe no Android).

### 3.2 Protocolo de sync v1 (LAN)

**Transporte: HTTP/1.1 mínimo sobre `QTcpServer`.** FATO VERIFICADO: o kit Qt instalado (`C:\Qt\6.10.2\msvc2022_64`) **não tem** o módulo `Qt6HttpServer` (add-on ausente do instalador; depender dele quebraria toolchain local e CI). O servidor próprio precisa de: parse de request-line + headers, corpo por `Content-Length`, resposta com `Content-Length` ou streaming de arquivo, `Range: bytes=N-`, e pode responder `Connection: close` sempre (OkHttp reabre). ~300–400 linhas, testável com QTest. Plano B documentado: se `qthttpserver` entrar no kit um dia, trocar transporte mantendo rotas/payloads.

**Sem TLS na v1** (limitação conhecida e aceita): rede local + token no header `X-Lumen-Token`; desktop guarda só o **SHA-256** do token e compara em tempo constante.

**Portas**: 45150/TCP (HTTP, configurável), 45151/UDP (descoberta).

**Descoberta — probe/response UDP (não é mDNS, de propósito)**: o **mobile** envia broadcast `255.255.255.255:45151` com `{"lumen":"discover","proto":1}`; o desktop responde **unicast** ao remetente `{"lumen":"announce","proto":1,"serverId":"<uuid>","name":"<hostname>","port":45150,"appVersion":"..."}`. Qt não tem mDNS nativo e broadcast+unicast dispensa `MulticastLock` no Android e evita quirks de multicast no Windows. **NÃO "corrigir" para mDNS** — a UX acordada (desktop anuncia, mobile lista) é preservada. Fallback de **primeira classe** na UI: digitar `IP:porta` manualmente (redes com AP isolation). No Android, use `DatagramSocket` broadcast simples.

**Identidade**: `serverId` = UUID gerado uma vez e persistido no desktop (`sync_meta`). FATO VERIFICADO: `tracks.id` e `playlists.id` do desktop são `INTEGER PRIMARY KEY AUTOINCREMENT` — nunca reutilizados. Logo o mobile guarda `remoteId` = id do desktop, sem UUID por faixa. Se o `vinil.db` for recriado, o `serverId` muda → mobile detecta servidor novo → re-pareia e ressincroniza do zero (comportamento definido, não é bug).

**Endpoints** (prefixo `/v1`, JSON UTF-8):

| Método/rota | Auth | Função |
|---|---|---|
| `GET /v1/ping` | não | `{serverId, name, proto:1, appVersion}` — health check |
| `POST /v1/pair` | PIN | body `{pin, deviceId, deviceName}` → `200 {token, serverId, serverName}` ou `403`. PIN de 6 dígitos, válido só com o diálogo de pareamento aberto, máx. 5 tentativas por sessão |
| `GET /v1/library` | token | snapshot completo da biblioteca (abaixo) |
| `GET /v1/tracks/{id}/file` | token | streaming do áudio; suporta `Range`; headers `Content-Length`, `X-Lumen-Size`, `X-Lumen-Mtime` |
| `GET /v1/playlists/{id}/cover` | token | imagem de capa quando houver, senão `404` (mobile usa gradiente) |
| `POST /v1/push` | token | merge aditivo mobile→desktop (abaixo) |

`proto` incompatível → `409 {"error":"proto_mismatch"}` → mobile mostra "atualize o Lumen Music no PC". Token revogado → `401` → mobile volta ao estado "não pareado".

**Snapshot completo a cada sync, sem deltas** — escala real é 10³–10⁴ faixas ≈ 1,5 MB de JSON, sub-segundo em LAN; delta exigiria change-tracking + tombstones no desktop para economizar ~1 MB. Deleções saem de graça: o snapshot é a verdade. Shape:

```json
{
  "serverId": "…", "generatedAt": 1724500000000, "proto": 1,
  "playlists": [{ "id": 3, "name": "Rock", "coverColor1": "#…", "coverColor2": "#…",
                  "hasCoverImage": true, "sortMode": "custom", "createdAt": 0 }],
  "tracks": [{ "id": 41, "title": "…", "artist": "…", "durationMs": 215000,
               "coverColor1": "#…", "coverColor2": "#…", "ownerPlaylistId": 3,
               "liked": true, "likedAt": 0, "addedAt": 0, "playCount": 7,
               "lastPlayedAt": 0, "missing": false,
               "fileSize": 4211234, "fileMtime": 1724400000 }],
  "playlistTracks": [{ "playlistId": 3, "trackId": 41, "position": 1024, "addedAt": 0 }],
  "playbackState": { "currentTrackId": 41, "positionMs": 63000, "shuffle": false,
                     "repeatMode": 0, "contextIds": [], "userQueueIds": [],
                     "contextIndex": 4, "contextName": "Rock" }
}
```

`file_path` do desktop **não** é exposto. `fileSize + fileMtime` são a identidade de conteúdo: mobile re-baixa quando um dos dois muda (o desktop nunca move arquivos, então mtime é estável; hash é overkill).

**Transferência de áudio**: um `GET .../file` por faixa, paralelismo 2–3, gravando em `<destino>.part`, retomando com `Range: bytes=<tamanho do .part>-`, validando tamanho final contra `fileSize` e renomeando — o `HttpDownloader` do molde já faz esse padrão.

**Merge aditivo (`POST /v1/push`) — ordem do sync: PUSH primeiro, PULL depois** (o snapshot já volta refletindo o merge e o mobile converge num passo):

```json
{
  "deviceId": "…",
  "likes":      [{ "trackId": 41, "liked": true, "likedAt": 1724499000000 }],
  "playCounts": [{ "trackId": 41, "delta": 3, "lastPlayedAt": 1724499000000 }],
  "newPlaylists": [{ "clientKey": "uuid-local", "name": "Do celular",
                     "coverColor1": "#…", "coverColor2": "#…", "trackIds": [41, 87] }],
  "playlistMembership": [{ "playlistId": 12, "trackIds": [41, 87, 90] }]
}
```

Regras (tudo por id de faixa do desktop; ids desconhecidos são ignorados e devolvidos em `skipped`):
- **Likes**: last-write-wins por timestamp — aplica o estado do mobile só se `likedAt` (mobile) > `liked_at` (desktop). Cobre like E unlike sem tombstone. Mobile marca `likeDirty` e limpa após `200`.
- **Play counts**: aditivo por delta; mobile zera `pendingPlayDelta` após `200` e depois adota o total do snapshot. `last_played_at = max(ambos)`.
- **Playlists criadas no mobile**: idempotentes por `clientKey` (UUID gerado no mobile); desktop guarda `clientKey→playlist.id` e responde `{"createdPlaylists":[{"clientKey":"…","id":12}]}` — reenvio não duplica. Colisão de nome → sufixo `" (celular)"`. **Posse por origem**: playlist nascida no mobile ⇒ mobile manda a membership completa e o desktop faz replace; nascida no desktop ⇒ só desce, mobile não envia edições (limitação documentada). Faixas que só existem no mobile ficam fora do push (arquivos nunca sobem).
- **playback_state**: só desce; o mobile pode oferecer "Continuar de onde parou no PC" (polish).

**Fluxo de pareamento**: usuário abre o diálogo de sync no desktop → servidor liga, PIN de 6 dígitos (+ QR `lumen-sync://pair?host=<ip>&port=45150&pin=<pin>&sid=<serverId>`) → mobile descobre via UDP (ou IP manual) → digita PIN → `POST /v1/pair` → guarda `{serverId, host, port, token, serverName}` no DataStore. Token = 32 bytes aleatórios em hex. Desktop grava em `sync_devices` com botão **revogar** no diálogo. Syncs seguintes: automático ao abrir o app se `ping` responder (re-resolvendo o host via descoberta se o IP mudou — casa por `serverId`) + botão "Sincronizar agora".

**Diff do `SyncEngine` no mobile**: casa por `remoteId`; ausente → insere; presente → atualiza campos remotos preservando os locais dirty; `remoteId` sumiu do snapshot → deleta entidade **e arquivo** (somente `origin=SYNC`); entidades `origin ∈ {LOCAL, DOWNLOAD}` intocadas.

### 3.3 Desktop — módulo novo `src/sync/` no lumen-music

Migração de schema **v3** em `src/database/migrator.cpp` (`user_version 2→3`, só tabelas novas, não toca em tracks/playlists):
- `sync_meta(key TEXT PRIMARY KEY, value TEXT)` — `server_id`, mapa `clientKey→playlistId`
- `sync_devices(device_id TEXT PRIMARY KEY, name TEXT, token_hash TEXT, created_at INTEGER, last_sync_at INTEGER)`

| Arquivo | Responsabilidade |
|---|---|
| `src/sync/http_server.h/cpp` | HTTP mínimo sobre `QTcpServer`: parse, roteamento por tabela, streaming de `QFile` dirigido por `bytesWritten` (nunca bloqueia), `Range` |
| `src/sync/discovery_responder.h/cpp` | `QUdpSocket` em 45151, responde announce unicast |
| `src/sync/pairing.h/cpp` | PIN/token, `QCryptographicHash` SHA-256, comparação constant-time, limite de tentativas |
| `src/sync/library_snapshot.h/cpp` | DB → `QJsonDocument`; função pura sobre uma conexão (testável com DB in-memory) |
| `src/sync/merge_service.h/cpp` | aplica push com as regras acima, em transação única, idempotente por `clientKey` (testável idem) |
| `src/sync/sync_server.h/cpp` | orquestra tudo; vive num **`QThread` dedicado** com event loop próprio |
| `src/sync/sync_dialog.h/cpp` | UI: toggle ligar/desligar (+ "manter ligado" persistido), nome do PC, IP:porta, PIN grande, QR, lista de dispositivos + revogar, último sync |
| `third_party/qrcodegen/` | QR-Code-generator do Nayuki (2 arquivos, MIT) vendorizado; render via `QPainter` |

Threading/DB: a thread do servidor abre **conexão `QSqlDatabase` própria** (nome `"sync"`, mesmo arquivo) — WAL já ativo permite leitor concorrente; para os writes do merge, `PRAGMA busy_timeout=5000`. Após merge com mudanças, sinal cross-thread `libraryChangedExternally()` → controller principal recarrega views (verificar e reaproveitar o caminho de refresh existente das telas). **Servidor desligado por padrão.** Primeira escuta dispara o prompt do Firewall do Windows — instruir no diálogo ("permita o acesso em redes privadas"). Registrar os novos fontes e alvos de teste no `CMakeLists.txt` (padrão `lumen_add_test()`).

### 3.4 Notas técnicas

- Versões conhecidas-boas do molde: NewPipeExtractor **v0.26.5** (JitPack), youtubedl-android **0.18.1** + ffmpeg (**Maven Central** `io.github.junkfood02.youtubedl-android` — o JitPack do yausername quebrou; pacotes Kotlin continuam `com.yausername.*`), Room 2.6.1, Media3 1.4.1, OkHttp 4.12, Coil 2.7, Navigation-Compose 2.8.4, AGP 8.7.3, Kotlin 2.0.21, desugaring ON, `jniLibs.useLegacyPackaging=true`, proguard keeps de NewPipe/Rhino/yausername/**commons-compress** (copiar do molde).
- **Sem analytics/telemetria de nenhum tipo.**
- Licença: desktop é AGPL-3.0; NewPipe/ffmpeg são GPL → mobile **GPLv3** (compatível com consumir a API do desktop).
- i18n: PT-BR é a fonte (como no desktop); portar os ~250 pares de `src/design/i18n.cpp` para `values/strings.xml` (PT) + `values-en/strings.xml`.

## 4. Fases e checklist

### Fase 0 — Bootstrap (repo mobile)
- [ ] 0.1 Verificar toolchain (seção 6) — já instalado para o lumen-stream-mobile; criar `local.properties`
- [ ] 0.2 Scaffold Gradle a partir do molde: version catalog, AGP/Kotlin/KSP, minSdk 26/target 35, desugaring, ABI splits, `MainActivity` + Compose "Hello"
- [ ] 0.3 Port do design system: `theme/` com as 6 paletas dark+light+HC derivado, densidades, reduce-motion (hex de `palettes.cpp`)
- [ ] 0.4 i18n: `values/strings.xml` (PT-BR) + `values-en/` com o dicionário portado
- [ ] 0.5 `Graph.kt` + `config/SettingsRepository` (tema/idioma funcionando ao vivo)
- [ ] 0.6 Ícone do app (logo oficial do lumen-music) + `git init` + repo `Lumen-Connection/lumen-music-mobile` via `gh` + push
- [ ] 0.7 CI `.github/workflows/android.yml` do molde (build push/PR + release assinado em tag `v*`; lembrar `permissions: contents: write` e `tr -d '\r\n '` no keystore)

### Fase 1 — Dados + casca de navegação
- [ ] 1.1 Room completo (schema da §3.1) + DAOs com Flows
- [ ] 1.2 Testes de DAO (in-memory)
- [ ] 1.3 Ports puros com testes unitários: `position_gap`, normalizador NFD de `textutils.h`, saudação de `greeting.h` (5 bandas × 3 variantes PT/EN)
- [ ] 1.4 `AppNav`: bottom nav (Início/Buscar/Biblioteca) + drawer (Adicionar, Curtidas, Playlists, Sincronizar, Configurações) com logo e ordem espelhando a sidebar
- [ ] 1.5 Todas as telas criadas com scaffold vazio + navegação com histórico/back
- [ ] 1.6 `LumenComponents`: TrackRow (glyph play/coração/menu), PlaylistCard (gradiente + mosaico 2×2 + imagem), SectionHeader, EmptyState, Toast
- [ ] 1.7 SettingsScreen: paleta, modo, densidade, reduce-motion, idioma — tudo ao vivo
- [ ] 1.8 Commit + CI verde

### Fase 2 — Player MVP
- [ ] 2.1 `PlaybackService : MediaSessionService` + ExoPlayer (foreground `mediaPlayback`)
- [ ] 2.2 `PlayerController`: fila de contexto + fila manual "a seguir" + shuffle bag + repeat off/all/one, alimentando 1 MediaItem por vez (port de `playbackengine.cpp`)
- [ ] 2.3 Testes unitários da lógica de fila/shuffle/repeat
- [ ] 2.4 PlayerBar persistente (com vinil girando, respeitando reduce-motion; coração de like)
- [ ] 2.5 NowPlayingScreen + QueueSheet com reordenar/remover/limpar
- [ ] 2.6 Notificação de mídia + lockscreen + Bluetooth + media keys (via session)
- [ ] 2.7 Restore de sessão: autosave 30 s + eventos (pause/troca/destroy); restore no boot do serviço sem "seek kick"
- [ ] 2.8 `play_count`/`last_played_at` (mesmo gatilho do desktop) + `pendingPlayDelta`
- [ ] 2.9 AddMusic mínimo: SAF multi-pick, parse "Artist - Título", tocar por URI
- [ ] 2.10 **E2E**: importar 3 arquivos → tocar → enfileirar → matar o app → reabrir → sessão restaurada → controlar pela notificação e por fone Bluetooth

### Fase 3 — Paridade de telas
- [ ] 3.1 Playlists: criar/renomear/deletar, capa gradiente (2 cores) ou imagem, lightbox
- [ ] 3.2 FolderDetail: header com capa, lista virtualizada, 6 sort modes persistidos por playlist, filtro de texto, drag-and-drop gap-1024
- [ ] 3.3 Curtidas (tela + like em todo lugar), multi-select + menu de contexto (tocar, enfileirar, curtir, adicionar-a-playlist com submenu, editar, deletar)
- [ ] 3.4 Busca global accent-insensitive (títulos, artistas, playlists separadas), debounce
- [ ] 3.5 Home completa: saudação por hora, chips de playlists, strip "Recentes", shelves Tocadas/Adicionadas recentemente
- [ ] 3.6 Library (biblioteca completa) + diálogo editar faixa (título/artista, só no DB)
- [ ] 3.7 Toasts + confirmações de deleção
- [ ] 3.8 **Checklist de paridade tela-a-tela contra o desktop** (rodar o desktop ao lado e comparar cada tela; navegação é a única divergência aceita)
- [ ] 3.9 Commit + CI verde

### Fase 4 — Downloads e importação
- [ ] 4.1 `extractor/`: NewPipe primeiro, yt-dlp fallback (versões/coordenadas da §3.4), init no `Application` com captura de `Throwable`
- [ ] 4.2 `DownloadService` foreground + `HttpDownloader` (reutilizar molde) + FriendlyError
- [ ] 4.3 AddMusic: download por URL do YouTube com progresso ao vivo
- [ ] 4.4 Import de playlist YouTube (flat-playlist, ordem preservada)
- [ ] 4.5 Import Spotify: parser de embed puro + testes (fixtures do desktop/molde) + matching heurístico + checklist de aprovação
- [ ] 4.6 Proguard keeps copiados do molde; retry com update do yt-dlp em HTTP 403
- [ ] 4.7 **Testar APK RELEASE** (lição do molde: R8 já quebrou launch em produção)
- [ ] 4.8 **E2E release**: baixar 1 música por URL + importar 1 playlist Spotify pequena com aprovação

### Fase 5 — Servidor de sync no desktop (repo lumen-music, branch própria)
- [ ] 5.1 Migração v3 (`sync_meta`, `sync_devices`) + caso no teste do migrator
- [ ] 5.2 `http_server` + teste QTest (requests crus via `QTcpSocket`, incl. `Range`)
- [ ] 5.3 `discovery_responder` UDP
- [ ] 5.4 `pairing` (PIN/token/SHA-256/constant-time/5 tentativas) + teste
- [ ] 5.5 `library_snapshot` + teste com DB in-memory
- [ ] 5.6 `merge_service` (likes LWW, play counts delta, playlists por `clientKey`, transação) + teste de idempotência
- [ ] 5.7 Streaming de arquivo com `Range` sem bloquear (dirigido por `bytesWritten`)
- [ ] 5.8 `sync_server` em QThread + conexão SQL própria + `busy_timeout` + sinal `libraryChangedExternally()` ligado ao refresh das views
- [ ] 5.9 `SyncDialog` (toggle, PIN, QR via qrcodegen vendorizado, devices + revogar) + entrada no menu/Settings
- [ ] 5.10 **Verificação**: com o app aberto tocando música, `curl` completa pair→library→file(Range)→push sem travar a UI; likes do push aparecem na tela

### Fase 6 — Cliente de sync no mobile
- [ ] 6.1 DTOs kotlinx-serialization + `SyncApi` (OkHttp, `X-Lumen-Token`)
- [ ] 6.2 `DiscoveryClient` UDP broadcast + fallback IP:porta manual (primeira classe)
- [ ] 6.3 `PairingFlow` + SyncScreen: lista de servidores, PIN, estado pareado, "Sincronizar agora", seleção por-playlist/"todas" para áudio, progresso, revogado (401) e `proto_mismatch` tratados
- [ ] 6.4 `SyncEngine`: push (dirty likes/deltas/playlists novas) → pull snapshot → diff por `remoteId` no Room
- [ ] 6.5 Fila de download de áudio com `.part` + resume + validação de tamanho, em `SyncService` foreground dataSync, paralelismo 2–3
- [ ] 6.6 Deleção espelhada (entidade + arquivo, somente `origin=SYNC`)
- [ ] 6.7 Re-download quando `fileSize`/`fileMtime` mudam
- [ ] 6.8 Auto-sync ao abrir o app quando o servidor responde ping (re-resolve host por `serverId`)
- [ ] 6.9 Testes do diff do SyncEngine (unitários, Room in-memory)
- [ ] 6.10 **E2E DE OURO**: parear via PIN → sync de biblioteca real → escolher 2 playlists p/ áudio → desligar Wi-Fi → tocar offline → curtir 3 faixas + criar 1 playlist no celular → religar → re-sync → conferir likes/playlist/play counts no desktop → deletar 1 faixa no desktop → re-sync → sumiu do celular (e o arquivo também)

### Fase 7 — Polish e release
- [ ] 7.1 QR scan no pareamento (`zxing-android-embedded`)
- [ ] 7.2 "Continuar de onde parou no PC" (playback_state do snapshot)
- [ ] 7.3 Passe de alto-contraste/compacto/reduce-motion + passe de tradução EN
- [ ] 7.4 Teste completo no APK release (universal + arm64)
- [ ] 7.5 Keystore novo `d:\HubLumen\.secrets\lumen-music-mobile.keystore` + secrets no repo via `gh secret set --body`
- [ ] 7.6 Tag `v0.1.0` mobile → APKs no GitHub Releases; README PT/EN (incl. troubleshooting de firewall/rede) + LICENSE GPLv3
- [ ] 7.7 Desktop: PR da branch de sync → release `v2.1.x` (coordenar com o roadmap 2.1 existente — TagLib/Álbuns — que NÃO faz parte deste plano)

## 5. Riscos conhecidos

| Risco | Mitigação |
|---|---|
| R8 quebra o release (aconteceu de verdade no lumen-stream v0.1.0) | Testar APK **release** nas fases 2, 4 e 6; copiar proguard keeps do molde; capturar `Throwable`, não `Exception` |
| Firewall do Windows bloqueia 45150/45151 | Prompt nativo na primeira escuta + instrução no SyncDialog + seção de troubleshooting no README |
| Broadcast UDP bloqueado (AP isolation) | Entrada manual de IP:porta é caminho de primeira classe na UI, não easter egg |
| Contenção SQLite (thread do sync × UI do desktop) | WAL + `busy_timeout=5000` + merge em transação única curta; teste de stress no QTest |
| UI do desktop travar servindo arquivo grande | Streaming assíncrono por `bytesWritten` + servidor em QThread próprio (dupla proteção) |
| `vinil.db` recriado → ids órfãos no mobile | `serverId` muda junto → re-pareamento + ressync do zero (comportamento definido) |
| Scope creep da "paridade perfeita" | Checklist de paridade fixado na fase 3; navegação é a única divergência sancionada |
| Tamanho do APK (Python/ffmpeg ≈ +60–80 MB) | ABI splits como no molde (lá: arm64 57 MB) |
| Push duplica playlists em retry | Idempotência por `clientKey` + resposta com mapeamento |
| Segurança: HTTP sem TLS na LAN | Token com hash SHA-256 + constant-time; documentado como limitação v1; servidor desligado por padrão |

## 6. Toolchain local (máquina do usuário — Windows 11, sem admin)

**Já provisionado** para o lumen-stream-mobile em `d:\HubLumen\.tools\` — reutilizar:
- JDK 17: `d:\HubLumen\.tools\jdk17\jdk-17.0.20+8` (`JAVA_HOME`)
- Android SDK: `d:\HubLumen\.tools\android-sdk` (`ANDROID_HOME`; platform-tools, android-35, build-tools 35.0.0, licenças aceitas)
- Gradle 8.10.2: `d:\HubLumen\.tools\gradle\gradle-8.10.2`
- Emulador: AVD `lumen_test` (android-35 x86_64, WHPX) já criado

Passos para este projeto:
1. `local.properties` com `sdk.dir=d:\\HubLumen\\.tools\\android-sdk`
2. `gradle wrapper --gradle-version 8.10.2` na raiz; build: `.\gradlew.bat assembleDebug`
3. Desktop: Qt 6.10.2 MSVC em `C:\Qt\6.10.2\msvc2022_64`, preset CMake `msvc-ninja` (ver `CMakePresets.json`/`CMakeUserPresets.json` do lumen-music). **Lembrete: o kit NÃO tem QHttpServer** (§3.2)
4. Keystore novo: `keytool -genkeypair` → `d:\HubLumen\.secrets\lumen-music-mobile.keystore` (senha em arquivo `.txt` na mesma pasta — FAZER BACKUP); secrets via `gh secret set --body` (nunca via pipe — CRLF quebra o base64)

## 7. Verificação (critérios de pronto)

- `gradlew assembleDebug` e `assembleRelease` sem erros; testes unitários (fila, gap-1024, NFD, saudação, parser Spotify, diff do sync) passando; QTest do desktop passando incl. os novos de `src/sync/`
- E2E player (fase 2.10): importar → tocar → matar app → restaurar → Bluetooth/notificação
- E2E downloads (fase 4.8) **no APK release**
- Verificação do servidor (fase 5.10): `curl` pair→library→file→push com o desktop tocando música, sem travar UI
- **E2E de ouro do sync (fase 6.10)** — o roteiro completo descrito lá
- Trocar idioma PT↔EN e tema (6 paletas × dark/light/HC) ao vivo nas duas plataformas do sync
- CI: push de tag `v*` gera APKs assinados no GitHub Releases

---

## 8. ESTADO ATUAL (atualizar sempre!)

**Última atualização**: 2026-08-24

- ✅ Repos analisados (lumen-music desktop v2.0.1 + lumen-stream-mobile como molde)
- ✅ Entrevistas concluídas e decisões registradas (seção 2)
- ✅ Protocolo de sync desenhado e fatos verificados: kit Qt sem QHttpServer (→ servidor próprio sobre QTcpServer); ids do desktop são AUTOINCREMENT estáveis (→ `remoteId` simples no mobile)
- ✅ Plano aprovado pelo usuário
- ⏭️ Nada implementado ainda — próximo passo: Fase 0 (bootstrap do repo mobile)
- ⚠️ Preferência do usuário registrada no projeto irmão: commits **sem** trailer de co-autoria do Claude
