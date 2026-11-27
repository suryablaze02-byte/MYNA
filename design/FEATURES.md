# MYNA – full feature list (for review)

How to review: put a ✗ next to anything to drop, a ✓ next to anything you're unsure about but want, and answer the **❓ questions**.
Tags: **[v1]** = in the first release · **[later]** = after v1 · **[A]** = Android only · **[W]** = Windows only · no tag = both.

---

## 1. App basics
1. Name **MYNA**, new app ID, installs next to FireTheDJ without clashing. [v1]
2. Plays **your own music files** only: no accounts, no ads, no tracking, works fully offline. [v1]
3. Android phone (portrait; landscape on tablets) and a Windows 10/11 desktop app. [v1]
4. Liquid-glass look from FireTheDJ: clear panels, bright rim on the top-left edge, no live blur (cheap on battery). [v1]
5. Teal accent `#2DD4BF` replaces FireTheDJ's orange. [v1]
6. Light, dark, or follow the system. [v1]
7. Now Playing and the mini player tint themselves to the current cover's colour. [v1]
8. App icon: a myna bird silhouette in teal. ❓ Want me to draw 3 icon options?
9. Guided tour on first launch (3 short cards: pick folders → Home → swipe the mini player). [v1]
10. Updates itself: Settings shows new versions and "what's new", like FireTheDJ. [v1]
11. Crash-safe: remembers the queue, song and position, and resumes after a restart. [v1]

## 2. First launch & music library
1. Pick music folders (Android: storage permission; Windows: defaults to the `Music` folder, add more). [v1]
2. Rescans quietly on every launch; new files appear without a manual refresh. [v1]
3. Windows: watches folders live, so a file dropped in shows up within seconds. [v1][W]
4. Formats: MP3, AAC/M4A, FLAC, ALAC, OGG, Opus, WAV, AIFF. [v1]
5. Reads tags: title, artist, album artist, album, track/disc number, year, genre, composer, embedded cover, embedded lyrics. [v1]
6. Covers: embedded art first, then `cover.jpg`/`folder.jpg` in the folder, then an online lookup (can be turned off). [v1]
7. Synced lyrics: `.lrc` file next to the song, embedded lyrics, then an online lookup (LRCLIB). [v1]
8. Hide short clips: ignore files under 30 s (WhatsApp voice notes, ringtones). Setting, on by default. [v1]
9. Exclude folders (e.g. `Recordings`, `Notifications`). [v1]
10. Duplicate check: the same song in two folders shows once. [v1]
11. Groups "feat." artists under the main artist. [v1]
12. Edit song info (title, artist, album, cover) inside the app. [later]

## 3. Bottom bar (Android) / sidebar (Windows)
1. **3 tabs: Home · Library · Search.** [v1]
2. Active tab: teal icon and label on a lighter glass pill. [v1]
3. Tap the active tab again to scroll to the top. [v1]
4. Each tab remembers where you were scrolled. [v1]
5. Android back button: back inside the tab first, then to Home, then leaves the app. [v1][A]
6. Windows: the same 3 items as a left sidebar, with your playlists listed under them and Settings at the bottom. [v1][W]
7. Windows: sidebar collapses to icons when the window is narrow. [v1][W]

## 4. Mini player (above the bar)
1. Cover, title, artist, play/pause, next. [v1]
2. Thin teal progress line along its bottom edge. [v1]
3. Tap or swipe up → Now Playing. [v1]
4. Swipe left/right → next/previous song (with the crossfade). [v1]
5. Long-press → quick menu: Like, Add to playlist, Go to album, Go to artist. [v1]
6. Hidden when nothing has played yet. [v1]
7. Windows: becomes the full-width bar at the bottom (see section 12). [v1][W]

## 5. Home
1. Gear button (top right) → Settings. [v1]
2. **Jump back in**: 4 tiles (6 on Windows) of what you played recently: playlists, albums, artists, folders. [v1]
3. **Recently played**: songs, swipe sideways. [v1]
4. **Most played**: songs from the last 30 days. [v1]
5. **Liked songs** shortcut. [v1]
6. Auto playlists, refreshed daily:
   1. Not played in a while (liked or often-played songs untouched for 60+ days) [v1]
   2. Recently added [v1]
   3. On repeat (most played this week) [v1]
   4. Never played [v1]
   5. Your year so far [later]
7. Greeting by time of day ("Good evening"). ❓ Keep or drop?
8. Pull down to refresh the library. [v1][A]

## 6. Library
1. Chips along the top: **Songs · Albums · Artists · Playlists · Folders**. [v1]
2. Songs: Play all + Shuffle buttons at the top, with the song count. [v1]
3. Sort: title, artist, album, date added, most played, duration. [v1]
4. Fast-scroll letter rail on the right (A–Z). [v1]
5. Playing song shown in teal with a small moving bars icon. [v1]
6. Albums: grid of covers; album page has a big cover, year, songs in disc/track order, Play and Shuffle. [v1]
7. Artists: round photos (from the internet, or the first album cover); artist page lists top songs, then albums. [v1]
8. Playlists: create, rename, delete, reorder songs by dragging, add a cover. [v1]
9. Import and export `.m3u` playlists. [v1]
10. Folders: browse the real folder tree and play a folder. [v1]
11. Each song's ⋯ menu: Play next, Add to queue, Add to playlist, Like, Hide, Go to album, Go to artist, Song info, Share file, Delete file (asks first). [v1]
12. Select many songs (long-press) → add to playlist, queue, hide or delete in one go. [v1]
13. Liked and Hidden songs come over from FireTheDJ's code. Hidden songs never play in shuffle or auto playlists. [v1]

## 7. Search
1. One box: searches songs, albums, artists, playlists **and lyrics**. [v1]
2. Results as you type, grouped by type, best match at the top. [v1]
3. Forgives typos and missing accents ("beyonce" finds "Beyoncé"). [v1]
4. Recent searches, with a clear button. [v1]
5. Browse tiles below the box: **Moods** (Party, Chill, Focus, Workout, Sad, Romance) and **Genres** from your tags. [v1]
6. Moods come from genre tags + the taste survey. ❓ Is that good enough, or drop Moods?
7. Windows: `Ctrl+F` or `/` jumps to search. [v1][W]

## 8. Now Playing
1. Opens with a slide-up; swipe down or press back to close. [v1]
2. Big cover (animated if available, see section 10). [v1]
3. Background: a soft blur of the cover's colours, slowly drifting. [v1]
4. Swipe the cover left/right to change song. [v1]
5. Title, artist (tap → artist page), album (tap → album page). [v1]
6. Heart = Like. [v1]
7. Seek bar: drag to seek, shows elapsed and remaining time. [v1]
8. Controls: **shuffle · previous · play/pause · next · repeat** (off / all / one). [v1]
9. Previous: restarts the song if you're past 3 s, else goes back. [v1]
10. "NEXT · song · crossfade 6 s" line under the controls. [v1]
11. Bottom row: **Lyrics · Queue · Sleep timer · Output**. [v1]
12. ⋯ menu: Add to playlist, Go to album, Song info (format, bitrate, file path), Share, Hide. [v1]
13. Double-tap the cover to Like (heart pops on the cover). ❓ Keep?
14. Landscape (tablet / Windows): cover on the left, lyrics or queue on the right. [v1]

## 9. Lyrics
1. Synced lyrics scroll with the song; the current line is bright, the rest dimmed. [v1]
2. Tap a line to jump there. [v1]
3. Plain (unsynced) lyrics shown as a scrollable page. [v1]
4. Full-screen lyrics mode with the cover colour as background. [v1]
5. Lyrics offset ±0.5 s if a file is out of sync. [later]

## 10. Animated album covers (new)
1. **Real animated covers**: if the album folder has `cover.mp4`, `cover.webm`, `cover.gif` or an animated `cover.webp`, it plays as a silent loop. [v1]
2. **Living covers** for every normal cover: a slow zoom-and-drift (Ken Burns), a soft light sweep across the art, and the glow behind it gently pulsing with the music's loudness. [v1]
3. Where they move: Now Playing (full motion), the mini player (subtle), album pages. Grids and lists stay still so scrolling stays smooth. [v1]
4. Download Apple Music–style motion covers from an online source. ❓ No official free source exists; OK to skip?
5. Setting: **Animated covers: On / Wi-Fi only / Off**, and they stop in battery saver. [v1]
6. Respects the system "reduce motion" setting. [v1]
7. Pause the motion when the screen is off or the app is in the background. [v1]
8. Make an animated cover from a still one inside the app (pick a style, save as `cover.mp4`). [later]

## 11. Smooth fade & playback
1. **Crossfade** 0–12 s, default **6 s**: plain volume fade, no beat matching, no effects. [v1]
2. Equal-power curve, so the volume doesn't dip in the middle of the fade. [v1]
3. **Gapless albums**: no fade when the next song is the next track of the same album. [v1]
4. Skipping (tap next): a short 1 s fade instead of the full one. [v1]
5. Pause/play: a quick 0.2 s fade in and out, no clicks. [v1]
6. Seek: no fade. [v1]
7. Volume levelling (ReplayGain tags), off by default. [v1]
8. Shuffle never plays the same artist twice in a row and skips Hidden songs. [v1]
9. Queue: Play next, Add to queue, drag to reorder, swipe to remove, Clear queue, Save queue as playlist. [v1]
10. Playback speed 0.5×–2× and Skip silence (from BitChord). [later]
11. Equaliser: Android opens the system equaliser; Windows gets a 10-band one. [later]

## 12. Windows extras
1. Bottom player bar: cover + title + heart (left), controls + seek (centre), Lyrics · Queue · Output · volume (right). [v1][W]
2. Media keys on the keyboard work. [v1][W]
3. Windows media flyout (next to the volume pop-up) shows the song and controls. [v1][W]
4. Taskbar thumbnail with previous / play / next buttons. [v1][W]
5. Shortcuts: `Space` play/pause, `Ctrl+→/←` next/previous, `Ctrl+↑/↓` volume, `Ctrl+L` like, `Ctrl+F` search. [v1][W]
6. Drag a file or folder onto the window to play it. [v1][W]
7. "Open with MYNA" for audio files in File Explorer. [v1][W]
8. Mini window: a small always-on-top player. [later][W]
9. Remembers window size and position. [v1][W]
10. Installer (.msix or .exe) and self-update. [v1][W]

## 13. Android extras
1. Lock-screen and notification player with cover, heart, previous/play/next (today's FireTheDJ fix carried over). [v1][A]
2. Home-screen widget: cover, title, play/pause/next. [later][A]
3. Android Auto: browse Home, Library and playlists from the car. [v1][A]
4. Cast to Chromecast / Google TV and Bluetooth output picker. [v1][A]
5. Pauses when headphones are unplugged; resumes when Bluetooth reconnects (setting). [v1][A]
6. Pauses for calls and ducks for navigation voices. [v1][A]
7. Quick Settings tile. [later][A]
8. "Open with MYNA" from file managers and WhatsApp audio. [v1][A]

## 14. Sleep timer
1. Presets: 15, 30, 45, 60 min, End of song, End of album, custom. [v1]
2. Fades out over the last **30 s** instead of stopping dead. [v1]
3. Countdown shown on the Sleep button. [v1]
4. Shake the phone to add 10 min. ❓ Keep? [A]

## 15. Taste & stats
1. Taste survey from FireTheDJ on first launch (optional, skippable). Feeds Moods and auto playlists. [v1]
2. **Wrapped stats**: top songs, artists, albums, minutes listened, by month and year, as shareable cards. [v1]
3. Play counts only count when 30 s+ of a song was heard. [v1]
4. Last.fm scrobbling (from BitChord). [later]

## 16. Settings (gear on Home)
1. **Playback**: crossfade, gapless albums, volume levelling, skip fade length. [v1]
2. **Covers**: animated covers on/Wi-Fi only/off, download missing covers. [v1]
3. **Lyrics**: download missing lyrics. [v1]
4. **Library**: music folders, excluded folders, hide short clips, rescan now. [v1]
5. **Appearance**: light/dark/system, tint by cover on/off. [v1]
6. **Sleep timer**: fade-out length. [v1]
7. **Liked & hidden**: see and un-hide hidden songs. [v1]
8. **Stats**: Wrapped, reset play counts. [v1]
9. **Backup**: export/import likes, playlists and stats as one file (move between phone and PC). [v1]
10. **About**: version, updates, what's new, licences. [v1]

## 17. Accessibility & quality
1. Every button has a spoken label for TalkBack / Narrator. [v1]
2. Touch targets at least 48 dp. [v1]
3. Text sizes follow the system font size. [v1]
4. Text contrast 4.5:1 or more in light and dark. [v1]
5. Full keyboard use on Windows, with visible focus rings. [v1][W]
6. Smooth on a 10,000-song library (lists load only what's on screen). [v1]

---

## Not in MYNA (stays in FireTheDJ)
- Smooth/Trickster modes, crates, tricks, sound effects
- Song analysis (BPM, key, drops, stems), beat-matched transitions
- Double start, Weave, two-deck and waveform screens
- Visualizers ❓ Drop all 8, or keep one calm one for full-screen Now Playing?
- YouTube Music / Spotify / Audius streaming ❓ Confirm: own files only?
- TV app (maybe later)

## ❓ Open questions in one place
1. App icon: want 3 options drawn? (§1.8)
2. Time-of-day greeting on Home? (§5.7)
3. Moods from tags + survey, or drop Moods? (§7.6)
4. Double-tap cover to Like? (§8.13)
5. Skip online Apple-style motion covers (no official free source)? (§10.4)
6. Shake to extend the sleep timer? (§14.4)
7. Keep one visualizer, or none?
8. Own files only, no streaming?
