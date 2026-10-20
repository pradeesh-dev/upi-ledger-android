# UPI Ledger

Personal expense tracker for UPI payments. Android, offline, no accounts.

## How you log an expense

| Way | What you do | What the app does |
|---|---|---|
| **Share a screenshot** | In GPay / Amazon Pay / PhonePe / Paytm, tap Share on the payment confirmation → *Log in UPI Ledger* | On-device OCR (ML Kit) reads amount, payee, UPI ID, date and transaction ID. Category is filled from your UPI registry. |
| **Bank SMS (automatic)** | Nothing. Allow the SMS permission once. | Debit alerts are parsed as they arrive. Known UPI IDs are logged silently; unknown ones get a notification: *Categorise ₹45 to raju@okicici*. |
| **Share an SMS** | Long-press a bank SMS → Share → *Log in UPI Ledger* | Same parser as above, for messages that arrived before you installed the app. |
| **Quick add** | Tap a chip for a payee you pay often | Pre-fills payee, UPI ID, category and the last amount. |
| **Statement import** | Settings → *Import CSV statement* | Reads HDFC/ICICI/SBI/Axis/Kotak-style CSVs, adds debits, skips anything already logged. |
| **Manual** | Tap *Add* | For cash. |

## UPI ID registry

`tttt@icici → Tea & Snacks`, `dfdjh@hdfc → Food`, and so on. A match on the UPI ID categorises a payment without asking. If the ID is unknown, the app falls back to the payee name, then to what you chose for that payee last time, and finally asks you — and remembers the answer.

## Data

Everything is in a local SQLite database on the phone (included in Android backups). Settings → Export gives you a CSV for the month or for everything. Nothing is sent anywhere; the SMS permission is used only to read debit alerts on the device.

## Building

Requires JDK 17 and the Android SDK (platform 35). `./gradlew assembleRelease`.

Release signing reads `keystore/keystore.properties` locally, or `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from the environment in CI. Without a keystore the release build falls back to the debug key (fine for personal sideloading, but updates must always be signed with the same key, so keep the keystore).

The GitHub Actions workflow in `.github/workflows/build.yml` runs the parser tests, builds a signed APK on every push to `main`, and attaches it to a GitHub release tagged with the version.

## Installing the APK

1. Copy `upi-ledger-vX.Y.Z.apk` to the phone (or download it there).
2. Open it. Android asks to allow installs from that source the first time.
3. On first launch, allow SMS and notifications if you want automatic capture. You can change this later in Settings.
