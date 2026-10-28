# WOrkout implementation status

This file distinguishes code present in the repository from production readiness.

## Present in this starter implementation
- Kotlin/Compose app shell with fixed bottom navigation and premium dark/lime design.
- Five Android resource locales: Russian, English, Azerbaijani, Turkish and Spanish.
- CameraX preview and on-device ML Kit pose detection for a small supported movement set.
- Rep-count hysteresis with confidence gating and manual fallback.
- Procedural OpenGL ES 2.0 3D human-like scene with rotation, muscle/skeleton display layers and target-group highlight.
- Nutrition catalog, sample recipes, macro summaries, allergy filtering, ingredient substitution primitive and calorie-target estimate guardrails.
- Android Keystore AES-GCM local-store primitive.
- Password-based AES-GCM portable backup format with authenticated encryption and version header.
- Direct local-network transfer primitive for an already-encrypted backup, with pairing code, size limits and SHA-256 integrity check.
- Media3 local audio player screen and MediaSessionService entry point.
- APK digest and signer-certificate checks in the update verifier.
- GitHub Actions unit tests/debug APK build and Stable/Beta signed-release job.

## Not production-complete yet
- The anatomical scene is procedural and stylized. It is not a medically accurate anatomy asset. A high-quality GLB must be sourced or commissioned under a suitable license, checked for biomechanical correctness, added as an on-demand content pack and tested on devices.
- Pose rep counting is heuristic and has not been validated across body types, camera angles, clothing, lighting or device hardware. It does not diagnose form or guarantee injury prevention.
- The nutrition catalog is a small demonstration dataset. It needs a substantially larger verified food database, recipe moderation, region-specific units/prices and a real pantry/meal-plan UI before nutrition recommendations should be relied on.
- The transfer primitives need a user flow for pairing, IP discovery, permissions and confirm-before-import. The current receiver only accepts a user-supplied port and code.
- The local encrypted store and portable backup are primitives, not a complete migration/recovery workflow. Key-loss recovery and interrupted-transfer tests are required.
- The music screen plays selected local audio. Adaptive ducking, volume mixer, external-player control and licensed built-in music packs remain future work.
- The signed-release workflow needs all four repository secrets for a release build. Signing key must be created and kept by the repository owner, never committed. Stable/Beta manifest delivery/update UX and rollback strategy still require end-to-end testing.
- No physical-device testing has been performed by this automated setup.

## Build validation
GitHub Actions is the canonical build environment for this repo. A green CI run confirms the configured build and unit tests succeeded on the runner; it does not replace physical Android testing.
