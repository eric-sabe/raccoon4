# Raccoon 4 (Modernized Research Fork)

**Raccoon** is a cross-platform desktop client and command-line tool for researching, querying, and downloading Android applications (base APKs, split APKs, and OBB expansion files) directly from Google Play, as well as extracting installed apps over ADB.

> [!NOTE]
> This repository ([`eric-sabe/raccoon4`](https://github.com/eric-sabe/raccoon4)) is a modernized, self-contained fork of the unmaintained upstream `onyxbits/raccoon4` project. It has been updated to run on **Java 21 + Gradle 9.7**, speak modern **TLS 1.2 (ECDSA + SNI)** to Google Play servers, authenticate with modern **OAuth2 (`ya29.*`) & Master (`aas_et/*`) tokens**, parse **Android 14/15 (SDK 30–35) APKs**, and includes **built-in DummyDroid synthetic device registration** so no physical phone is required.

See **[`docs/MODERNIZATION.md`](docs/MODERNIZATION.md)** for a complete architectural and commit-by-commit breakdown of all changes made in this fork.

---

## Key Capabilities

* **100% Phone-Free Operation (Built-in DummyDroid)**: Generates synthetic Android 15 (Google Pixel 7a) hardware fingerprints and registers fresh Google Services Framework IDs (`gsfId`) directly against `https://android.clients.google.com/checkin`.
* **Google Play CLI & GUI**: Search apps, inspect full protobuf metadata (`details` & `bulkDetails`), download specific version codes or latest releases (including all split APKs `config.*`), and batch-update local repositories.
* **Automatic OAuth2 Token Refresh**: Uses long-lived Google App Passwords or master tokens (`aas_et/*`) to automatically mint and refresh 1-hour `ya29.*` Google Play Bearer tokens whenever they expire.
* **Android 10–15 ADB Integration**: Inspect connected phones, list installed packages, pull full split-APK bundles (`base.apk` + `split_config.*.apk`), or bootstrap credentials from a rooted device.

---

## Requirements & Building

* **JDK**: Java 11 through Java 21+ (tested with OpenJDK 21)
* **Build System**: Gradle 9.7.1 (bundled via `./gradlew`)

```bash
# Build the self-contained executable fat JAR
./gradlew fatJar

# Run the test suite (including live DummyDroid synthetic GSF checkin test)
./gradlew test
```

The standalone JAR is written to:
```text
build/libs/Raccoon-desktop-4.25.0.jar
```

---

## Quickstart 1: 100% Phone-Free Setup (Recommended)

You do **not** need a physical Android phone to use Raccoon.

### Step 1: Create a Google App Password
1. Enable 2-Step Verification on your Google account.
2. Visit **<https://myaccount.google.com/apppasswords>** and generate a 16-character App Password.

### Step 2: Initialize Your Raccoon Profile
Run `--gp-setup` with your email and 16-character App Password (with no phone plugged in):

```bash
java -jar build/libs/Raccoon-desktop-4.25.0.jar --gp-setup "your.account@gmail.com,xxxx xxxx xxxx xxxx"
```

What happens automatically:
1. Raccoon exchanges your App Password at `https://android.clients.google.com/auth` for a non-hardware-bound `aas_et/...` master token and a `ya29.*` Google Play OAuth2 Bearer token.
2. Using the built-in **DummyDroid** Pixel 7a (Android 15) preset, Raccoon performs a synthetic device checkin at `https://android.clients.google.com/checkin`, obtains a new `gsfId`, binds it to your account, and saves the profile in `~/Raccoon`.
3. Whenever a 1-hour `ya29.*` access token expires in the future, Raccoon automatically refreshes it in the background using the stored credential.

### Step 3: Search, Inspect, and Download APKs

```bash
# 1. Search Google Play
java -jar build/libs/Raccoon-desktop-4.25.0.jar --gpa-search "vlc" | grep -E "docid:|title:" | head -n 10

# 2. Fetch full package metadata (versionCode, splits, dependencies, permissions)
java -jar build/libs/Raccoon-desktop-4.25.0.jar --gpa-details org.videolan.vlc

# 3. Download latest version (base APK + all split APKs) into ~/Raccoon/content/apps/
java -jar build/libs/Raccoon-desktop-4.25.0.jar --gpa-download org.videolan.vlc

# 4. Download a specific versionCode (e.g. 2016011907) to a custom directory
java -jar build/libs/Raccoon-desktop-4.25.0.jar --gpa-download org.mozilla.firefox,2016011907,1 -f /tmp/apks
```

---

## Quickstart 2: Using Custom DummyDroid Device Profiles

If an app targets specific device properties (e.g., tablet vs. phone, specific ABI, or carrier), you can generate or supply a custom DummyDroid `.properties` / `build.prop` file:

```bash
# Export the built-in Pixel 7a preset (or a connected phone's properties) to a file
java -jar build/libs/Raccoon-desktop-4.25.0.jar --dummydroid-dump custom-device.properties

# Edit custom-device.properties as needed, then register a new synthetic GSF ID from it
java -jar build/libs/Raccoon-desktop-4.25.0.jar --dummydroid custom-device.properties
```

---

## Quickstart 3: Connected Android Phone (ADB)

If you have an Android device connected via USB with **USB Debugging** enabled:

```bash
# Verify ADB connection and authorization status
java -jar build/libs/Raccoon-desktop-4.25.0.jar --adb-devices

# List third-party packages installed on the phone
java -jar build/libs/Raccoon-desktop-4.25.0.jar --adb-list

# Pull an installed app (base.apk + all split_config.*.apk files) into ~/Raccoon/content/apps/
java -jar build/libs/Raccoon-desktop-4.25.0.jar --adb-pull com.example.app
```

> [!IMPORTANT]
> **Note on Rooted Phone Token Import (`--gp-setup` without password):**
> Running `--gp-setup` with no arguments while a rooted phone is connected pulls the phone's active `ya29.*` OAuth2 access token from `/data/system_ce/0/accounts_ce.db`. Because Android 15 hardware-binds the phone's internal `aas_et/` master token to the phone's Titan M2 keystore chip, a phone-pulled `ya29.*` token is only valid for **1 hour** once the phone is unplugged. For permanent phone-free use, pass a 16-character Google App Password to `--gp-setup "email,app_password"`.

---

## Complete CLI Reference

| Option | Argument | Description |
| :--- | :--- | :--- |
| `--help` / `-h` | *(none)* | Print all available command-line options. |
| `--gp-setup` | `[email,password_or_token]` | Create or update the Google Play profile in `~/Raccoon`. Accepts a 16-char Google App Password, an `aas_et/...` master token, or an `oauth2_4/...` token. |
| `--dummydroid` | `[file.properties]` | Register a new synthetic DummyDroid device & GSF ID (uses built-in Pixel 7a Android 15 preset if no file is given) and update the active profile. |
| `--dummydroid-dump` | `[output.properties]` | Export a DummyDroid `.properties` device profile (live from a connected ADB phone if present, otherwise the built-in Pixel 7a preset). |
| `--gpa-search` / `-s` | `<query>` | Search Google Play and print raw protobuf search results to stdout. |
| `--gpa-details` / `-d` | `<package_or_file>` | Fetch full `DetailsResponse` protobuf for a package name (or a file containing one package name per line). |
| `--gpa-bulk-details` / `-b` | `<file>` | Fetch bulk details for a newline-delimited file of package names, writing one output file per package in the same directory. |
| `--gpa-download` / `-a` | `<pkg[,versionCode,offerType]>` | Download an app from Google Play. Omit `versionCode` and `offerType` to automatically fetch the latest version. |
| `--gpa-update` / `-u` | *(none)* | Check all apps in the local Raccoon repository for newer versions on Google Play and download updates. |
| `--paid` / `-f` | `[directory]` | When combined with `--gpa-download`, treat the app as already acquired (skips `purchase` call) and optionally save APKs to `directory` instead of `~/Raccoon`. |
| `--adb-devices` | *(none)* | List connected Android devices and their ADB status. |
| `--adb-list` | *(none)* | List all user-installed packages (`packageName=path`) on the connected phone. |
| `--adb-pull` | `<packageName>` | Pull all APK files (`base.apk` + split APKs) for `<packageName>` from the connected phone into `~/Raccoon/content/apps/`. |

### System Properties

You can override the default Raccoon storage directory (`~/Raccoon`) or active profile alias via JVM flags:

```bash
java -Draccoon.home=/path/to/custom/repo -Draccoon.playprofile=myprofile -jar build/libs/Raccoon-desktop-4.25.0.jar ...
```

---

## Local Repository Layout (`~/Raccoon`)

```text
~/Raccoon/
└── content/
    ├── apps/
    │   └── <package.name>/
    │       ├── <package.name>-<versionCode>.apk      # Base APK
    │       ├── config.arm64_v8a-<versionCode>.apk    # Native ABI split APK (if present)
    │       ├── config.xxhdpi-<versionCode>.apk       # Density split APK (if present)
    │       └── appicon-<versionCode>.png             # Extracted launcher icon
    └── database/
        ├── raccoon.properties                        # Embedded HSQLDB metadata
        └── raccoon.script                            # Profiles, app index, and variables
```

---

## Troubleshooting

### `Connection error: Error retrieving information from server. DF-DFERH-01` (HTTP 401)
* **Cause**: Google Play's FDFE endpoint returns `HTTP 401 Unauthorized` wrapped in a protobuf `DF-DFERH-01` message when the active `ya29.*` OAuth2 access token has expired (1-hour TTL) and Raccoon only has a short-lived phone token rather than a long-lived credential.
* **Fix**: Run `--gp-setup "your.account@gmail.com,<16_char_app_password>"` once. Raccoon will store a non-hardware-bound master credential and automatically mint new `ya29.*` tokens whenever needed.

### Installing Downloaded Split APKs (`config.*.apk`) to a Device
Modern Android apps on Google Play are delivered as split APKs (`<pkg>-<vc>.apk` plus one or more `config.*-<vc>.apk` files). To install a split APK bundle via `adb`:

```bash
adb install-multiple ~/Raccoon/content/apps/org.videolan.vlc/*-13070108.apk
```

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
