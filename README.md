# MONO

A local music player for Android. Point it at a folder, and it plays the MP3s
(and m4a, flac, wav, ogg, opus, aac) it finds inside — no account, no network,
no library import step.

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
| Other audio | Audio focus was requested | Unchanged, and now correct: pausing no longer loses the queue |

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
