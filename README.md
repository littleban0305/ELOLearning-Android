# ELOLearning

ELOLearning is an Android AI learning assistant with locally saved study notes and practice quizzes. It uses the Gemini API configured in the app, and keeps notes on the device.

The app also retains ELOBrowser features: search and read local Kiwix ZIM archives, connect to a Kiwix library server, and browse regular HTTP/HTTPS pages.

## Learning features

- Ask AI to explain a topic for a selected grade and subject.
- Save generated study notes on the device.
- Generate a five-question multiple-choice quiz from saved notes and grade it locally.
- Configure the Gemini API key in the app; the key is stored using Android Keystore encryption.

## Library and browser features

- Search article titles using the ZIM title index.
- Search article contents using the ZIM full-text index when the archive provides one.
- Read articles and their embedded resources offline.
- Open regular websites in the built-in WebView.

If `Archive.hasFulltextIndex()` is false, full-text search is unavailable for that particular archive; title search remains available.

## Build

Open this folder in Android Studio with a Java 17 Gradle JDK and Android SDK Platform 35 installed, then build the `app` debug variant. The project uses the official `org.kiwix:libkiwix:2.6.0` Android library.

The GitHub Actions workflow builds `app-debug.apk` and uploads it as the `ELOLearning-debug-apk` artifact. To download it, open the repository's **Actions** tab, select a successful **Build Android APK** run, then download **ELOLearning-debug-apk** from its Artifacts section. The artifact is retained for 14 days.

The current Termux setup script does not install Android Studio or the Android SDK, so it cannot build an APK on its own. The source can be edited in code-server; APK assembly needs an Android SDK/Gradle environment.
