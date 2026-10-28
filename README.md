# WOrkout

A privacy-first Android fitness app built with Kotlin and Jetpack Compose. The visual direction is **Dark Luxury Sport**: near-black surfaces, graphite cards and electric-lime highlights.

## Build locally
- JDK 17
- Android SDK Platform 36
- Gradle is provided by GitHub Actions; use `./gradlew` after adding the Gradle wrapper.
- Run `gradle testDebugUnitTest assembleDebug`.

## Product modules
- Personal workout planner, timer and manual rep controls.
- On-device CameraX + ML Kit pose estimation with heuristic rep counting for supported movements.
- Interactive procedural 3D anatomy preview (a stylized educational visualization, not a medically validated anatomical model).
- Nutrition data model with recipes, macro totals, allergy filters, substitutions, pantry and shopping-list primitives.
- Media3-based local audio playback.
- AES-GCM encrypted local data and password-protected encrypted backup export/import primitives.
- Direct local-network transfer primitives; no personal fitness data needs a cloud account.
- Stable/Beta release channels and APK digest/signature verification hooks.

## Privacy and safety
Camera frames are processed on-device and are not recorded or uploaded. Pose accuracy depends on visibility, camera angle and lighting; an estimate must never be treated as medical advice. Pain, dizziness or feeling unwell are reasons to stop or reduce activity. The app does not use accounts or cloud sync for personal fitness records. Internet is used for content and update checks.

## Signed releases
Create these repository secrets only when ready to sign:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never commit a keystore or passwords. The unsigned debug build can be produced without these secrets; a production signed release cannot.

## Current status
This repository is the working implementation base, not a claim that every item in the product plan is production-complete. The 3D anatomy scene is procedural/stylized until a properly licensed, biomechanically reviewed anatomical model is added. Camera inference, local transfer, signing and update flows need real-device/release verification.
