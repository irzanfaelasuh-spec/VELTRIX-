# Signing key and release setup

A signed APK must keep the same signing identity across every update. The release workflow reads one persistent keystore from GitHub Actions secrets.

Add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `VELTRIX_KEYSTORE_BASE64` | Base64 text of your `.jks` keystore file |
| `VELTRIX_KEYSTORE_PASSWORD` | Keystore password |
| `VELTRIX_KEY_ALIAS` | Key alias |
| `VELTRIX_KEY_PASSWORD` | Key password |

Do not commit the keystore or passwords into the repository. Keep a private backup of the keystore and its passwords. Losing them means you cannot publish an installable update over the existing app with the same application ID.

To publish a release, update `versionName`/`versionCode` through tag and workflow inputs, commit, then push a tag such as `v1.0.1`. GitHub Actions builds the signed APK and attaches it to the GitHub Release.

The app's About screen can then read the tag, notes, publication timestamp and `.apk` release asset from the public GitHub API.
