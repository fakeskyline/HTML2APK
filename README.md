# HTML2APK

Android utility to package a standalone HTML page into a WebView Android project.

Current build: imports HTML and an icon, configures app name, package ID and min API, and exports a Gradle project ZIP. This version does not yet create a signed APK directly on-device.

SDK tools cannot simply be bundled and executed inside a normal Android app sandbox; the toolchain is large and Android restricts executable files in app-private storage. The exported project is buildable in AndroidIDE/Android Studio or CI.
