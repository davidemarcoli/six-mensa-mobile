# SIX Mensa — Publish Guide (App Store)

This guide takes SIX Mensa from a working build to a live App Store listing.
Everything in "Your Mac (build)" below can be done for you. Everything in
"App Store Connect" needs your paid Apple Developer account and your Apple ID.

## Prerequisites

- Paid Apple Developer Program membership (enrollment done).
- Xcode with your Apple ID signed in (Team: `54MYV55XF5`).
- Bundle IDs registered:
  - `dev.davidemarcoli.mensa` (app)
  - `dev.davidemarcoli.mensa.widget` (widget)
  - `dev.davidemarcoli.mensa.intents` (Siri intents extension)
- App Group `group.dev.davidemarcoli.mensa` enabled on all three.

## Step 1 — Create the app record (App Store Connect)

1. Open https://appstoreconnect.apple.com and sign in.
2. Click **Apps → "+" → New App**.
3. Enter:
   - Platform: **iOS**
   - Name: **SIX Mensa**
   - Primary language: English
   - Bundle ID: `dev.davidemarcoli.mensa`
   - SKU: `SIXMENSA`
4. Click **Create**.

## Step 2 — Build the Release archive

Done automatically or via command:

```
cd ios
xcodebuild \
  -project SIXMensa.xcodeproj \
  -scheme SIXMensa \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -allowProvisioningUpdates \
  -archivePath /tmp/SIXMensa.archive.xcarchive \
  archive
```

`-allowProvisioningUpdates` lets Xcode fetch App Store distribution profiles.

## Step 3 — Export & upload

### Option A: via Xcode (recommended)
1. Open `SIXMensa.xcodeproj` in Xcode.
2. Select scheme **SIXMensa**, device = "Any iOS Device".
3. **Product → Archive**.
4. In Organizer click **Distribute App → App Store Connect → Upload**.
5. Follow prompts; wait for "Upload successful".

### Option B: command line
```
xcodebuild -exportArchive \
  -archivePath /tmp/SIXMensa.archive.xcarchive \
  -exportOptionsPlist /tmp/exportOptions.plist \
  -exportPath /tmp/SIXMensa-export \
  -allowProvisioningUpdates
```
`/tmp/exportOptions.plist` must contain:
```xml
<key>method</key><string>app-store-connect</string>
<key>destination</key><string>export</string>
```
The signed IPA is written to `/tmp/SIXMensa-export/SIXMensa.ipa`.
Upload it with **Transporter** (free app) by dragging the `.ipa` in.

## Step 4 — Fill in metadata (App Store Connect)

On the app record fill in:
- **App Privacy** questionnaire — be accurate:
  - Location used (Always, for the in-office notification geofence).
  - Menu data cached in the shared app group.
- **Pricing** — Free or set a price.
- **Description** — what the app does.
- **What's New** — for 1.0: "First release".
- **Keywords**, **Category** (Food & Drink).
- **Support URL** and optional **Marketing URL**.
- **Screenshots** — required sizes listed below.
- **Icon** — the 1024x1024 app icon (already in `Assets.xcassets/AppIcon`).

## Step 5 — Screenshots (required)

Provide screenshots at the required sizes (6.9", 6.5", 5.5", iPad if supported).
These can be captured from the simulator and uploaded per device in App Store Connect.

## Step 6 — Select the build & submit

1. In App Store Connect → your app → **App Store** tab.
2. Scroll to **Build**, click **"+ Select a build"**, choose the uploaded build.
3. Ensure all metadata + privacy are complete.
4. Click **Submit for Review**.
5. Wait for Apple review (hours to days; notifications sent on status change).

## Recommended before submitting: TestFlight

1. In App Store Connect → **TestFlight** → enable Beta Testing.
2. Add yourself as an internal tester.
3. The build uploaded in Step 3 appears in TestFlight automatically.
4. Install via the TestFlight app on your iPhone and test.
5. Then submit that same build for review.

## Notes / likely review questions

- **Location "Always":** Apple reviewers may ask why you need background location.
  Justify it with the in-office daily notification. If you prefer to avoid that,
  restrict to "While Using" (the in-office notification then only works while
  the app is open).
- **App Group:** must be provisioned for all three targets or signing fails.
- **Privacy answers must match what the app actually collects** — don't
  under-report location usage.
