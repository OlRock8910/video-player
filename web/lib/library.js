/*
  Walking the chosen folder and turning it into a list of tracks.

  A scan is the slow part of using Mono, so results are cached in IndexedDB and
  keyed by path. A cached entry is reused when the file's size and modified time
  still match, which means a rescan after adding one album only reads tags for
  that album.
*/

import { idb } from "./store.js";
import { readTags } from "./tags.js";

export const AUDIO_EXTS = new Set(["mp3", "m4a", "flac", "wav", "ogg", "opus", "aac"]);

export const isAudio = (name) => AUDIO_EXTS.has(name.split(".").pop().toLowerCase());

/** Tracks whose tags we can actually read; the rest fall back to filenames. */
const TAGGED_EXTS = new Set(["mp3", "flac"]);

export function formatClock(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function formatLong(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  return hours > 0 ? `${hours} hr ${minutes} min` : `${minutes} min`;
}

/**
 * Duration is the one thing no header parser here produces reliably (VBR MP3s
 * lie), so the browser's own decoder is asked instead. It only reads far enough
 * to answer, and the object URL is released either way.
 */
function readDuration(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    const done = (value) => {
      URL.revokeObjectURL(url);
      audio.removeAttribute("src");
      resolve(value);
    };
    const timer = setTimeout(() => done(0), 5000);
    audio.preload = "metadata";
    audio.onloadedmetadata = () => {
      clearTimeout(timer);
      done(Number.isFinite(audio.duration) ? Math.round(audio.duration * 1000) : 0);
    };
    audio.onerror = () => {
      clearTimeout(timer);
      done(0);
    };
    audio.src = url;
  });
}

/** Depth-first walk, mirroring the phone app's five-level cap. */
async function collect(dirHandle, prefix, depth, out) {
  if (depth > 5) return;
  for await (const entry of dirHandle.values()) {
    if (entry.kind === "directory") {
      await collect(entry, prefix ? `${prefix}/${entry.name}` : entry.name, depth + 1, out);
    } else if (isAudio(entry.name)) {
      out.push({
        handle: entry,
        name: entry.name,
        path: prefix ? `${prefix}/${entry.name}` : entry.name,
        folder: prefix.split("/").pop() || "",
      });
    }
  }
}

/** Runs `worker` over `items`, at most `limit` at a time. */
async function pooled(items, limit, worker) {
  let index = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (index < items.length) {
      const mine = index++;
      await worker(items[mine], mine);
    }
  });
  await Promise.all(runners);
}

export async function scanFolder(dirHandle, onProgress = () => {}) {
  const found = [];
  await collect(dirHandle, "", 0, found);

  const cache = (await idb.get("library", "tracks").catch(() => null)) || {};
  const next = {};
  const songs = new Array(found.length);
  let done = 0;

  await pooled(found, 6, async (entry, at) => {
    let file = null;
    try {
      file = await entry.handle.getFile();
    } catch {
      // The file vanished between the walk and here; skip it.
    }

    if (file) {
      const cached = cache[entry.path];
      const fresh =
        cached && cached.size === file.size && cached.mtime === file.lastModified
          ? cached
          : null;

      let meta = fresh;
      if (!meta) {
        const ext = entry.name.split(".").pop().toLowerCase();
        const tags = await readTags(file);
        const duration = await readDuration(file);
        meta = {
          size: file.size,
          mtime: file.lastModified,
          title: tags.title,
          artist: tags.artist,
          album: tags.album,
          durationMs: duration,
          hasArt: Boolean(tags.picture),
          tagged: TAGGED_EXTS.has(ext),
        };
        if (tags.picture) await idb.set("art", entry.path, tags.picture).catch(() => {});
        else await idb.del("art", entry.path).catch(() => {});
      }

      next[entry.path] = meta;
      songs[at] = {
        handle: entry.handle,
        path: entry.path,
        name: entry.name,
        folder: entry.folder || meta.album || "",
        title: meta.title,
        artist: meta.artist,
        album: meta.album,
        durationMs: meta.durationMs,
        hasArt: meta.hasArt,
        addedAt: meta.mtime,
      };
    }

    done += 1;
    onProgress(done, found.length);
  });

  await idb.set("library", "tracks", next).catch(() => {});

  return songs
    .filter(Boolean)
    .sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()));
}

/*
  Cover art. Blobs live in IndexedDB; object URLs are created on demand and kept
  in a bounded map, because a URL that is never revoked pins its blob in memory
  for the life of the page.
*/

const urls = new Map();
const MAX_URLS = 120;

export async function artUrl(path) {
  if (!path) return null;
  if (urls.has(path)) return urls.get(path);

  const blob = await idb.get("art", path).catch(() => null);
  if (!blob) {
    urls.set(path, null);
    return null;
  }

  if (urls.size >= MAX_URLS) {
    const oldest = urls.keys().next().value;
    const stale = urls.get(oldest);
    if (stale) URL.revokeObjectURL(stale);
    urls.delete(oldest);
  }

  const url = URL.createObjectURL(blob);
  urls.set(path, url);
  return url;
}

export function forgetArt() {
  for (const url of urls.values()) if (url) URL.revokeObjectURL(url);
  urls.clear();
}
