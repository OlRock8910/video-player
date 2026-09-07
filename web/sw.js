// Caches the app shell so Mono opens with no network at all. Music is never
// cached here — it is read straight off the disk through the folder handle,
// so the only thing this holds is the few files that make up the interface.
//
// Bump CACHE when any shell file changes; the old cache is dropped on activate.
const CACHE = "mono-shell-v6";

const SHELL = [
  "./",
  "./index.html",
  "./styles.css",
  "./app.js",
  "./lib/store.js",
  "./lib/tags.js",
  "./lib/library.js",
  "./lib/player.js",
  "./lib/recommend.js",
  "./lib/ui.js",
  "./lib/icons.js",
  "./manifest.webmanifest",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/maskable-512.png",
  "./icons/favicon-64.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;

  // Network first so a deploy is picked up promptly, falling back to the cached
  // shell when offline. Successful responses refresh the cache as they pass.
  event.respondWith(
    fetch(request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(request, copy)).catch(() => {});
        return response;
      })
      .catch(() => caches.match(request).then((hit) => hit || caches.match("./index.html"))),
  );
});
