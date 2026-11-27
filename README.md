# HTML2APK

A native Android app that converts one standalone HTML file into a signed APK entirely on-device.

## Features
- Choose a local HTML document.
- Set app label and Android package ID.
- Pick a launcher icon.
- Set minimum SDK from API 26 through 36.
- Build and sign APK locally with a key stored in Android Keystore.
- Save, share, or invoke Android's package installer.

## Build this app
Requires JDK 17 and Android SDK API 36. Open the project in Android Studio/AndroidIDE or run Gradle build tasks. GitHub Actions builds a debug APK artifact.

## How it works
HTML2APK builds a WebView template as part of its build, then patches the template APK (manifest strings, package resource table, HTML asset, optional icon), aligns ZIP entries and signs it using `com.android.tools.build:apksig`. The signing key is generated on the device and stored in Android Keystore. It never uploads the HTML or signing key.

The generated apps use Android System WebView and load `file:///android_asset/www/index.html`. This tool currently accepts one HTML file; external CSS, JS, images, and font files must be embedded in the HTML.

## Third-party code
The APK patching utilities are adapted from [html-to-apk](https://github.com/mrnomaguow/html-to-apk), Copyright 2026 Elijah Wougamon, Apache License 2.0. See LICENSE-APACHE-2.0 and NOTICE.

## License
HTML2APK app code is licensed under MIT.
