# MYNA

A music player for Android with a bold Bento look: lime and ink tiles, big type, and no clutter.
It plays songs from the web and from your phone, learns what you like, and gives you a fresh mix every day.

[![Download](https://img.shields.io/github/v/release/suryablaze02-byte/MYNA?style=for-the-badge&label=Download&labelColor=111111&color=C6F432)](https://github.com/suryablaze02-byte/MYNA/releases/latest)
[![Support on Ko-fi](https://img.shields.io/badge/Support-Ko--fi-C6F432?style=for-the-badge&labelColor=111111&logo=kofi&logoColor=white)](#support)

<p>
  <img src="docs/screens/home.png" width="24%" alt="Home">
  <img src="docs/screens/discover.png" width="24%" alt="Discover">
  <img src="docs/screens/now-playing.png" width="24%" alt="Now Playing">
  <img src="docs/screens/search.png" width="24%" alt="Search">
</p>
<p>
  <img src="docs/screens/playlist.png" width="24%" alt="Liked songs">
  <img src="docs/screens/sound.png" width="24%" alt="Sound settings">
</p>

**On a tablet**, MYNA switches to a sidebar and a full-width player bar:

<img src="docs/screens/tablet-home.png" width="100%" alt="MYNA on a tablet">

## What it does

- **Home:** jump back in, your listening time this week, songs on repeat and recently played.
- **Discover:** "Your mix" and "Because you play…" shelves, built from what you play.
- **Search:** songs, albums and artists. A film name such as *Kabir Singh* finds the film's soundtrack.
- **Now Playing:** crossfade, synced lyrics, queue, a sleep timer and the output device.
- **Sound:** spaces (Studio, Stage, Big Hall, Headset), an equaliser and Clear Bass. The spaces run inside MYNA's own player, so they work on any phone and any speaker.
- **Lock screen:** media controls in the notification. Tap the art and it fills the lock screen without unlocking the phone.
- **Library:** liked songs, playlists, songs on your phone, and online songs you add with ⋯ › Add to library.
- **Updates itself:** MYNA checks GitHub once a day. When a new version is out, a lime banner on Home installs it in one tap.

## Install

Download the APK from [Releases](../../releases) and open it on your phone.
Android asks once to allow installs from your browser or file app.

Needs Android 8.0 or newer. After that, MYNA updates itself.

## Build

Built with Kotlin Multiplatform and Compose Multiplatform, using Media3 ExoPlayer for playback.

```sh
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

| Folder | What's in it |
|---|---|
| `composeApp/src/commonMain` | Shared UI, the player engine, search and recommendations |
| `composeApp/src/androidMain` | Android playback, media controls, the lock screen player and sound effects |
| `design/` | Mockups and design notes |

<div align="center">

<h2 id="support">Support</h2>

MYNA is free and always will be. If it has kept you company, you can chip in on Ko-fi from anywhere, or over UPI in India:

<br/>

<table>
  <tr>
    <td align="center" width="50%">
      <b>Ko-fi</b> · worldwide<br/><br/>
      <a href="https://ko-fi.com/suryavardhan1"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support MYNA on Ko-fi" height="40" /></a><br/><br/>
      <sub><a href="https://ko-fi.com/suryavardhan1">ko-fi.com/suryavardhan1</a></sub>
    </td>
    <td align="center" width="50%">
      <b>UPI</b> · India<br/><br/>
      <img src="docs/upi_support.png" alt="UPI QR code to support MYNA: mochi.vardhan@federal (Surya Vardhan)" width="220" /><br/>
      <sub>UPI ID: <code>mochi.vardhan@federal</code> · Surya Vardhan</sub>
    </td>
  </tr>
</table>

</div>
