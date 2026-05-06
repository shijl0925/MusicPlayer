# MusicPlayer

A native Android offline music player for local audio files.

## Features

- Scans local music from Android MediaStore after audio permission is granted.
- Album grid inspired by the reference UI, with a floating mini player.
- Playlist-style song list for all local tracks.
- Full playback screen with artwork, seek bar, play/pause, previous, and next controls.
- GitHub Actions workflow builds downloadable debug APK artifacts for pull requests, and attaches APKs to GitHub releases.

## Build locally

```bash
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Pull request APK download

For every pull request, the **Android APK** workflow builds a debug APK and uploads it as a workflow artifact named `music-player-pr-<PR number>-debug-apk`. Open the workflow run in a browser, download the artifact from **Artifacts**, unzip it, and install the APK on your Android phone for verification.
