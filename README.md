# Petal Launcher

A Lawnchair-inspired home screen launcher built with Kotlin, Jetpack Compose, Material 3 and DataStore.
Min SDK 26 · Target SDK 36 · Universal release APK (arm64-v8a, armeabi-v7a, x86, x86_64).

## Shortcuts & Navigation
| Action | Result |
|---|---|
| Swipe left / right | Switch home page |
| Swipe up (default) | Open app drawer |
| Swipe down (default) | Expand notification panel |
| Double tap | Configurable (default: none) |
| Long-press empty space | Workspace options (Settings, Folder, Page, Wallpaper) |
| Long-press any app icon | Pin/Unpin Home, Dock, Hide, App info, Uninstall |
| Tap search bar | Search apps; globe icon = web search; gear icon = settings |
| Tap clock / date / note | Alarms / Calendar / edit reminder |
| Home button | Closes overlays, returns to first page |
| Deep links | `petal://settings`, `petal://drawer` |

## How to use
1. Install the APK and press Home → choose **Petal Launcher** → **Always**.
2. Long-press an app → **Pin to Home** / **Pin to Dock** (max 4 dock apps).
3. Long-press empty space → **Create folder**; open it to rename, add/remove apps or delete it.
4. Open the drawer, type to search, or use category chips (Flowerpot auto-categories).
5. Settings → Appearance for theme, Material You, backdrop and icon shape.
6. Settings → Backup & restore to export/import `.json` or reset.

## Flowerpot rules
Add your own rules in `app/src/main/assets/flowerpot/*.txt` using `Category:package.prefix`.

## Build (GitHub Actions)
Push to `main`; download the APK from the workflow **Artifacts**.
For stable signing add these repository secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Create a keystore (Windows PowerShell):

    keytool -genkeypair -keystore release.jks -alias petal -keyalg RSA -keysize 2048 -validity 10000
    [Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard

Versioning: `versionCode = run number`, `versionName = 1.<run number>`.

## New Features / Changelog
### v1.1
- Initial release: multi-page workspace, folders, At a Glance, dock & QSB, app drawer with Flowerpot categories,
  Material 3 theming, icon shapes, gestures, backup/restore, About & licenses.
