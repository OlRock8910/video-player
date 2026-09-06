/*
  Mono for the desktop — the shell that ties folder access, the library, the
  player and the interface together.

  Design and behaviour follow the Android app: the same four tabs, the same For
  you sections, likes keyed to a stable path, and a record that turns while the
  music plays.
*/

import { Store } from "./lib/store.js";
import { scanFolder, artUrl, formatClock, formatLong, forgetArt } from "./lib/library.js";
import { sections as buildSections } from "./lib/recommend.js";
import { Player } from "./lib/player.js";
import { $, el, clear, toast, contextMenu, setArt } from "./lib/ui.js";
import { icon, setIcon } from "./lib/icons.js";

const LIKED = "Liked songs";

const TABS = [
  { id: "foryou", label: "For you" },
  { id: "songs", label: "Songs" },
  { id: "artists", label: "Artists" },
  { id: "playlists", label: "Playlists" },
];

const state = {
  songs: [],
  tab: "foryou",
  query: "",
  selection: new Set(),
  lastClicked: -1,
  openArtist: null,
  openPlaylist: null,
  scanning: false,
  scanNote: "",
};

let store;
let player;
let folderHandle = null;
let installPrompt = null;

// --- Boot -----------------------------------------------------------------

async function boot() {
  store = await Store.load();
  player = new Player($("audio"), store, onPlayerChange);

  wireChrome();
  wireKeyboard();
  wirePlayerScreen();
  registerServiceWorker();

  if (!window.showDirectoryPicker) {
    showWelcome(
      "Mono needs the File System Access API to read your music folder. " +
        "Chrome or Edge on desktop will work; Firefox and Safari do not support it yet.",
      true,
    );
    return;
  }

  folderHandle = await Store.folderHandle();
  if (!folderHandle) {
    showWelcome();
    return;
  }

  // A saved handle survives restarts, but the permission behind it may not; the
  // browser only re-grants it inside a user gesture, hence the button.
  const permission = await folderHandle.queryPermission({ mode: "read" });
  if (permission === "granted") {
    showShell();
    await rescan();
  } else {
    showWelcome("Mono remembers your folder. Reconnect it to carry on.", false, true);
  }
}

function showWelcome(note, fatal = false, reconnect = false) {
  $("welcome").hidden = false;
  $("shell").hidden = true;
  $("bar").hidden = true;
  const button = $("pick-folder");
  button.hidden = fatal;
  button.textContent = reconnect ? "Reconnect folder" : "Choose folder";
  const noteEl = $("welcome-note");
  noteEl.hidden = !note;
  noteEl.textContent = note || "";
  button.onclick = reconnect ? reconnectFolder : chooseFolder;
}

function showShell() {
  $("welcome").hidden = true;
  $("shell").hidden = false;
}

async function chooseFolder() {
  try {
    const handle = await window.showDirectoryPicker({ id: "mono-music", mode: "read" });
    folderHandle = handle;
    // Remembering the folder is a convenience; if the handle cannot be stored
    // the session still works, it just asks again next launch.
    await Store.setFolderHandle(handle).catch(() => {});
    forgetArt();
    showShell();
    await rescan();
  } catch {
    // The picker was dismissed.
  }
}

async function reconnectFolder() {
  const granted = await folderHandle.requestPermission({ mode: "read" });
  if (granted === "granted") {
    showShell();
    await rescan();
  } else {
    showWelcome("Permission was declined. Choose a folder to continue.", false, false);
  }
}

async function rescan() {
  if (!folderHandle || state.scanning) return;
  state.scanning = true;
  state.scanNote = "Scanning…";
  render();

  try {
    state.songs = await scanFolder(folderHandle, (done, total) => {
      state.scanNote = `Scanning ${done} of ${total}…`;
      $("scan-status").textContent = state.scanNote;
    });
  } catch {
    toast("Could not read that folder");
    state.songs = [];
  }

  state.scanning = false;
  state.scanNote = "";
  restoreIfIdle();
  render();
}

/** Parks the player on the last track played, so the bar is ready to press. */
function restoreIfIdle() {
  if (player.current || !store.data.resume) return;
  const song = state.songs.find((it) => it.path === store.data.resume.path);
  if (!song) return;
  player.queue = state.songs;
  player.order = state.songs.map((_, i) => i);
  player.at = state.songs.indexOf(song);
  player.current = song;
}

// --- Chrome ---------------------------------------------------------------

function wireChrome() {
  setIcon($("sel-clear"), "close");
  setIcon($("bar-prev"), "prev");
  setIcon($("bar-next"), "next");
  setIcon($("player-close"), "chevronDown", 26);
  setIcon($("ctl-prev"), "prev", 26);
  setIcon($("ctl-next"), "next", 26);

  $("pick-folder").onclick = chooseFolder;
  $("change-folder").onclick = chooseFolder;
  $("rescan").onclick = rescan;

  $("search").addEventListener("input", (e) => {
    state.query = e.target.value;
    render();
  });

  $("sel-clear").onclick = () => {
    state.selection.clear();
    render();
  };
  $("sel-all").onclick = () => {
    const list = visibleSongs();
    const all = list.length > 0 && list.every((s) => state.selection.has(s.path));
    state.selection = all ? new Set() : new Set(list.map((s) => s.path));
    render();
  };
  $("sel-play").onclick = () => {
    const chosen = selectedSongs();
    if (chosen.length) player.playFrom(chosen, chosen[0]);
    state.selection.clear();
    render();
  };
  $("sel-shuffle").onclick = () => {
    const chosen = selectedSongs();
    if (chosen.length) player.playShuffled(chosen);
    state.selection.clear();
    render();
  };
  $("sel-add").onclick = () => openPlaylistDialog([...state.selection]);

  $("bar-open").onclick = openPlayer;
  $("bar-play").onclick = () => player.toggle();
  $("bar-next").onclick = () => player.advance(false);
  $("bar-prev").onclick = () => player.previous();
  $("bar-like").onclick = () => {
    if (player.current) toggleLike(player.current);
  };

  // Offered by Chrome and Edge when the app is installable; clicking it is what
  // puts the icon on the taskbar and in the Start menu.
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    installPrompt = event;
    renderInstallButton();
  });
  window.addEventListener("appinstalled", () => {
    installPrompt = null;
    renderInstallButton();
    toast("Mono installed");
  });
}

function renderInstallButton() {
  const existing = $("install-btn");
  if (!installPrompt) {
    existing?.remove();
    return;
  }
  if (existing) return;
  const button = el("button.pill.pill-filled.small", {
    id: "install-btn",
    text: "Install",
    onclick: async () => {
      installPrompt.prompt();
      await installPrompt.userChoice;
      installPrompt = null;
      renderInstallButton();
    },
  });
  $("chrome").append(button);
}

function wireKeyboard() {
  document.addEventListener("keydown", (event) => {
    const typing = /^(INPUT|TEXTAREA)$/.test(event.target.tagName);
    if (event.key === "Escape") {
      if (!$("player").hidden) closePlayer();
      else if (state.selection.size) {
        state.selection.clear();
        render();
      } else if (typing) event.target.blur();
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "f") {
      event.preventDefault();
      $("search").focus();
      $("search").select();
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "a" && !typing) {
      if (state.selection.size) {
        event.preventDefault();
        $("sel-all").click();
      }
      return;
    }
    if (typing || event.ctrlKey || event.metaKey || event.altKey) return;

    const keys = {
      " ": () => player.toggle(),
      k: () => player.toggle(),
      n: () => player.advance(false),
      p: () => player.previous(),
      s: () => player.toggleShuffle(),
      r: () => player.cycleRepeat(),
      l: () => player.current && toggleLike(player.current),
      arrowright: () => player.seekTo(player.positionMs + 5000),
      arrowleft: () => player.seekTo(player.positionMs - 5000),
      arrowup: () => player.setVolume(Math.min(1, player.audio.volume + 0.05)),
      arrowdown: () => player.setVolume(Math.max(0, player.audio.volume - 0.05)),
    };
    const run = keys[event.key.toLowerCase()];
    if (run) {
      event.preventDefault();
      run();
      render();
    }
  });
}

// --- Data views -----------------------------------------------------------

function matchingSongs() {
  const needle = state.query.trim().toLowerCase();
  if (!needle) return state.songs;
  return state.songs.filter(
    (song) =>
      song.title.toLowerCase().includes(needle) ||
      song.artist.toLowerCase().includes(needle) ||
      song.folder.toLowerCase().includes(needle),
  );
}

/** The list the current screen is showing — what select-all applies to. */
function visibleSongs() {
  if (state.openPlaylist) {
    const paths = new Set(
      state.openPlaylist === LIKED ? [...store.likes] : store.playlist(state.openPlaylist),
    );
    const inList = state.songs.filter((song) => paths.has(song.path));
    if (state.openPlaylist === LIKED) return inList;
    // A user playlist keeps its own order.
    const order = store.playlist(state.openPlaylist);
    return order.map((path) => inList.find((s) => s.path === path)).filter(Boolean);
  }
  if (state.openArtist) return state.songs.filter((song) => song.artist === state.openArtist);
  return matchingSongs();
}

function selectedSongs() {
  return state.songs.filter((song) => state.selection.has(song.path));
}

function toggleLike(song) {
  store.toggleLike(song.path);
  render();
}

// --- Render ---------------------------------------------------------------

function onPlayerChange(reason) {
  if (reason === "tick") {
    updateProgress();
    return;
  }
  render();
}

function render() {
  renderTabs();
  renderChromeState();
  renderContent();
  renderBar();
  renderPlayerScreen();
}

function renderChromeState() {
  const selecting = state.selection.size > 0;
  $("chrome").hidden = selecting;
  $("selectbar").hidden = !selecting;
  if (selecting) $("sel-count").textContent = `${state.selection.size} selected`;

  const status = $("scan-status");
  status.hidden = !state.scanning;
  status.textContent = state.scanNote;
}

function renderTabs() {
  const nav = clear($("tabs"));
  for (const tab of TABS) {
    nav.append(
      el("button.tab", {
        text: tab.label,
        role: "tab",
        "aria-selected": String(state.tab === tab.id),
        onclick: () => {
          state.tab = tab.id;
          state.openArtist = null;
          state.openPlaylist = null;
          render();
        },
      }),
    );
  }
}

function renderContent() {
  const main = clear($("content"));
  main.scrollTop = 0;

  if (state.scanning && !state.songs.length) {
    main.append(el("p.empty", { text: "Reading your folder…" }));
    return;
  }
  if (!state.songs.length) {
    main.append(el("p.empty", { text: "No audio files found in that folder." }));
    return;
  }

  if (state.openPlaylist) {
    const name = state.openPlaylist;
    renderSongList(main, {
      title: name,
      songs: visibleSongs(),
      onBack: () => {
        state.openPlaylist = null;
        render();
      },
      removeFrom: name === LIKED ? null : name,
    });
    return;
  }

  if (state.openArtist) {
    renderSongList(main, {
      title: state.openArtist,
      songs: visibleSongs(),
      onBack: () => {
        state.openArtist = null;
        render();
      },
    });
    return;
  }

  if (state.tab === "foryou") renderForYou(main);
  else if (state.tab === "songs") renderSongList(main, { title: "Songs", songs: matchingSongs() });
  else if (state.tab === "artists") renderArtists(main);
  else renderPlaylists(main);
}

function renderSongList(main, { title, songs, onBack, removeFrom }) {
  const head = el("div.list-head");
  if (onBack) head.append(el("button.back", { text: "← Back", onclick: onBack }));
  head.append(el("h2", { text: title }));
  head.append(
    el("p.list-sub", {
      text: `${songs.length} songs · ${formatLong(songs.reduce((sum, s) => sum + s.durationMs, 0))}`,
    }),
  );
  if (songs.length) {
    head.append(
      el(
        "div.head-actions",
        {},
        el("button.pill.pill-filled", {
          text: "Play",
          onclick: () => player.playFrom(songs, songs[0]),
        }),
        el("button.pill", { text: "Shuffle", onclick: () => player.playShuffled(songs) }),
      ),
    );
  }
  main.append(head);

  if (!songs.length) {
    main.append(el("p.empty", { text: "Nothing here yet" }));
    return;
  }

  const rows = el("div.rows", { class: state.selection.size ? "selecting" : "" });
  songs.forEach((song, index) => {
    rows.append(songRow(song, songs, index, removeFrom));
  });
  main.append(rows);
}

function songRow(song, list, index, removeFrom) {
  const selected = state.selection.has(song.path);
  const liked = store.isLiked(song.path);
  const isCurrent = player.current?.path === song.path;

  const check = el("button.row-check", {
    title: "Select",
    "aria-label": "Select",
    onclick: (event) => {
      event.stopPropagation();
      toggleSelection(song, index, event);
    },
  }, icon("check", 14));

  const art = el("img.art", { alt: "", loading: "lazy" });
  setArt(art, artUrl(song.path));

  const text = el(
    "div.row-text",
    {},
    el("div.row-title", { text: song.title }),
    el("div.row-sub", { text: `${song.artist} · ${formatClock(song.durationMs)}` }),
  );

  const like = el(
    "button.icon-btn",
    {
      class: liked ? "on" : "",
      title: liked ? "Unlike" : "Like",
      "aria-label": liked ? "Unlike" : "Like",
      onclick: (event) => {
        event.stopPropagation();
        toggleLike(song);
      },
    },
    icon(liked ? "heart" : "heartOutline"),
  );

  const more = el(
    "button.icon-btn",
    {
      title: "More",
      "aria-label": "More",
      onclick: (event) => {
        event.stopPropagation();
        rowMenu(event, song, removeFrom);
      },
    },
    icon("more"),
  );

  const row = el(
    "div.row",
    {
      class: `${selected ? "selected" : ""} ${isCurrent ? "playing" : ""}`.trim(),
      onclick: (event) => {
        if (state.selection.size || event.ctrlKey || event.metaKey || event.shiftKey) {
          toggleSelection(song, index, event);
        } else {
          player.playFrom(list, song);
        }
      },
      oncontextmenu: (event) => {
        event.preventDefault();
        rowMenu(event, song, removeFrom);
      },
    },
    check,
    art,
    text,
    el("div.row-actions", {}, like, more),
  );
  return row;
}

function toggleSelection(song, index, event) {
  if (event.shiftKey && state.lastClicked >= 0) {
    const list = visibleSongs();
    const [from, to] = [state.lastClicked, index].sort((a, b) => a - b);
    for (let i = from; i <= to; i++) if (list[i]) state.selection.add(list[i].path);
  } else if (state.selection.has(song.path)) {
    state.selection.delete(song.path);
  } else {
    state.selection.add(song.path);
  }
  state.lastClicked = index;
  render();
}

function rowMenu(event, song, removeFrom) {
  const items = [
    { label: "Play", run: () => player.playFrom(visibleSongs(), song) },
    { label: "Add to playlist", run: () => openPlaylistDialog([song.path]) },
    {
      label: store.isLiked(song.path) ? "Unlike" : "Like",
      run: () => toggleLike(song),
    },
    {
      label: "Select",
      run: () => {
        state.selection.add(song.path);
        render();
      },
    },
  ];
  if (removeFrom) {
    items.push({
      label: "Remove from this playlist",
      run: () => {
        store.removeFromPlaylist(removeFrom, song.path);
        render();
      },
    });
  }
  contextMenu(event, items);
}

function renderForYou(main) {
  const list = buildSections({
    songs: matchingSongs(),
    likes: store.likes,
    playCounts: store.data.playCounts,
    lastPlayed: store.data.lastPlayed,
  });

  if (!list.length) {
    main.append(el("p.empty", { text: "Play a few tracks and this fills up." }));
    return;
  }

  for (const section of list) {
    const cards = el("div.cards");
    for (const song of section.songs) {
      const art = el("img.card-art", { alt: "", loading: "lazy" });
      setArt(art, artUrl(song.path));
      cards.append(
        el(
          "button.card",
          {
            onclick: () => player.playFrom(section.songs, song),
            oncontextmenu: (event) => {
              event.preventDefault();
              rowMenu(event, song, null);
            },
          },
          art,
          el("div.card-title", { text: song.title }),
          el("div.card-sub", { text: song.artist }),
        ),
      );
    }

    main.append(
      el(
        "section.section",
        {},
        el(
          "div.section-head",
          {},
          el("div", {}, el("h3", { text: section.title }), el("p", { text: section.subtitle })),
          el(
            "button.icon-btn",
            {
              title: `Shuffle ${section.title}`,
              onclick: () => player.playShuffled(section.songs),
            },
            icon("shuffle"),
          ),
          el(
            "button.icon-btn",
            {
              title: `Play ${section.title}`,
              onclick: () => player.playFrom(section.songs, section.songs[0]),
            },
            icon("play", 22),
          ),
        ),
        cards,
      ),
    );
  }
}

function renderArtists(main) {
  const groups = new Map();
  for (const song of matchingSongs()) {
    if (!groups.has(song.artist)) groups.set(song.artist, []);
    groups.get(song.artist).push(song);
  }
  const artists = [...groups.entries()].sort((a, b) =>
    a[0].toLowerCase().localeCompare(b[0].toLowerCase()),
  );

  main.append(
    el(
      "div.list-head",
      {},
      el("h2", { text: "Artists" }),
      el("p.list-sub", { text: `${artists.length} artists` }),
    ),
  );

  const rows = el("div.rows");
  for (const [artist, tracks] of artists) {
    const art = el("img.art.round", { alt: "", loading: "lazy" });
    setArt(art, artUrl(tracks[0].path));
    rows.append(
      el(
        "div.row",
        {
          onclick: () => {
            state.openArtist = artist;
            render();
          },
        },
        art,
        el(
          "div.row-text",
          {},
          el("div.row-title", { text: artist }),
          el("div.row-sub", { text: `${tracks.length} songs` }),
        ),
      ),
    );
  }
  main.append(rows);
}

function renderPlaylists(main) {
  main.append(
    el(
      "div.list-head",
      {},
      el("h2", { text: "Playlists" }),
      el("p.list-sub", { text: "Long lists start with a selection — pick tracks on the Songs tab." }),
    ),
  );

  const rows = el("div.rows");
  rows.append(playlistRow(LIKED, store.likes.size, true));
  for (const name of store.playlistNames()) {
    rows.append(playlistRow(name, store.playlist(name).length, false));
  }
  rows.append(
    el(
      "div.row",
      {
        onclick: () => openNameDialog(null),
      },
      el("span.art.tile", { style: "color:var(--accent)" }, icon("add", 24)),
      el("div.row-text", {}, el("div.row-title", { text: "Create new playlist" })),
    ),
  );
  main.append(rows);
}

function playlistRow(name, count, builtin) {
  const open = () => {
    state.openPlaylist = name;
    render();
  };

  const menu = (event) => {
    event.preventDefault();
    event.stopPropagation();
    const songs = () => {
      const paths = new Set(builtin ? [...store.likes] : store.playlist(name));
      return state.songs.filter((song) => paths.has(song.path));
    };
    const items = [
      { label: "Play", run: () => player.playFrom(songs(), songs()[0]) },
      { label: "Shuffle", run: () => player.playShuffled(songs()) },
    ];
    if (!builtin) {
      items.push(
        {
          label: "Shuffle order",
          run: () => {
            const shuffled = [...store.playlist(name)];
            for (let i = shuffled.length - 1; i > 0; i--) {
              const j = Math.floor(Math.random() * (i + 1));
              [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
            }
            store.setPlaylist(name, shuffled);
            toast(`Shuffled “${name}”`);
            render();
          },
        },
        {
          label: "Delete playlist",
          run: () => {
            store.deletePlaylist(name);
            if (state.openPlaylist === name) state.openPlaylist = null;
            render();
          },
        },
      );
    }
    contextMenu(event, items);
  };

  return el(
    "div.row",
    { onclick: open, oncontextmenu: menu },
    el(
      "span.art.tile",
      { style: builtin ? "color:var(--accent)" : "" },
      icon(builtin ? "heart" : "list", 22),
    ),
    el(
      "div.row-text",
      {},
      el("div.row-title", { text: name }),
      el("div.row-sub", { text: `${count} songs` }),
    ),
    el(
      "div.row-actions",
      {},
      el("button.icon-btn", { title: "More", onclick: menu }, icon("more")),
    ),
  );
}

// --- Now playing bar -------------------------------------------------------

function renderBar() {
  const song = player.current;
  const bar = $("bar");
  bar.hidden = !song;
  if (!song) return;

  $("bar-title").textContent = song.title;
  setArt($("bar-art"), artUrl(song.path));
  setIcon($("bar-play"), player.playing ? "pause" : "play", 22);
  const liked = store.isLiked(song.path);
  const like = $("bar-like");
  setIcon(like, liked ? "heart" : "heartOutline");
  like.classList.toggle("on", liked);
  updateProgress();
}

function updateProgress() {
  const song = player.current;
  if (!song) return;
  const position = player.positionMs;
  const duration = player.durationMs;
  $("bar-sub").textContent =
    `${formatClock(position)} / ${formatClock(duration)} · ${song.artist}`;
  const pct = duration > 0 ? (position / duration) * 100 : 0;
  $("bar-fill").style.width = `${pct}%`;

  if (!$("player").hidden) {
    const seek = $("seek");
    if (!seek.dataset.dragging) seek.value = String(Math.round(pct * 10));
    seek.style.setProperty("--played", `${pct}%`);
    $("time-now").textContent = formatClock(position);
    $("time-end").textContent = formatClock(duration);
  }
}

// --- Full-screen player ----------------------------------------------------

function wirePlayerScreen() {
  $("player-close").onclick = closePlayer;
  $("ctl-play").onclick = () => player.toggle();
  $("ctl-next").onclick = () => player.advance(false);
  $("ctl-prev").onclick = () => player.previous();
  $("ctl-shuffle").onclick = () => player.toggleShuffle();
  $("ctl-repeat").onclick = () => player.cycleRepeat();
  $("player-like").onclick = () => player.current && toggleLike(player.current);

  const seek = $("seek");
  seek.addEventListener("pointerdown", () => {
    seek.dataset.dragging = "1";
  });
  seek.addEventListener("input", () => {
    const pct = Number(seek.value) / 10;
    seek.style.setProperty("--played", `${pct}%`);
    $("time-now").textContent = formatClock((pct / 100) * player.durationMs);
  });
  const commit = () => {
    if (!seek.dataset.dragging) return;
    delete seek.dataset.dragging;
    player.seekTo((Number(seek.value) / 1000) * player.durationMs);
  };
  seek.addEventListener("change", commit);
  seek.addEventListener("pointerup", commit);
  seek.addEventListener("pointercancel", commit);
}

function openPlayer() {
  if (!player.current) return;
  $("player").hidden = false;
  renderPlayerScreen();
}

function closePlayer() {
  $("player").hidden = true;
}

function renderPlayerScreen() {
  const screen = $("player");
  if (screen.hidden) return;
  const song = player.current;
  if (!song) {
    closePlayer();
    return;
  }

  $("player-title").textContent = song.title;
  $("player-artist").textContent = song.artist;
  $("player-folder").textContent = song.folder || song.album || "Now playing";
  setIcon($("ctl-play"), player.playing ? "pause" : "play", 28);

  const liked = store.isLiked(song.path);
  const like = $("player-like");
  setIcon(like, liked ? "heart" : "heartOutline");
  like.classList.toggle("on", liked);

  setIcon($("ctl-shuffle"), "shuffle");
  $("ctl-shuffle").classList.toggle("on", store.shuffle);
  const repeat = $("ctl-repeat");
  setIcon(repeat, store.repeat === "one" ? "repeatOne" : "repeat");
  repeat.classList.toggle("on", store.repeat !== "off");

  $("vinyl").classList.toggle("spinning", player.playing);

  setArt($("vinyl-art"), artUrl(song.path));
  artUrl(song.path).then((url) => {
    if (player.current?.path !== song.path) return;
    $("player-backdrop").style.backgroundImage = url ? `url("${url}")` : "none";
  });

  updateProgress();
}

// --- Playlist dialogs -------------------------------------------------------

let pendingPaths = null;

function openPlaylistDialog(paths) {
  pendingPaths = paths;
  const dialog = $("playlist-dialog");
  $("playlist-dialog-title").textContent =
    paths.length === 1 ? "Add to playlist" : `Add ${paths.length} songs`;

  const choices = clear($("playlist-choices"));
  const names = store.playlistNames();
  if (!names.length) {
    choices.append(el("p.note", { text: "No playlists yet — make one below." }));
  }
  for (const name of names) {
    choices.append(
      el("button.choice", {
        text: name,
        onclick: () => {
          store.addToPlaylist(name, pendingPaths);
          toast(`Added to “${name}”`);
          pendingPaths = null;
          state.selection.clear();
          dialog.close();
          render();
        },
      }),
    );
  }

  $("playlist-new").onclick = () => {
    dialog.close();
    openNameDialog(pendingPaths);
  };
  $("playlist-cancel").onclick = () => {
    pendingPaths = null;
    dialog.close();
  };
  dialog.showModal();
}

function openNameDialog(paths) {
  const dialog = $("name-dialog");
  const input = $("name-input");
  input.value = "";
  const hint = $("name-hint");
  hint.hidden = !paths?.length;
  if (paths?.length) {
    hint.textContent =
      paths.length === 1 ? "1 song will be added." : `${paths.length} songs will be added.`;
  }

  const create = () => {
    const name = input.value.trim();
    if (!name) return;
    store.createPlaylist(name);
    if (paths?.length) store.addToPlaylist(name, paths);
    pendingPaths = null;
    state.selection.clear();
    dialog.close();
    toast(`Created “${name}”`);
    render();
  };

  $("name-create").onclick = create;
  $("name-cancel").onclick = () => {
    pendingPaths = null;
    dialog.close();
  };
  input.onkeydown = (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      create();
    }
  };

  dialog.showModal();
  input.focus();
}

// --- Service worker ---------------------------------------------------------

function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("./sw.js").catch(() => {
      // Offline support is a bonus; the app runs fine without it.
    });
  });
}

boot();
