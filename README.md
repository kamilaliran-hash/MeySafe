# MeySafe

<img src="assets/icon.png" width="96" alt="MeySafe logo">

**MeySafe** hides and encrypts your private photos, videos, files and notes (Sgallery-style vault).
Developer: **Kamil** · Version 1.0.0 · Windows (EXE + installer) and Android (APK).

## Features
- PIN lock (4–8 digits), with lockout after repeated wrong attempts
- **AES-256-GCM encryption** of every file, thumbnail, file name and note (master key wrapped by a PBKDF2-SHA256 PIN key)
- Photos & Videos galleries with albums, full-screen viewer, swipe, video player
- Files vault (any file type; previews for images, video, audio, text, PDF on Windows)
- Encrypted private notes with autosave
- **Fake PIN / decoy vault** – a second PIN opens a separate, harmless vault
- **Calculator disguise** – app opens as a working calculator; type PIN and press `=` (on Android the launcher icon also changes to “Calculator”)
- Break-in alerts (log of wrong PIN attempts)
- Offer to delete originals after hiding (Android)
- Unhide (move out) or export a copy – Android saves to Pictures/Movies/Download › MeySafe
- Recycle bin (auto-erase after 30 days)
- Encrypted `.meysafe` backup & restore
- Auto-lock when leaving the app; screenshots and recent-apps previews blocked

## Project layout
| Folder | What |
| --- | --- |
| `www/` | The app itself (one HTML file, shared by both platforms) |
| `desktop/` | Windows shell (Electron + electron-builder → portable EXE and NSIS installer) |
| `android/` | Android shell (WebView, Java) → APK |
| `assets/` | Logo (MS + lock), Windows .ico, calculator disguise icon |
| `.github/workflows/build.yml` | Builds APK, EXE and installer and publishes them as a GitHub Release |

## Building
Push to `main` (or run the workflow manually). Downloads appear under **Releases**:
`MeySafe-1.0.0.apk`, `MeySafe-Setup-1.0.0.exe`, `MeySafe-1.0.0-portable.exe`.

Local builds:
```bash
# Windows
cp -r www desktop/www && mkdir -p desktop/build && cp assets/icon.ico desktop/build/
cd desktop && npm install && npm run dist
# Android (needs Android SDK)
cd android && gradle assembleRelease
```

## Notes
- There is **no PIN recovery**. Make backups from Settings › Back up vault.
- The APK is signed with `android/app/meysafe-release.jks` (password `meysafe2026`). Keep the same key so updates install over older versions; for public distribution, replace it and set `MEYSAFE_STORE_PASSWORD` / `MEYSAFE_KEY_PASSWORD`.
