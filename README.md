Raccoon - PC APK Downloader
---------------------------

Raccoon is an APK downloader for fetching apps from Google Play.

* Cross platform (Linux, Windows, Mac OS)
* Avoids the privacy issues that arise from connecting your Android device 
  with a Google account
* Easily install apps on multiple devices without downloading them several
  times.

Building & Running
------------------

Raccoon is built with Gradle (supports Java 11 through Java 21+). Version can be changed via `gradle.properties`.

#### Build standalone fat JAR (macOS / Linux / Windows):
```sh
./gradlew fatJar
```

#### Launch GUI:
```sh
./gradlew run
# or
java -jar build/libs/Raccoon-desktop-4.25.0.jar
```

#### Command-Line Usage (Google Play & Connected ADB Phone):
```sh
# Show all CLI flags
java -jar build/libs/Raccoon-desktop-4.25.0.jar --help

# Configure a Google Play profile (using App Password, aas_et/... master token, or oauth2_4/... token)
java -jar build/libs/Raccoon-desktop-4.25.0.jar --gp-setup "user@gmail.com,app_password_or_token"

# Generate & register a synthetic DummyDroid device & GSF ID (no physical phone required)
java -jar build/libs/Raccoon-desktop-4.25.0.jar --dummydroid
# or from a custom DummyDroid .properties / build.prop file:
java -jar build/libs/Raccoon-desktop-4.25.0.jar --dummydroid mydevice.properties

# Export a DummyDroid .properties profile (from connected phone or built-in preset)
java -jar build/libs/Raccoon-desktop-4.25.0.jar --dummydroid-dump pixel7a.properties

# Download an app from Google Play
java -jar build/libs/Raccoon-desktop-4.25.0.jar --gpa-download com.example.app

# List connected Android devices and their ADB authorization status
java -jar build/libs/Raccoon-desktop-4.25.0.jar --adb-devices

# List user-installed packages on the connected Android phone
java -jar build/libs/Raccoon-desktop-4.25.0.jar --adb-list

# Pull an installed app's APK(s) (base + split APKs) from the connected phone
java -jar build/libs/Raccoon-desktop-4.25.0.jar --adb-pull com.example.app
```

Prebuild binaries are available at

https://raccoon.onyxbits.de

