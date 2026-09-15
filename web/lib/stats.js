/*
  Listening statistics.

  The play counts Mono already kept could not answer "this week": they record how
  many times a track has ever been played and when it was last played, which
  collapses all history into one number and one timestamp. So playback now writes
  an append-only log of listening chunks, and everything here is derived from it.

  Time is measured as time actually listened — wall clock while audio was
  playing — not track length, so skipping through an album does not bank forty
  minutes.
*/

export const PERIODS = [
  { id: "today", label: "Today" },
  { id: "week", label: "7 days" },
  { id: "month", label: "30 days" },
  { id: "year", label: "12 months" },
  { id: "all", label: "All time" },
];

const DAY = 86_400_000;

const startOfDay = (ms) => {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
};

const startOfMonth = (ms) => {
  const d = new Date(ms);
  d.setDate(1);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
};

const addMonths = (ms, n) => {
  const d = new Date(ms);
  d.setMonth(d.getMonth() + n);
  return d.getTime();
};

/** The window a period covers, and the bucket size its chart should use. */
export function rangeFor(id, plays = [], now = Date.now()) {
  switch (id) {
    case "today":
      return { from: startOfDay(now), to: now, unit: "hour" };
    case "week":
      return { from: startOfDay(now - 6 * DAY), to: now, unit: "day" };
    case "month":
      return { from: startOfDay(now - 29 * DAY), to: now, unit: "day" };
    case "year":
      return { from: addMonths(startOfMonth(now), -11), to: now, unit: "month" };
    default: {
      const earliest = plays.length ? Math.min(...plays.map((p) => p.t)) : now;
      return { from: startOfMonth(earliest), to: now, unit: "month" };
    }
  }
}

function bucketStarts(from, to, unit) {
  const out = [];
  if (unit === "hour") {
    for (let t = from; t <= to; t += 3_600_000) out.push(t);
  } else if (unit === "day") {
    for (let t = from; t <= to; t += DAY) out.push(startOfDay(t));
  } else {
    let t = startOfMonth(from);
    while (t <= to) {
      out.push(t);
      t = addMonths(t, 1);
    }
  }
  return out;
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function bucketLabel(start, unit, spanDays) {
  const d = new Date(start);
  if (unit === "hour") return `${String(d.getHours()).padStart(2, "0")}:00`;
  if (unit === "month") return MONTHS[d.getMonth()];
  // A week reads better by weekday; a month of days needs the date.
  return spanDays <= 7 ? WEEKDAYS[d.getDay()] : `${d.getDate()} ${MONTHS[d.getMonth()]}`;
}

function endOf(start, unit) {
  if (unit === "hour") return start + 3_600_000;
  if (unit === "day") return start + DAY;
  return addMonths(start, 1);
}

/** "2 hr 14 min", "47 min", "38 sec" — the long form used for totals. */
export function formatSpan(ms) {
  const total = Math.max(0, Math.round(ms / 1000));
  if (total < 60) return `${total} sec`;
  const hours = Math.floor(total / 3600);
  const minutes = Math.round((total % 3600) / 60);
  if (hours === 0) return `${minutes} min`;
  if (minutes === 0) return `${hours} hr`;
  return `${hours} hr ${minutes} min`;
}

/**
 * @param plays  log entries {p: path, t: started at, ms: listened}
 * @param songs  the current library, for titles and artists
 */
export function summarise({ plays, songs, from, to, unit }) {
  const inRange = plays.filter((play) => play.t >= from && play.t <= to);
  const byPath = new Map(songs.map((song) => [song.path, song]));

  const starts = bucketStarts(from, to, unit);
  const spanDays = Math.round((to - from) / DAY) + 1;
  const buckets = starts.map((start) => ({
    start,
    label: bucketLabel(start, unit, spanDays),
    ms: 0,
  }));

  const perTrack = new Map();
  let totalMs = 0;

  for (const play of inRange) {
    totalMs += play.ms;

    // Buckets are contiguous and ordered, so a scan from the end finds the
    // owning bucket without a lookup table.
    for (let i = buckets.length - 1; i >= 0; i--) {
      if (play.t >= buckets[i].start && play.t < endOf(buckets[i].start, unit)) {
        buckets[i].ms += play.ms;
        break;
      }
    }

    const seen = perTrack.get(play.p) || { path: play.p, ms: 0, count: 0 };
    seen.ms += play.ms;
    seen.count += 1;
    perTrack.set(play.p, seen);
  }

  const tracks = [...perTrack.values()].map((entry) => {
    const song = byPath.get(entry.path);
    return {
      ...entry,
      song: song || null,
      title: song?.title || entry.path.split("/").pop(),
      artist: song?.artist || "Unknown artist",
    };
  });

  const byArtist = new Map();
  for (const track of tracks) {
    const seen = byArtist.get(track.artist) || { artist: track.artist, ms: 0, count: 0 };
    seen.ms += track.ms;
    seen.count += track.count;
    byArtist.set(track.artist, seen);
  }

  const byTime = (a, b) => b.ms - a.ms;

  return {
    totalMs,
    playCount: inRange.length,
    uniqueCount: perTrack.size,
    // Averaged over days that had listening, not over the calendar — a daily
    // average that counts silent days says more about the window than the habit.
    activeDays: new Set(inRange.map((play) => startOfDay(play.t))).size,
    buckets,
    topTracks: tracks.sort(byTime).slice(0, 5),
    topArtists: [...byArtist.values()].sort(byTime).slice(0, 5),
  };
}
