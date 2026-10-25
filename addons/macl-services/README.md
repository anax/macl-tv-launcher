# MACL Services

A live dashboard for Velana International Airport (VIA / MLE) and Maldives Airports Company Limited
(MACL): Flight Information Display System (FIS / FIDS), 24/7 RTL Airport Bus & Shuttle Schedules, and
accurate astronomical Prayer Times for Male' and Hulhule.

For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md)
so it appears as a branded tile ("MACL Services") with a turquoise glow.

- **Flight Information System (FIS)**: Live Departures and Arrivals for Velana International Airport,
  flight numbers, international airlines (Emirates, Singapore Airlines, Qatar Airways, Etihad, Turkish,
  Maldivian, Trans Maldivian), destinations, scheduled times, gates/belts and color-coded status badges.
- **Airport Bus & Shuttle Network**: Real-time schedules and countdown timers for RTL Route R1 (Male'
  Bridge Express), Route R2 (Hulhumale' Phase 1), Route R3 (Hulhumale' Phase 2), and MACL Airside /
  Seaplane terminal transfers.
- **Prayer Times**: Precise daily prayer times calculated according to the Maldives Ministry of
  Islamic Affairs (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) with remaining countdown to the next prayer.

## Permissions

| Permission | Why |
|---|---|
| `android.permission.INTERNET` | Fetches real-time flight information from the official Velana International Airport portal (https://fis.com.mv/api/flights). Offline fallback included. |

No telemetry, no background service, no location permission, no account needed.

## Build and install

```bash
cd addons/macl-services && ./gradlew assembleRelease
TV_HOST=10.221.216.252 ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/macl-services.apk
TV_HOST=10.221.216.252 ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/macl-services.apk; rm -f /data/local/tmp/macl-services.apk'
```

## Tested on

Hisense SmartTV 4K FFM (songshan, MediaTek), Android TV 11.
