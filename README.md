# Clean TV launcher

English launcher in the ATV Launcher Pro style: weather widget, status bar, hexagon background, banner tiles.

## Build
1. Install Android Studio. Open this folder (CleanTV). Let Gradle sync.
2. Build > Build APK(s). APK: app/build/outputs/apk/debug/app-debug.apk

## Install on the TV
1. TV: Settings > Device Preferences > About > tap Build 7 times. Then Developer options > enable USB/network debugging.
2. PC: adb connect TV_IP:5555
3. adb install -r app-debug.apk
4. Press Home and choose Clean TV as the default. If there is no chooser, disable the stock launcher (see debloat/packages.txt) after confirming Clean TV opens.

## Remote keys
D-pad moves focus. OK opens an app. Hold OK on a tile: open, app info, uninstall, remove from home. Menu or Settings key opens launcher settings. Back does nothing on the home screen.

## Settings (gear icon or Menu key)
Status bar toggles, Manage apps (choose what shows), Weather city, System settings.

## Removing bloatware and ads
The launcher has no ads and hides every app you do not pick. Apps keep running in the background, though. To remove system bloat for your user, put package names in debloat/packages.txt and run debloat.bat (or debloat.sh). Undo with restore.bat. For ads inside apps, set the router DNS to an ad-blocking DNS such as AdGuard DNS.
