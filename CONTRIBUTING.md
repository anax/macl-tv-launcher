# Contributing to MACL TV Launcher

Thank you for contributing to the MACL TV Launcher project!

## Where Does Your Idea Go?

| What you want to do | Where it goes |
|---|---|
| A new airport theme (colors, wallpaper, dock configuration) | `themes/<theme-name>/` |
| A new widget or service tile | `addons/<service-name>/` as an isolated app module |
| Launcher bug fix or core enhancement | `app/src/main/` |
| Documentation or translation | `README.md` or `app/src/main/res/values-<lang>/` |

## Rules for Code Contributions

1. **Keep the Core Launcher Lean**:
   - Zero network permissions (`android.permission.INTERNET`) in `app/`.
   - Release APK size must remain under **480 KB (491,520 bytes)**.
   - Verified on a real TV screen (10-foot distance, D-pad navigation).

2. **Themes**:
   - Each theme folder in `themes/<name>/` must have a valid `config.json` and a `screenshot.jpg` (1280x720, under 300 KB).
   - High visual contrast: text must remain crisp and readable over wallpapers.

3. **No Prebuilt APKs**:
   - Never commit prebuilt `.apk` or `.aab` binaries into the repository.
   - Run `rm -rf app/build` before committing.
