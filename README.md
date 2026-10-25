# VaultFlow

VaultFlow is an offline-first Android app for storing and viewing personal documents. Documents are encrypted on the device and are not uploaded to a server.

**Author:** Almaz Lashin (Алмаз Лашин)

## Features

- Import PDF, JPEG, and PNG files up to 50 MiB.
- Encrypt documents with AES-GCM and a key held in Android Keystore.
- Search by title, category, or tag; sort documents and mark favorites.
- View PDFs and images inside the app.
- Set optional expiration reminders for 30, 7, or 1 day before expiry.
- Export or share a document only after confirmation.
- Choose English or Russian in Settings; the choice is saved across restarts.

## Technology

Kotlin, Jetpack Compose, Material 3, Room, Hilt, DataStore, WorkManager, and Android Keystore.

The app uses MVVM and a repository. Room stores document metadata; encrypted files stay in the app's private, no-backup storage.

## Security

- No account, server, analytics, or internet permission.
- Document titles, categories, dates, and tags are stored locally without app-level encryption.
- The optional biometric/device lock protects the app screen; it does not gate each encryption operation.
- Viewing and sharing use temporary plaintext files in private cache. Copies exported to other apps are outside VaultFlow's control.
- Backups are disabled. There is no key recovery, so losing app data or the Keystore key can make documents unrecoverable.
- Android may delay expiration reminders. Notifications require permission on Android 13 and later.

## Build

Requirements: Android SDK Platform 37 and JDK 17 or newer.

```bash
./gradlew assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

```bash
./gradlew lintDebug testDebugUnitTest assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest # requires an emulator or connected Android device
```

Unit tests cover encryption, validation, and reminder timing. Android tests cover Room operations, a synthetic PDF import and repository flow, and an onboarding/navigation scenario.

## Known limitations

- Categories are built in and cannot be edited.
- Biometric prompts, notification permission flows, and sharing to other apps are not covered by automated tests.
- The repository has no selected license. All rights are reserved unless the owner adds a license.

Minimum Android version: Android 8.0 (API 26). The project compiles and targets SDK 37.
