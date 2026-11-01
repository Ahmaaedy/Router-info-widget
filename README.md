# Router Battery Widget

An Android home-screen widget that shows the battery level and session data of an **MTN MiFi U10LS**, **(though it can easily be ported to support other routers by finding out the data enpoint and replacing in code)** (ZTE-style `mf93d`) mobile router, without having to log in to the router's admin page every time.

## Background: the Python script

The project started as a small Python script, `logic.py`. The router's web portal only shows its battery as an icon with a few levels, and you have to log in to see it. The script automates that:

1. Fetches a fresh single-use `LD` token from the router.
2. Logs in the same way the portal's JavaScript does: `password = SHA256(SHA256(plaintext) + LD)`, sent to `/goform/goform_set_cmd_process`.
3. Reads the battery percentage, charging state and session data counters from `/goform/goform_get_cmd_process`.
4. Converts the percentage to bars using the portal's own icon thresholds, and prints something like:

```
Battery: 2 of 4 bars (~47%), on battery
Session: Down 12.3 MB  Up 1.2 MB  (2540 s connected)
```

The API details (login flow, hashing, battery fields) came from a read-only recon of the portal's JavaScript.

## From script to widget

The script was then translated to Kotlin and turned into a home-screen widget. The login and fetch logic is the same, now in `RouterApi.kt`. The rest of the app exists to run it on a schedule and display the result.

## Features

- Battery shown as 4 bars plus percentage, with on battery / charging / full state
- Session data download and upload (a small "↓ / ↑" line)
- Material You colors (system wallpaper accent plus surface)
- Refreshes about every 10 minutes (best-effort), or instantly with the refresh button on the widget
- Shows the last known reading and time if the router can't be reached
- No launcher icon: the app only appears in the widget picker
- Router IP and password stored encrypted on the device

## How it works

| File | Role |
|---|---|
| `RouterApi.kt` | Kotlin port of the Python script: LD token, hashed login, reading values |
| `Store.kt` | Encrypted storage for IP and password, plus the last reading |
| `Refresh.kt` | WorkManager chain that refreshes every 10 minutes, and the refresh button action |
| `BatteryWidget.kt` | The Glance widget UI and the bar thresholds |
| `ConfigActivity.kt` | Setup screen shown when the widget is added (also opens when you tap the widget) |

Requests are bound to the phone's Wi-Fi network, so they reach the router even if mobile data is preferred.

## Requirements

- Android 12 or newer (needed for Material You colors)
- Android Studio
- The phone must be connected to the router's Wi-Fi for readings to update

## Build

Open the folder in Android Studio and let Gradle sync, then either:

- Press **Run** (set **Launch** to **Nothing** in the run configuration, because there is no launcher activity), or
- Build an APK from the command line:

```
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
#!/usr/bin/env python3
"""Read the battery level of an MTN MiFi (ZTE-style, mf93d) via its web portal.

## to use logic.py
    pip install requests
    export ROUTER_PASSWORD='your-admin-password'   # or you'll be prompted
    python mtn_battery.py
    python mtn_battery.py --ip 192.168.0.1 --json
    
## Setup

1. Install the app.
2. Long-press the home screen, open **Widgets**, and pick **Router Battery**.
3. Enter the router IP (default `192.168.0.1`) and the admin password, then save.
4. Tap the widget any time to change these settings, and tap the refresh icon to update immediately.

## Widget size

The widget size is set in `app/src/main/res/xml/battery_widget_info.xml` (`targetCellWidth`, `targetCellHeight`, `minWidth`, `minHeight`). After changing it, remove the widget and add it again so the launcher picks up the new size.

## Notes and limitations

- The session data fields (`realtime_rx_bytes`, `realtime_tx_bytes`) are the usual ZTE names and have not been confirmed on this router. If the data line doesn't appear, change the two constants at the top of `RouterApi.kt`.
- The login allows a limited number of wrong attempts (`MAX_LOGIN_COUNT` is 5 on this firmware), so make sure the saved password is correct.
- The 10-minute refresh is best-effort. Android may delay it when the phone is idle or in battery saver.
- The router uses plain HTTP on the local network, so the app allows cleartext traffic.

## Planned

- Sending USSD codes through the router (for example `*323*4#` to check the data balance) and showing the result in the app.
