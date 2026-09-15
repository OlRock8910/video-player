/*
  Playback: one audio element, a queue, and the OS media integration.

  The queue lives here rather than in the view, for the same reason it was moved
  into the playback service on Android — so that advancing at the end of a track,
  the media keys, and the Windows media overlay all read from one place and stay
  in agreement.
*/

import { artUrl } from "./library.js";

export class Player {
  /**
   * @param audio  the <audio> element
   * @param store  settings, for shuffle/repeat/play counts
   * @param onChange called whenever anything the interface shows has moved
   */
  constructor(audio, store, onChange) {
    this.audio = audio;
    this.store = store;
    this.onChange = onChange;

    this.queue = [];
    this.order = [];
    this.at = -1;
    this.current = null;
    this.objectUrl = null;
    this.countedPath = null;

    // Time actually spent listening to the current track, measured on the wall
    // clock while audio is playing. Track length would over-count anyone who
    // skips; currentTime would over-count a seek to the end.
    this.listenedMs = 0;
    this.lastTick = null;

    this.audio.volume = store.volume;

    audio.addEventListener("ended", () => this.advance(true));
    audio.addEventListener("play", () => {
      this.lastTick = Date.now();
      this.updateSessionState();
      onChange();
    });
    audio.addEventListener("pause", () => {
      this.flushListening();
      this.saveResume();
      this.updateSessionState();
      onChange();
    });
    audio.addEventListener("timeupdate", () => {
      this.tickListening();
      this.countPlayIfStarted();
      onChange("tick");
    });
    audio.addEventListener("loadedmetadata", () => {
      this.updateSessionState();
      onChange();
    });
    audio.addEventListener("error", () => {
      // A file that has been moved or deleted should not stall the queue.
      if (this.current) this.advance(true);
    });

    window.addEventListener("pagehide", () => this.flushListening());
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") this.flushListening();
    });

    this.wireMediaKeys();
  }

  /** Adds the time since the last tick, ignoring gaps a sleeping machine leaves. */
  tickListening() {
    const now = Date.now();
    if (this.lastTick !== null && this.playing) {
      const delta = now - this.lastTick;
      if (delta > 0 && delta < 5000) this.listenedMs += delta;
    }
    this.lastTick = now;
  }

  /** Writes what has accrued for the current track to the log and resets. */
  flushListening() {
    this.tickListening();
    if (this.current && this.listenedMs > 0) {
      this.store.logListening(this.current.path, this.listenedMs);
    }
    this.listenedMs = 0;
    this.lastTick = null;
  }

  get playing() {
    return !this.audio.paused && !this.audio.ended;
  }

  get positionMs() {
    return Math.floor((this.audio.currentTime || 0) * 1000);
  }

  get durationMs() {
    const value = this.audio.duration;
    if (Number.isFinite(value) && value > 0) return Math.floor(value * 1000);
    return this.current?.durationMs || 0;
  }

  /** Builds the play order for `queue`, honouring shuffle, starting at `song`. */
  playFrom(queue, song) {
    if (!queue.length) return;
    this.queue = [...queue];
    const startIndex = Math.max(0, queue.findIndex((it) => it.path === song?.path));

    const rest = this.queue.map((_, i) => i).filter((i) => i !== startIndex);
    if (this.store.shuffle) shuffleInPlace(rest);
    this.order = [startIndex, ...rest];
    this.at = 0;
    this.load(true);
  }

  playShuffled(queue) {
    if (!queue.length) return;
    this.store.shuffle = true;
    this.playFrom(queue, queue[Math.floor(Math.random() * queue.length)]);
  }

  async load(autoplay) {
    const index = this.order[this.at];
    const song = this.queue[index];
    if (!song) return;

    this.flushListening();
    this.current = song;
    this.countedPath = null;
    this.onChange();

    let file;
    try {
      file = await song.handle.getFile();
    } catch {
      // Permission lapsed or the file is gone; step over it.
      this.advance(true);
      return;
    }

    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = URL.createObjectURL(file);
    this.audio.src = this.objectUrl;

    if (autoplay) {
      try {
        await this.audio.play();
      } catch {
        // Autoplay can be refused before the first interaction; the button works.
      }
    }
    this.updateSessionMetadata();
    this.onChange();
  }

  toggle() {
    if (!this.current) {
      if (this.queue.length) this.load(true);
      return;
    }
    if (this.playing) this.audio.pause();
    else this.audio.play().catch(() => {});
  }

  /** @param auto true when the track ended by itself rather than being skipped */
  advance(auto = false) {
    if (!this.order.length) return;

    if (auto && this.store.repeat === "one") {
      this.audio.currentTime = 0;
      this.audio.play().catch(() => {});
      return;
    }
    if (this.at + 1 < this.order.length) {
      this.at += 1;
    } else if (this.store.repeat === "all" || !auto) {
      this.at = 0;
    } else {
      this.audio.pause();
      return;
    }
    this.load(true);
  }

  /** Restarts the track when more than three seconds in, else steps back. */
  previous() {
    if (!this.order.length) return;
    if (this.audio.currentTime > 3) {
      this.audio.currentTime = 0;
      return;
    }
    this.at = this.at > 0 ? this.at - 1 : this.order.length - 1;
    this.load(true);
  }

  seekTo(ms) {
    if (!this.current) return;
    this.audio.currentTime = Math.max(0, ms) / 1000;
    this.updateSessionState();
    this.onChange();
  }

  setVolume(value) {
    this.audio.volume = value;
    this.store.volume = value;
  }

  toggleShuffle() {
    this.store.shuffle = !this.store.shuffle;
    if (this.order.length) {
      // Keep playing what is playing; reorder only what comes after it.
      const currentIndex = this.order[this.at];
      const rest = this.queue.map((_, i) => i).filter((i) => i !== currentIndex);
      if (this.store.shuffle) shuffleInPlace(rest);
      else rest.sort((a, b) => a - b);
      this.order = [currentIndex, ...rest];
      this.at = 0;
    }
    this.onChange();
  }

  cycleRepeat() {
    const next = { off: "all", all: "one", one: "off" };
    this.store.repeat = next[this.store.repeat] || "off";
    this.onChange();
  }

  /**
   * A track counts as played once it is a few seconds in, so scrubbing past
   * things does not colour the For you tab.
   */
  countPlayIfStarted() {
    if (!this.current || this.countedPath === this.current.path) return;
    if (this.audio.currentTime < 5) return;
    this.countedPath = this.current.path;
    this.store.recordPlay(this.current.path);
  }

  saveResume() {
    if (this.current) this.store.setResume(this.current.path, this.positionMs);
  }

  // --- Media keys and the Windows media overlay ----------------------------

  wireMediaKeys() {
    if (!("mediaSession" in navigator)) return;
    const handlers = {
      play: () => this.audio.play().catch(() => {}),
      pause: () => this.audio.pause(),
      previoustrack: () => this.previous(),
      nexttrack: () => this.advance(false),
      stop: () => this.audio.pause(),
      seekbackward: (details) => this.seekTo(this.positionMs - (details.seekOffset || 10) * 1000),
      seekforward: (details) => this.seekTo(this.positionMs + (details.seekOffset || 10) * 1000),
      seekto: (details) => this.seekTo((details.seekTime || 0) * 1000),
    };
    for (const [action, handler] of Object.entries(handlers)) {
      try {
        navigator.mediaSession.setActionHandler(action, handler);
      } catch {
        // Chrome refuses actions it does not implement; the rest still bind.
      }
    }
  }

  async updateSessionMetadata() {
    if (!("mediaSession" in navigator) || !this.current) return;
    const song = this.current;
    const artwork = [];
    const url = await artUrl(song.path);
    if (url) artwork.push({ src: url, sizes: "512x512", type: "image/jpeg" });
    else artwork.push({ src: "./icons/icon-512.png", sizes: "512x512", type: "image/png" });

    // The track may have changed while the cover was being fetched.
    if (this.current !== song) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: song.title,
      artist: song.artist,
      album: song.album || song.folder || "MONO",
      artwork,
    });
  }

  updateSessionState() {
    if (!("mediaSession" in navigator)) return;
    navigator.mediaSession.playbackState = this.playing ? "playing" : "paused";
    const duration = this.audio.duration;
    if (!Number.isFinite(duration) || duration <= 0) return;
    try {
      navigator.mediaSession.setPositionState({
        duration,
        position: Math.min(this.audio.currentTime, duration),
        playbackRate: this.audio.playbackRate || 1,
      });
    } catch {
      // Thrown if position briefly exceeds duration mid-seek; harmless.
    }
  }
}

function shuffleInPlace(list) {
  for (let i = list.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [list[i], list[j]] = [list[j], list[i]];
  }
  return list;
}
