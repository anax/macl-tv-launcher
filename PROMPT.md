# The Prompt

Paste this into an AI coding assistant with shell access on a computer on the same Wi-Fi (or Tailscale network) as the TV. Works on Android TV and Google TV (Hisense, Xiaomi, TCL, Sony, Chromecast, Nvidia Shield, and others).

---

```markdown
I want to install MACL TV Launcher and replace my TV's stock home screen with the tvOS-inspired MACL living room experience.
My TV IP is: <TV_IP_OR_TAILSCALE_IP_HERE>.

Please execute the following procedure step-by-step:

1. Connect:
   - Connect via ADB: `adb connect <TV_IP>:5555`.
   - Verify connection with `adb -s <TV_IP>:5555 get-state`.

2. Pre-flight Check:
   - Verify TV SDK version (`getprop ro.build.version.sdk`) is at least 26.
   - Query current stock launcher (`cmd package resolve-activity ...`).

3. Backup:
   - Backup package lists (`pm list packages -u` and `-d`) and `getprop` to `backup/<timestamp>/`.

4. Build & Install:
   - Run `./setup.sh <TV_IP>` to build release APKs and install MACL TV Launcher (`com.macl.tvlauncher`), MACL Services (`com.macl.services`), and TV Agent (`com.macl.tvagent`).
   - Push the default frosted glass theme and wallpaper.

5. Verification:
   - Verify that `com.macl.tvlauncher` starts with `am start -W`.
   - Set as default home activity: `cmd package set-home-activity --user 0 com.macl.tvlauncher/.MainActivity`.
   - Take a screenshot with `adb exec-out screencap -p > shots/after.png` and verify that the living room widgets (FIS, Prayer Times, Airport Bus) and floating dock appear properly.
```
