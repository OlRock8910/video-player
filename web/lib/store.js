/*
  Everything Mono remembers, in IndexedDB.

  Three stores: `kv` holds settings and the folder handle, `library` caches the
  last scan so a restart does not re-read every tag, and `art` holds cover
  images as blobs. Tracks are keyed by their path relative to the chosen folder,
  which is the desktop equivalent of the document id the Android app uses —
  stable across rescans, and what likes, counts and playlists point at.
*/

const DB_NAME = "mono";
const DB_VERSION = 1;

let dbPromise = null;

function open() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains("kv")) db.createObjectStore("kv");
      if (!db.objectStoreNames.contains("library")) db.createObjectStore("library");
      if (!db.objectStoreNames.contains("art")) db.createObjectStore("art");
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  return dbPromise;
}

function tx(store, mode, run) {
  return open().then(
    (db) =>
      new Promise((resolve, reject) => {
        const transaction = db.transaction(store, mode);
        const request = run(transaction.objectStore(store));
        transaction.oncomplete = () => resolve(request?.result);
        transaction.onerror = () => reject(transaction.error);
        transaction.onabort = () => reject(transaction.error);
      }),
  );
}

export const idb = {
  get: (store, key) => tx(store, "readonly", (s) => s.get(key)),
  set: (store, key, value) => tx(store, "readwrite", (s) => s.put(value, key)),
  del: (store, key) => tx(store, "readwrite", (s) => s.delete(key)),
  clear: (store) => tx(store, "readwrite", (s) => s.clear()),
};

/** A pause longer than this starts a new entry rather than extending the last. */
const RESUME_WINDOW_MS = 30 * 60 * 1000;

/** Roughly a year of heavy listening; older entries are dropped. */
const MAX_PLAY_LOG = 20000;

const DEFAULTS = {
  likes: [],
  plays: [],
  playCounts: {},
  lastPlayed: {},
  playlists: {},
  shuffle: false,
  repeat: "off",
  resume: null,
  volume: 1,
};

/**
 * Settings are read once at start-up and kept in memory; every mutation writes
 * the whole object straight back, which is cheap at this size and means a crash
 * can never leave half an update behind.
 */
export class Store {
  constructor(data) {
    this.data = { ...DEFAULTS, ...(data || {}) };
  }

  static async load() {
    const saved = await idb.get("kv", "settings").catch(() => null);
    return new Store(saved);
  }

  save() {
    // Structured-clone-safe copy; a stray undefined would abort the write.
    return idb.set("kv", "settings", JSON.parse(JSON.stringify(this.data))).catch(() => {});
  }

  // --- Folder -------------------------------------------------------------

  static folderHandle() {
    return idb.get("kv", "folder").catch(() => null);
  }

  static setFolderHandle(handle) {
    return idb.set("kv", "folder", handle);
  }

  // --- Likes --------------------------------------------------------------

  get likes() {
    return new Set(this.data.likes);
  }

  isLiked(path) {
    return this.data.likes.includes(path);
  }

  toggleLike(path) {
    const at = this.data.likes.indexOf(path);
    if (at >= 0) this.data.likes.splice(at, 1);
    else this.data.likes.push(path);
    this.save();
    return at < 0;
  }

  // --- History ------------------------------------------------------------

  recordPlay(path) {
    this.data.playCounts[path] = (this.data.playCounts[path] || 0) + 1;
    this.data.lastPlayed[path] = Date.now();
    this.save();
  }

  /**
   * Appends time actually spent listening to a track.
   *
   * Pausing and resuming the same track lands back in the previous entry rather
   * than starting a new one, so a single sitting reads as one play however many
   * times it was interrupted. The log is capped: at a few hundred plays a week
   * the oldest entries are worth less than the space they cost.
   */
  logListening(path, ms) {
    if (!path || !(ms > 1000)) return;
    const log = this.data.plays;
    const last = log[log.length - 1];
    const now = Date.now();

    if (last && last.p === path && now - (last.t + last.ms) < RESUME_WINDOW_MS) {
      last.ms += ms;
    } else {
      log.push({ p: path, t: now - ms, ms });
    }

    if (log.length > MAX_PLAY_LOG) log.splice(0, log.length - MAX_PLAY_LOG);
    this.save();
  }

  get plays() {
    return this.data.plays;
  }

  setResume(path, position) {
    this.data.resume = { path, position };
    this.save();
  }

  // --- Preferences --------------------------------------------------------

  get shuffle() {
    return this.data.shuffle;
  }

  set shuffle(value) {
    this.data.shuffle = value;
    this.save();
  }

  get repeat() {
    return this.data.repeat;
  }

  set repeat(value) {
    this.data.repeat = value;
    this.save();
  }

  get volume() {
    return this.data.volume;
  }

  set volume(value) {
    this.data.volume = value;
    this.save();
  }

  // --- Playlists ----------------------------------------------------------

  playlistNames() {
    return Object.keys(this.data.playlists).sort((a, b) =>
      a.toLowerCase().localeCompare(b.toLowerCase()),
    );
  }

  playlist(name) {
    return this.data.playlists[name] || [];
  }

  createPlaylist(name) {
    if (this.data.playlists[name]) return false;
    this.data.playlists[name] = [];
    this.save();
    return true;
  }

  addToPlaylist(name, paths) {
    const list = this.data.playlists[name] || [];
    const seen = new Set(list);
    for (const path of paths) {
      if (!seen.has(path)) {
        seen.add(path);
        list.push(path);
      }
    }
    this.data.playlists[name] = list;
    this.save();
  }

  removeFromPlaylist(name, path) {
    const list = this.data.playlists[name];
    if (!list) return;
    this.data.playlists[name] = list.filter((it) => it !== path);
    this.save();
  }

  /** Replaces a playlist's contents wholesale — used to shuffle its order. */
  setPlaylist(name, paths) {
    this.data.playlists[name] = [...paths];
    this.save();
  }

  deletePlaylist(name) {
    delete this.data.playlists[name];
    this.save();
  }

  renamePlaylist(from, to) {
    if (!this.data.playlists[from] || this.data.playlists[to]) return;
    this.data.playlists[to] = this.data.playlists[from];
    delete this.data.playlists[from];
    this.save();
  }
}
