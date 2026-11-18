# Deploy VELTRIX from an Android phone

This project is designed to build on GitHub Actions. You do not need Android Studio or a paid build server.

## 1. Upload the source from Termux

1. Download and extract `VELTRIX-source.zip` into a folder such as `~/VELTRIX`.
2. Create an empty repository on GitHub. The default branch should be `main`.
3. In Termux, run the commands below from inside the extracted project directory. Replace the URL with your repository URL.

```sh
cd ~/VELTRIX
git init -b main
git add .
git commit -m "Initial VELTRIX Android project"
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

GitHub does not accept your account password for Git pushes. When Git prompts for a password, use a fine-grained Personal Access Token restricted to this repository with **Contents: Read and write**. Do not paste the token into a source file, script, chat, or public repository.

## 2. Get a test APK

1. Open the repository on GitHub and select **Actions**.
2. Enable Actions if GitHub asks.
3. Open the **VELTRIX Android CI** workflow and wait for the run to finish.
4. Open the successful run and download the `VELTRIX-debug-apk` artifact.
5. Extract the artifact and install `app-debug.apk`.

The debug build has package suffix `.debug`. It can be installed alongside a signed release, but it is not the package that receives in-place production updates.

## 3. Configure signed releases and the in-app update center

1. Keep the separate private signing bundle in a safe place. Never upload the `.jks` file to GitHub source control.
2. Open the private `GITHUB_ACTIONS_SECRETS.txt` file and add its four values under repository **Settings → Secrets and variables → Actions** using the exact names shown in that file.
3. In VELTRIX, open **Settings → Update Center** and enter `USERNAME/REPOSITORY`.
4. To publish a release, commit your changes and push a new version tag, for example:

```sh
git tag v1.0.1
git push origin v1.0.1
```

The `VELTRIX Signed Release` workflow builds a signed APK and attaches it to the GitHub Release. The About page reads that release's version, notes, published time, APK asset and size.

Keep the same signing key for every future release. Android will reject an update if the signing identity changes. Back up the private key and passwords before deleting or changing devices.

## 4. First-run permissions

- Enable **Display over other apps** for the Island overlay.
- Enable **Notification access** so Android can expose supported media sessions and notification previews.
- Allow VELTRIX notifications for prayer reminders.
- Allow location only if you want the prayer schedule to use the device's last-known coordinates; manual coordinates are also supported.
- Allow exact alarms if available and desired. Without that access, Android may delay reminders.
- Select an adzan audio file in Settings. VELTRIX does not bundle a recording; it plays the file you choose.

## Build status and real capability limits

A successful GitHub Actions run is the first full Android compile check. This local source package was statically inspected, but it was not compiled in this environment because Android SDK and Gradle are unavailable here.

The initial implementation includes the control center, icon, Island master switch, module switchboard, media-session controls, basic notification previews, battery/charging state, LRC import and line sync, prayer timetable and reminders, and GitHub release checking. Other module switches are present but are marked as provider/extension/platform-limited until their individual engines and integrations are completed.
