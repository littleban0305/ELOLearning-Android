# ELOBrowser

ELOBrowser is an Android offline reader for local Kiwix ZIM archives. It opens a document through Android's file picker and retains access to that file instead of copying a potentially huge archive into app storage.

It also includes a basic online browser for HTTP/HTTPS pages; online pages require a network connection.

## First version

- Import a `.zim` file through Android's document picker.
- Search article titles using the ZIM title index.
- Search article contents using the ZIM full-text index when the archive provides one.
- Read articles and their embedded resources offline.
- Open regular websites in the built-in WebView.

If `Archive.hasFulltextIndex()` is false, full-text search is unavailable for that particular archive; title search remains available.

## Build

Open this folder in Android Studio with a Java 17 Gradle JDK and Android SDK Platform 35 installed, then build the `app` debug variant. The project uses the official `org.kiwix:libkiwix:2.6.0` Android library.

The current Termux setup script does not install Android Studio or the Android SDK, so it cannot build an APK on its own. The source can be edited in code-server; APK assembly needs an Android SDK/Gradle environment.
