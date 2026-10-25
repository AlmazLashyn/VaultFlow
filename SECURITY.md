# Security policy

VaultFlow is a portfolio project and is not a certified financial or banking product. It does not promise absolute protection or secure erasure from flash storage.

Please do not publish details of a suspected vulnerability before the maintainer has had a chance to review it. This repository currently has no configured private security contact. The repository owner should add a private contact method before making the project public; until then, use a private channel available from the owner's profile rather than including sensitive details in a public issue.

The app stores document contents encrypted in its private no-backup directory. Metadata such as titles, categories, dates, and tags is stored in Room without application-level encryption. The AES key is held in Android Keystore and is not included in backups. Reinstalling the app, clearing app data, losing the device, or losing its Keystore key can make encrypted documents unrecoverable.
