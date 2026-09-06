/*
  Reads title, artist, album and embedded cover art straight out of the audio
  file.

  The browser gives no equivalent of Android's MediaMetadataRetriever, so the
  containers are parsed here: ID3v2 for MP3 (2.2, 2.3 and 2.4 frame layouts all
  differ), and Vorbis comments plus PICTURE blocks for FLAC. Anything else falls
  back to the "Artist - Title.ext" filename convention, which is the same
  fallback the phone app uses.
*/

const utf8 = new TextDecoder("utf-8");
const latin1 = new TextDecoder("windows-1252");
const utf16le = new TextDecoder("utf-16le");
const utf16be = new TextDecoder("utf-16be");

/** ID3 text frames start with a byte naming the encoding of what follows. */
function decodeText(bytes, encoding) {
  if (encoding === 0) return latin1.decode(bytes);
  if (encoding === 3) return utf8.decode(bytes);
  if (encoding === 2) return utf16be.decode(bytes);
  // Encoding 1 carries a byte-order mark.
  if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xfe) {
    return utf16le.decode(bytes.subarray(2));
  }
  if (bytes.length >= 2 && bytes[0] === 0xfe && bytes[1] === 0xff) {
    return utf16be.decode(bytes.subarray(2));
  }
  return utf16le.decode(bytes);
}

const trimNul = (text) => text.replace(/\0+$/, "").trim();

/** ID3 sizes are "synchsafe": seven bits per byte, high bit always clear. */
const synchsafe = (v, o) =>
  ((v[o] & 0x7f) << 21) | ((v[o + 1] & 0x7f) << 14) | ((v[o + 2] & 0x7f) << 7) | (v[o + 3] & 0x7f);

const uint32 = (v, o) => (v[o] << 24) | (v[o + 1] << 16) | (v[o + 2] << 8) | v[o + 3];

function parseId3(bytes) {
  const out = {};
  if (bytes.length < 10 || bytes[0] !== 0x49 || bytes[1] !== 0x44 || bytes[2] !== 0x33) return out;

  const major = bytes[3];
  const tagSize = synchsafe(bytes, 6);
  const end = Math.min(10 + tagSize, bytes.length);
  const idLength = major === 2 ? 3 : 4;
  const headerLength = major === 2 ? 6 : 10;

  let at = 10;
  // An extended header, when present, sits between the tag header and frame one.
  if (major >= 3 && bytes[5] & 0x40) {
    at += major === 4 ? synchsafe(bytes, at) : uint32(bytes, at) + 4;
  }

  while (at + headerLength <= end) {
    const id = latin1.decode(bytes.subarray(at, at + idLength));
    if (!/^[A-Z0-9]+$/.test(id)) break; // padding, or we have lost the thread

    let size;
    if (major === 2) size = (bytes[at + 3] << 16) | (bytes[at + 4] << 8) | bytes[at + 5];
    else if (major === 4) size = synchsafe(bytes, at + 4);
    else size = uint32(bytes, at + 4);

    const start = at + headerLength;
    if (size <= 0 || start + size > end) break;
    const body = bytes.subarray(start, start + size);

    if (id === "TIT2" || id === "TT2") out.title = trimNul(decodeText(body.subarray(1), body[0]));
    else if (id === "TPE1" || id === "TP1") out.artist = trimNul(decodeText(body.subarray(1), body[0]));
    else if (id === "TALB" || id === "TAL") out.album = trimNul(decodeText(body.subarray(1), body[0]));
    else if ((id === "APIC" || id === "PIC") && !out.picture) out.picture = parseApic(body, id === "PIC");

    at = start + size;
  }
  return out;
}

/**
 * APIC: encoding, mime type, picture type, description, then the image. The
 * description is terminated the same way the encoding terminates strings, so
 * UTF-16 needs a two-byte terminator on an even boundary.
 */
function parseApic(body, isV22) {
  const encoding = body[0];
  let at = 1;

  let mime;
  if (isV22) {
    mime = latin1.decode(body.subarray(at, at + 3)).toLowerCase();
    mime = mime === "png" ? "image/png" : "image/jpeg";
    at += 3;
  } else {
    const stop = body.indexOf(0, at);
    if (stop < 0) return null;
    mime = latin1.decode(body.subarray(at, stop)).toLowerCase() || "image/jpeg";
    if (!mime.includes("/")) mime = mime === "png" ? "image/png" : "image/jpeg";
    at = stop + 1;
  }

  at += 1; // picture type

  if (encoding === 1 || encoding === 2) {
    while (at + 1 < body.length && !(body[at] === 0 && body[at + 1] === 0)) at += 2;
    at += 2;
  } else {
    const stop = body.indexOf(0, at);
    if (stop < 0) return null;
    at = stop + 1;
  }

  if (at >= body.length) return null;
  return new Blob([body.subarray(at)], { type: mime });
}

/** FLAC: "fLaC" then a chain of metadata blocks, each with a 4-byte header. */
function parseFlac(bytes) {
  const out = {};
  if (bytes.length < 8 || latin1.decode(bytes.subarray(0, 4)) !== "fLaC") return out;

  let at = 4;
  for (let guard = 0; guard < 64; guard++) {
    if (at + 4 > bytes.length) break;
    const last = (bytes[at] & 0x80) !== 0;
    const type = bytes[at] & 0x7f;
    const size = (bytes[at + 1] << 16) | (bytes[at + 2] << 8) | bytes[at + 3];
    const start = at + 4;
    if (start + size > bytes.length) break;
    const body = bytes.subarray(start, start + size);

    if (type === 4) Object.assign(out, parseVorbis(body));
    else if (type === 6 && !out.picture) out.picture = parseFlacPicture(body);

    if (last) break;
    at = start + size;
  }
  return out;
}

function parseVorbis(body) {
  const out = {};
  const view = new DataView(body.buffer, body.byteOffset, body.byteLength);
  let at = 4 + view.getUint32(0, true); // skip the vendor string
  if (at + 4 > body.length) return out;
  const count = view.getUint32(at, true);
  at += 4;

  for (let i = 0; i < count && at + 4 <= body.length; i++) {
    const size = view.getUint32(at, true);
    at += 4;
    if (at + size > body.length) break;
    const entry = utf8.decode(body.subarray(at, at + size));
    at += size;
    const split = entry.indexOf("=");
    if (split < 0) continue;
    const key = entry.slice(0, split).toUpperCase();
    const value = entry.slice(split + 1).trim();
    if (key === "TITLE" && !out.title) out.title = value;
    else if (key === "ARTIST" && !out.artist) out.artist = value;
    else if (key === "ALBUM" && !out.album) out.album = value;
  }
  return out;
}

function parseFlacPicture(body) {
  const view = new DataView(body.buffer, body.byteOffset, body.byteLength);
  let at = 4; // picture type
  const mimeLength = view.getUint32(at, false);
  at += 4;
  const mime = latin1.decode(body.subarray(at, at + mimeLength)) || "image/jpeg";
  at += mimeLength;
  const descLength = view.getUint32(at, false);
  at += 4 + descLength;
  at += 16; // width, height, depth, indexed colour count
  const dataLength = view.getUint32(at, false);
  at += 4;
  if (at + dataLength > body.length) return null;
  return new Blob([body.subarray(at, at + dataLength)], { type: mime });
}

/** "Artist - Title.mp3", the convention the phone app also falls back on. */
export function fromFileName(name) {
  const base = name.replace(/\.[^.]+$/, "");
  const parts = base.split(" - ");
  if (parts.length >= 2) {
    return { artist: parts[0].trim(), title: parts.slice(1).join(" - ").trim() };
  }
  return { artist: "", title: base.trim() };
}

/**
 * Reads only the head of the file. Tags live at the front in both containers,
 * and a cover rarely runs past a megabyte — worth capping, since a library
 * scan opens every file in turn.
 */
const HEAD_BYTES = 1_500_000;

export async function readTags(file) {
  const guess = fromFileName(file.name);
  let parsed = {};
  try {
    const head = new Uint8Array(await file.slice(0, HEAD_BYTES).arrayBuffer());
    if (head[0] === 0x49 && head[1] === 0x44 && head[2] === 0x33) parsed = parseId3(head);
    else if (head[0] === 0x66 && head[1] === 0x4c && head[2] === 0x61 && head[3] === 0x43) {
      parsed = parseFlac(head);
    }
  } catch {
    // Unreadable or malformed: the filename still gives us something to show.
  }

  return {
    title: parsed.title || guess.title || file.name,
    artist: parsed.artist || guess.artist || "Unknown artist",
    album: parsed.album || "",
    picture: parsed.picture || null,
  };
}
