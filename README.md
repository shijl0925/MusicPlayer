# MusicPlayer

A native Android offline music player for local audio files.

## Features

- Scans local music from Android MediaStore after audio permission is granted.
- Album grid inspired by the reference UI, with a floating mini player.
- Playlist-style song list for all local tracks.
- Full playback screen with artwork, seek bar, play/pause, previous, and next controls.
- GitHub Actions workflow builds and uploads a debug APK artifact, and attaches APKs to GitHub releases.

## Build locally

```bash
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
