# Zmittag — iOS

Native SwiftUI port of the Zmittag Android app (`../android`) and the six-mensa web app.
Shows the weekly menu (Mon–Fri) of the two SIX canteens in Zurich — **HTP** (Pfingstweidstrasse 110)
and **HT 201** (Hardturmstrasse 201) — with compare, stats, PDF menu plans, daily notifications,
and a home-screen widget.

- Min iOS: **17.0**, Swift 5.9+, no third-party dependencies
- Data: `https://six-mensa-api.homelab.davidemarcoli.dev/` (same backend as web + Android)
- Language: German + English (menu content language = API `?language=`)
- All date logic anchored to `Europe/Zurich`

## Project layout

```
Zmittag.xcodeproj        two targets: Zmittag (app) + ZmittagWidget (extension)
App/                     app target sources
  Core/                  models, API client, date resolution, type normalization, theme
  Data/                  MenuAPI, JSONCache (App Group), SettingsStore, MenuStore, HistoryStore
  Features/              Menu, Compare, Stats (Swift Charts), Settings, PDF (PDFKit), Components
  Notifications/         NotificationManager (UNUserNotificationCenter)
  Localization/          en + de Localizable.strings
  Assets.xcassets        AccentColor (#DE3919), AppIcon (add your icon!)
Widget/                  WidgetKit extension (small/medium/large, brand-red design)
Scripts/                 generate-project.ps1 — regenerates project.pbxproj from the file tree
```

**App Group**: `group.dev.davidemarcoli.mensa` — the app writes the cached week + settings into the
shared container; the widget reads it there. Cache TTLs: week 6 h, history 12 h, PDF links 24 h.

## Build & run (on a Mac)

1. Install Xcode 15+ (Xcode 16 recommended) from the App Store.
2. Open `Zmittag.xcodeproj`.
3. Select the **Zmittag** target → *Signing & Capabilities* → pick your **Team** (same for the
   **ZmittagWidget** target).
4. App Group: if signing fails with an App Group error, add the *App Groups* capability to **both**
   targets and create the group `dev.davidemarcoli.mensa` (the entitlements files already reference
   `group.dev.davidemarcoli.mensa`).
5. Add an app icon: `App/Assets.xcassets/AppIcon.appiconset` currently has an empty 1024 px slot —
   drop a 1024×1024 PNG in and name it in the set (white utensils glyph on `#DE3919` matches Android).
6. Build & run on a real device (or simulator).

If you add/remove Swift files later, re-run the project generator so the file graph stays in sync:

```
powershell -ExecutionPolicy Bypass -File Scripts\generate-project.ps1
```

## Feature notes

- **Menu tab**: HTP/HT201 switch, day chips (today outlined + auto-selected), swipeable day pages,
  pull-to-refresh, share, PDF menu plans, settings.
- **Notifications**: weekdays only, default **10:30 (Zurich)**; toggle + time picker + test button in
  Settings. Self-rescheduling: the app re-schedules the next weekday occurrence whenever it
  foregrounds (covers phone sleep/restart). No geofence "only at work" mode in v1 (Android has one).
- **Widget**: long-press home screen → `+` → Zmittag. Small/medium/large, brand-red, shows today's
  dishes + prices for the standard restaurant. Refreshes when the app refreshes the menu or when the
  user changes the standard restaurant/language.
- **Stats**: history since 2025-04 (default range: last 3 months), price trends, dietary donut,
  dish frequency, allergen frequency — Swift Charts.

## Wire-format quirks (handled in `App/Core/Models.swift`)

- The day field is spelled `menues` on the wire.
- `dietaryType` decodes unknown values to `.unknown`; `origin ""` → `nil`.
- `allergens` sometimes contains serializer junk (`imagePath`, `image/<hex>`) — sanitized.
- `GET /history?from&to` expects strict `YYYY-MM` or silently returns `[]` (empty string = no bound).
- `imagePath` currently always 404s (backend `imageGeneration: false`) — not rendered.

## Releasing to the App Store

1. **Archive**: with a physical device selected (or *Any iOS Device*),
   *Product → Archive*. Version is `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` in the target
   build settings (currently 1.0.0 (1)) — bump before each release.
2. **TestFlight** (optional): in Xcode *Organizer* select the archive → *Distribute App → App Store
   Connect* → *Upload*, or use the *TestFlight* tab to upload and add internal testers right away.
3. **App Store Connect** (appstoreconnect.apple.com):
   - *My Apps → +* → new app, platform iOS, name e.g. "SIX Mensa",
     bundle ID **`dev.davidemarcoli.mensa`** (must match the target), primary language English.
   - Fill in App Information: description, keywords, support/marketing URL, SKU (auto),
     age rating (will be 4+), pricing tier (free).
   - Privacy: the app collects **no data** — select "Data Not Collected"
     (it only calls a public HTTPS menu API; no accounts, no analytics, no tracking).
   - Attach the uploaded build under the version's *Build*.
4. **Submit for review**: *Pre-prepare for Submission* → *Submit*. Review typically takes 24–48 h.

### Before submitting, double-check

- [ ] App icon set (otherwise the app ships with the placeholder icon).
- [ ] Widget appears on a test device: long-press home screen → `+` → SIX Mensa.
- [ ] Notification flow: Settings → toggle on (grants permission) → "Send test notification" →
      received; toggle off → pending notification cancelled.
- [ ] German/English switch updates menu text, prices (9.90 vs 9,90) and widget.
- [ ] Dark mode looks right on all four tabs + settings + PDF.
