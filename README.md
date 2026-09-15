# MONO

A local music player. Point it at a folder, and it plays the MP3s (and m4a,
flac, wav, ogg, opus, aac) it finds inside — no account, no network, no library
import step.

Two builds, same design and the same ideas:

- **`app/`** — the Android app, sideloaded as an APK.
- **`web/`** — the desktop app, installed from the browser. See
  [On your PC](#on-your-pc).

## What changed in 2.0

The first version handed the player **one track at a time** and kept the queue
in the activity. Everything below follows from moving the whole queue into the
playback service instead.

| | Before | Now |
|---|---|---|
| End of a track | Only advanced while the app was on screen | The player advances on its own, screen off or app closed |
| Notification / lock screen | Play and pause only | Previous, play/pause, next, and a like button |
| Likes | — | Heart in the app and in the notification, kept in step both ways |
| Position | Progress bar with no numbers | Elapsed and total time, in the bar and on the full player |
| Cover art | Every cover halved before display | Decoded to fit what is on screen, cached at up to 1024px |
| Now playing | A bar at the bottom | Full screen: the cover as a turning record over a blurred backdrop |
| Suggestions | — | A **For you** tab built from your own listening |
| Playlists | One track at a time | Long-press to multi-select, then add or shuffle in bulk |
| Other audio | Audio focus was requested | Unchanged, and now correct: pausing no longer loses the queue |

### Building playlists

Long-press any track to start selecting; tap to add more. The header becomes a
selection bar — select all, play, shuffle, or add the lot to a playlist. Adding
to a playlist that does not exist yet creates it with those tracks already in.

Every list has both **Play** and **Shuffle**. A playlist's own order can be
randomised for good from its overflow menu (**Shuffle order**), which rewrites
the stored order rather than only the current queue.

### For you

There is no music service to ask, so suggestions come from the library itself:

- **Recently played** and **On repeat** — from play counts recorded when a track
  actually starts, not when it is skipped past.
- **Liked songs** and **More like this** — other tracks by the artists you liked.
- **Recently added** — newest files in the folder, by file date.
- **Never played** — a handful you own but have not played, rotating daily.

## Installing

CI builds an APK for every push. Open the latest run under
**Actions → Build Mono APK**, download **Mono-apk** from the Artifacts section,
and open the `.apk` on the phone.

> **One-time step:** uninstall the old Mono first. Version 1 was signed with a
> throwaway debug key that no longer exists, so Android will refuse to install
> over it. Playlists from the old install are lost in that step — there are only
> a few, and they are quick to rebuild.
>
> This does not happen again. Both build types are now signed with
> `app/mono-sideload.jks`, which is committed to this repo, so every future
> build installs straight over the previous one and keeps your playlists,
> likes and play counts.

On first launch, grant the notification permission — the media notification is
where the lock-screen controls come from — and pick your music folder.

## On your PC

Mono also runs as a desktop app. It is a web app, but an installed one: its own
window, no address bar, an icon you can pin to the taskbar and the Start menu,
and it works with the network off.

> **One-time repo setting.** GitHub Pages has to be switched on by hand before
> the first deploy: **Settings → Pages → Build and deployment → Source →
> GitHub Actions**. The workflow token is not permitted to create the site
> itself. After that, every push to `web/` deploys on its own.

**Install it** in Chrome or Edge:

1. Open **https://olrock8910.github.io/video-player/**
2. Click **Install** — either the button in Mono's own header, or the install
   icon at the right-hand end of the address bar.
3. Pin the window's taskbar icon, or find MONO in the Start menu.

Then choose your music folder. Windows asks once whether to let the site read
it; Mono remembers the folder after that, though Chrome asks you to re-confirm
the permission after a restart, which is one click on the welcome screen.

Nothing is uploaded. The audio is read straight off your disk through the
folder you picked, and likes, playlists and play counts live in the browser's
own storage on that machine.

### What works there

Everything the phone app does — the four tabs, For you, likes, multi-select,
playlists, shuffle and repeat, the turning record — plus the parts that only
make sense on a desktop:

- **Media keys** and the Windows media overlay, with cover art and track name.
- **Keyboard**: space or `K` play/pause, `N`/`P` next and previous, `←`/`→` seek
  five seconds, `↑`/`↓` volume, `S` shuffle, `R` repeat, `L` like, `Ctrl+F`
  search, `Ctrl+A` select all while selecting, `Esc` to back out.
- **Right-click** any track or playlist for its menu; `Ctrl`-click and
  `Shift`-click to build a selection.

### Listening stats

A **Stats** tab: time listened, songs played, different tracks, days listened and
a daily average, over Today / 7 days / 30 days / 12 months / all time, with a
column chart of when you listened and your top five songs and artists.

Time is counted on the wall clock **while audio is actually playing**, not by
track length, so skipping through an album does not bank forty minutes. Pausing
and resuming the same track stays one play. This needed a new append-only play
log — the old counters recorded only a total and a last-played date per track,
which cannot answer "this week".

### Two limits worth knowing

- **Chrome or Edge only.** Reading a folder needs the File System Access API,
  which Firefox and Safari have not shipped. Mono says so rather than failing
  oddly.
- **Tags are read for MP3 and FLAC.** Those cover embedded titles, artists and
  cover art. Other formats still play, and fall back to the
  "Artist - Title.ext" filename convention — the same fallback the phone app
  uses for untagged files.

### Working on it

`web/` is plain ES modules with no build step, so it serves from any static
server:

```
cd web && python3 -m http.server 8765   # then open http://127.0.0.1:8765/
```

| File | What it does |
|---|---|
| `app.js` | The shell: folder access, state, rendering, dialogs, shortcuts |
| `lib/player.js` | Audio element, queue, shuffle/repeat, media keys |
| `lib/library.js` | Folder walk, scan cache, durations, cover art URLs |
| `lib/tags.js` | ID3v2 and FLAC parsing, filename fallback |
| `lib/store.js` | IndexedDB: settings, likes, history, playlists |
| `lib/recommend.js` | For you sections, ported from `Recommend.kt` |
| `lib/ui.js`, `lib/icons.js` | DOM helpers and the SVG icon set |
| `sw.js` | Caches the app shell so it opens offline |

Icons are generated rather than drawn — re-run `node web/tools/make-icons.mjs`
only if the mark changes. Pushing to `web/` deploys to Pages automatically.

## Building

```
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # formatting and file-type helpers
./gradlew :app:lintDebug
```

## How it fits together

| File | What it does |
|---|---|
| `MainActivity.kt` | Holds UI state, connects to the session, hands queues to the player |
| `PlaybackService.kt` | The player itself: audio focus, the notification, the like button, play counts |
| `Library.kt` | Walks the chosen folder, reads tags, `Song` |
| `Store.kt` | SharedPreferences: folder, shuffle, repeat, playlists, likes, history |
| `Art.kt` | Embedded cover extraction, disk cache, the session's `BitmapLoader` |
| `Recommend.kt` | Builds the For you sections |
| `MonoApp.kt` | Library screens: search, tabs, lists, playlists |
| `NowPlaying.kt` | The bar, the full player, the spinning record |
| `Theme.kt` | Palette and type |

`Store` keeps the key names version 1 used (`folder_uri`, `shuffle`, `repeat`,
`playlists`), and tracks are still identified by their Storage Access Framework
document id, so playlists survive a rescan and future upgrades.
