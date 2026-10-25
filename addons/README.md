# MACL TV Add-ons

Pre-built companion apps tailored for the MACL TV living room & lounge experience.

| Add-on | Package | Description |
|---|---|---|
| [`macl-services`](macl-services/) | `com.macl.services` | Flight Information Display System (FIS live board & `fis.com.mv` web view), Prayer times calculator with adhan timings, and RTL Airport Bus schedules. |
| [`tv-agent`](tv-agent/) | `com.macl.tvagent` | AI TV voice and remote control assistant for managing TV settings, querying flights, and airport announcements. |

Each add-on is its own standalone Android Gradle project and can be built independently or deployed together using the root setup script:

```bash
TV_HOST=100.83.39.24 ./setup.sh
```
