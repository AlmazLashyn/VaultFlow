# Contributing

Thanks for considering a contribution. Keep changes focused and preserve the offline-first design.

## Before submitting

1. Use JDK 17 and Android SDK 37.
2. Run `./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest`.
3. Run `./gradlew connectedDebugAndroidTest` on an Android emulator or connected device.
4. Add or update tests for changes to validation, persistence, or cryptography.
5. Never add real documents, credentials, keys, or personal metadata to the repository or logs.

Please describe user-visible changes and any security or migration impact in your pull request.
