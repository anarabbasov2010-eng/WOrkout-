# Stable and Beta releases

## Channels
- Tags `stable-vX.Y.Z` publish a Stable release.
- Tags `beta-vX.Y.Z` publish a pre-release Beta build.
- Each release includes the APK and a JSON update manifest with the version, SHA-256 checksum, channel, APK URL and signer-certificate SHA-256 pin.

## GitHub secrets required for signed release
- `ANDROID_KEYSTORE_BASE64`: base64-encoded Android release keystore.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Use a repository-owned signing key. Keep it offline where practical; never commit it to the repository or logs. If these secrets are absent, the tagged release job is expected to fail rather than silently publish an unsigned production APK.

## Update verification
The client verifier checks that the version is newer, the URL uses HTTPS, the APK SHA-256 matches the manifest, and the APK signer certificate matches a separately configured trusted fingerprint. Android still requires the user to confirm APK installation. Downgrades are not automatic: preserve or export data before any rollback.

## Before first public release
1. Create and back up the release keystore.
2. Add the four secrets above.
3. Confirm the signer-certificate SHA-256 fingerprint from the same keystore.
4. Run tests and inspect the generated manifest.
5. Test installation, update, failed migration and recovery on real supported Android devices.
