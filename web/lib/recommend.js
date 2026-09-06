/*
  The For you tab, ported from the Android app's Recommend.kt so both stay in
  step. There is no music service to ask, so a suggestion here means "you own
  this, and here is why it is worth playing": what you keep going back to, what
  you have not heard in a while, and more by the artists you liked.
*/

const ROW = 12;

export function sections({
  songs,
  likes,
  playCounts,
  lastPlayed,
  day = Math.floor(Date.now() / 86_400_000),
}) {
  if (!songs.length) return [];
  const out = [];
  const countOf = (song) => playCounts[song.path] || 0;

  const recent = songs
    .filter((song) => lastPlayed[song.path])
    .sort((a, b) => (lastPlayed[b.path] || 0) - (lastPlayed[a.path] || 0))
    .slice(0, ROW);
  if (recent.length) {
    out.push({ title: "Recently played", subtitle: "Pick up where you left off", songs: recent });
  }

  const mostPlayed = songs
    .filter((song) => countOf(song) > 1)
    .sort((a, b) => countOf(b) - countOf(a))
    .slice(0, ROW);
  if (mostPlayed.length) {
    out.push({ title: "On repeat", subtitle: "Your most played", songs: mostPlayed });
  }

  const liked = songs.filter((song) => likes.has(song.path));
  if (liked.length) {
    out.push({ title: "Liked songs", subtitle: `${liked.length} tracks`, songs: liked });

    // More by the artists behind the likes — the liked tracks themselves
    // excluded, since those are one row up.
    const likedArtists = new Set(liked.map((song) => song.artist.toLowerCase()));
    const moreLikeThis = songs
      .filter((song) => likedArtists.has(song.artist.toLowerCase()) && !likes.has(song.path))
      .sort((a, b) => countOf(b) - countOf(a))
      .slice(0, ROW);
    if (moreLikeThis.length) {
      const names = [...new Set(liked.map((song) => song.artist))];
      out.push({
        title: "More like this",
        subtitle: names.length === 1 ? `Because you like ${names[0]}` : "Based on what you liked",
        songs: moreLikeThis,
      });
    }
  }

  const added = songs
    .filter((song) => song.addedAt > 0)
    .sort((a, b) => b.addedAt - a.addedAt)
    .slice(0, ROW);
  if (added.length) {
    out.push({ title: "Recently added", subtitle: "Newest in your folder", songs: added });
  }

  // A rotating handful that have never been played. The window moves with the
  // day so the row is not identical every time the tab is opened, without
  // needing anything stored.
  const unplayed = songs
    .filter((song) => !playCounts[song.path])
    .sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()));
  if (unplayed.length > 2) {
    const start = ((day % unplayed.length) + unplayed.length) % unplayed.length;
    const window = [];
    for (let i = 0; i < Math.min(ROW, unplayed.length); i++) {
      window.push(unplayed[(start + i) % unplayed.length]);
    }
    out.push({ title: "Never played", subtitle: "Buried in your library", songs: window });
  }

  return out;
}
