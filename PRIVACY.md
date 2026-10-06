# Privacy policy

Voidbox collects no data. It has no analytics, ads, crash reporting or accounts of its own, and the
developers receive nothing from it.

## Network

Voidbox connects only to the clouds you add: your Nextcloud, WebDAV or S3 server. Files are encrypted
on the device before they are uploaded and decrypted on the device after they are downloaded. The
cloud sees encrypted files, their sizes and when they are read or written, and which parts of a file
are read when media is played or a video thumbnail is made.

## Data on the device

Everything stays in the app's private storage:

- the list of vaults and clouds; cloud passwords and keys are encrypted with a key in the Android
  Keystore;
- vault passwords only if you turn on biometric unlock, encrypted with a Keystore key that needs
  your fingerprint or face;
- thumbnails, encrypted with a key of their vault;
- files you keep offline, as the encrypted files from the cloud;
- a decrypted copy of a file while it is open in a viewer that needs one, deleted when the viewer
  closes.

Android backups of the app are disabled.

## Permissions

- **Network access**, to reach your clouds.
- **Photos and videos**, only for automatic photo upload, when you turn it on.
- **Notifications and a foreground service**, to keep vaults unlocked and finish uploads in the
  background.
- **Biometrics**, for biometric unlock.
- **Start at boot**, to resume automatic photo upload after a restart.
- **Install packages**, only to open an APK stored in a vault with the system installer.

## Contact

dev@vesmirov.com
