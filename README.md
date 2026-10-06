# Gratitude Garden

An Android (Kotlin + Jetpack Compose) gratitude-journaling game. Users write daily
gratitude entries, earn coins, and grow a personal garden. The backend is **Supabase**
(Auth + Postgres/PostgREST), accessed from the app through the official Supabase Kotlin
client using the **anon key** only.

Gratitude Garden is being taken toward a public release on **Google Play**. The feature
set below is complete and working; the [roadmap](#roadmap-to-google-play) is the plan for
turning it into a shippable product.

| | |
| --- | --- |
| Status | Feature-complete; pre-release |
| `applicationId` | `com.gratitudegarden.app` — set in [0.1](#tier-0--release-blockers); **permanent once published** |
| Version | `versionCode 1` / `versionName 1.0` — never published |
| Min / target SDK | 28 / 36 |
| Release build | R8-minified, signed with the upload key; `consumer` flavor only ships to Play (see [0.2](#tier-0--release-blockers)) |

---

## Table of contents

- [Roadmap to Google Play](#roadmap-to-google-play)
  - [Tier 0 — Release blockers](#tier-0--release-blockers)
  - [Tier 1 — Retention features](#tier-1--retention-features)
  - [Tier 2 — Trust, reach, and money](#tier-2--trust-reach-and-money)
  - [Tier 3 — Engineering foundation](#tier-3--engineering-foundation)
  - [Suggested sequencing](#suggested-sequencing)
  - [Open decisions](#open-decisions)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [App architecture](#app-architecture)
- [Supabase backend](#supabase-backend)
  - [Client write surface](#client-write-surface)
  - [Server-authoritative economy](#server-authoritative-economy)
    - [A policy constrains which row, never which columns](#a-policy-constrains-which-row-never-which-columns)
- [Feature notes](#feature-notes)
  - [Accessibility (TalkBack)](#accessibility-talkback)
  - [Streak freezes](#streak-freezes)
  - [XP and levels](#xp-and-levels)
  - [Backdrops](#backdrops)
  - [Daily streak reminders](#daily-streak-reminders)
  - [Home-screen widget and quick replies](#home-screen-widget-and-quick-replies)
  - [Entry photos](#entry-photos)
  - [Entry moods](#entry-moods)
  - [Prompts, days and lists](#prompts-days-and-lists)
  - [Crash reports and the funnel](#crash-reports-and-the-funnel)
  - [Account deletion](#account-deletion)
  - [Privacy policy and Data Safety](#privacy-policy-and-data-safety)
  - [Admin CRUD dashboard](#admin-crud-dashboard)
- [Setup & build](#setup--build)
- [Run on an emulator](#run-on-an-emulator)
- [Granting / changing admins](#granting--changing-admins)

---

## Roadmap to Google Play

Sizes: **S** = a session, **M** = a few sessions, **L** = a multi-week arc.

### Tier 0 — Release blockers

Cannot publish without these. Most are unglamorous, and several are fine for a prototype
but disqualifying on a public store listing.

| # | Item | Size | Why it blocks |
| --- | --- | --- | --- |
| 0.1 | **Settle the permanent namespace** | M | ✅ **Done** — renamed to `com.gratitudegarden.app` across `namespace`, `applicationId`, all 48 source files, and the reminder broadcast action. The application ID is **permanent once published**, which is why this landed before any feature work. |
| 0.2 | **Signing, minification, real release build** | S/M | ✅ **Done** — R8 + resource shrinking on, release signed with an upload key kept outside the repo, and the signed build walked end to end on an emulator (signup, entries, planting, watering, journal, reminders, sign-out) with no crashes. No hand-written keep rules were needed. Play App Signing enrollment happens at first upload. *Original scope:* No signing config existed and R8 was off. Needs an upload keystore (stored outside the repo and **backed up** — losing it means never updating the app again), Play App Signing enrollment, R8 with keep rules for the Supabase/Ktor/kotlinx-serialization models, and an AAB we actually install and walk before uploading. Serialization + R8 is the classic first-crash-in-production combo. |
| 0.3 | **Get the admin dashboard out of the consumer build** | S | ✅ **Done** — a `staging` flavor holds all admin code; the `consumer` build compiles an empty stub, and an admin account signed into it sees no dashboard. *Original scope:* `AdminDashboardScreen.kt` is a generic CRUD editor over nine tables. RLS is the real guard, but shipping the client-side admin surface to every user is unnecessary attack surface and a reviewer red flag. Preference: a `staging` flavor, so we keep the tooling without shipping it. |
| 0.4 | **Privacy policy, Data Safety form, account-deletion URL** | M | ✅ **Built** — the policy and a self-serve deletion page live in `site/`, published to GitHub Pages; the Play Console answers are in `docs/play-data-safety.md`; sign-up links the policy and no longer pre-ticks consent; deleting an entry now erases its words on the server; the login screen's dead Google, Apple and "Forgot password?" controls are gone (they return with 0.9). Left: enter the answers once the Play developer account exists (`docs/play-data-safety.md` → Setting up Play Console). See [Privacy policy and Data Safety](#privacy-policy-and-data-safety). *Original scope:* Play requires all three, and this app trips several categories at once: email + password, free-text personal reflections, photos, microphone, notifications, and crash logs. The deletion backend existed; the public web page and the hosted policy did not. |
| 0.5 | **Harden secrets and key handling** | S | ✅ **Done** — two coin-minting holes closed, then the policy audit found three more (a privilege escalation among them) and closed those too. Sign-up also checks new passwords against known breaches, the one piece of Supabase's leaked-password protection that needs Pro; see [Server-authoritative economy](#server-authoritative-economy) and [Client write surface](#client-write-surface). |
| 0.6 | **Crash reporting and basic analytics** | S | ✅ **Done** — Sentry reports crashes, ANRs and unexpected caught errors, scrubbed on the device first; the first-week funnel is SQL over data we already store, so the app sends no analytics events; see [Crash reports and the funnel](#crash-reports-and-the-funnel). *Original scope:* Zero production visibility today. Without Crashlytics (or Sentry) plus Play Vitals we learn about an ANR from a one-star review. Add crash reporting and a small funnel (signup completed, first entry, day-2 return) *before* there are users to lose. |
| 0.7 | **Store listing assets** | M | Feature graphic, 4–8 phone screenshots (ideally a short video), 512px icon, short + full description, content rating questionnaire. The launcher icon is still the Android Studio template — that alone reads as "unfinished" in search results. Design work, routinely underestimated. |
| 0.8 | **Notification & alarm permission posture** | S | ✅ **Done** — `SCHEDULE_EXACT_ALARM` dropped; the reminder always uses the inexact, Doze-safe `setAndAllowWhileIdle`, and was watched firing and re-arming on an emulator. The WorkManager move was dropped on inspection: WorkManager declares `RECEIVE_BOOT_COMPLETED` itself (so it couldn't be removed), and Android defers its work hardest for rarely-opened apps, which are the users the nudge is for. *Original scope:* We held `SCHEDULE_EXACT_ALARM` with a documented inexact fallback. Play scrutinizes exact alarms and a daily journaling nudge is unlikely to qualify for an exemption. Move the reminder fully to WorkManager and drop the permission (and `RECEIVE_BOOT_COMPLETED` with it). Fewer sensitive permissions = smoother review. |
| 0.9 | **Email sender, confirmation and password reset; then Google sign-in** | M | There is no password reset, and email confirmation is off, because Supabase's built-in email only reaches the project's own team and is capped per hour. Needs a domain we own, an SMTP provider (e.g. Resend) and Supabase custom SMTP. Then: confirmation on (sign-up says "check your email"), and reset through a `reset-password.html` page on the site (supabase-js recovery) or a deep link into the app. **Google sign-in waits for this**: Supabase links a Google login to an existing account with the same email and only drops *unconfirmed* identities, so while every password account counts as confirmed, anyone could create one for someone else's Gmail address and keep access after the owner signs in with Google. Google sign-in itself: Credential Manager plus supabase-kt `signInWith(IDToken)` with a nonce; Google-only accounts have no password, so deletion needs Google re-auth in the app and "Continue with Google" on `delete-account.html`; the policy and Data Safety add *OAuth* and the name, email and picture URL Google shares. |

### Tier 1 — Retention features

A gratitude journal lives or dies on day-7 retention. Roughly ordered by expected impact
per unit of work.

- **1.1 Offline-first with a local database — L — *highest impact*.** Every read in
  `GardenRepository` is a live PostgREST call: no network means no journal, no garden, no
  writing an entry. For a habit app that is fatal — people journal in bed, on planes, on
  the subway. Add Room as the source of truth, render from it instantly, sync to Supabase
  in the background with a queue and conflict resolution. Also kills the loading spinners.
  Do this **first** in this tier: it gets harder with every feature layered onto the
  current direct-to-network pattern. Plan in
  [`docs/offline-first-plan.md`](docs/offline-first-plan.md): journal fully offline,
  economy online-only, three phases. **Done**: the read cache, the offline journal, and
  the polish (an offline banner, economy buttons that say they need a connection, and
  retry or discard for an entry the server refuses).
- **1.2 Home-screen widget + richer notifications — M.** The fastest path to a daily habit
  is not opening the app. **Done**: a widget showing the garden, the streak and what's
  left of today, with a Write button that opens the entry sheet; and a reminder you can
  answer inline, put off for an hour, or that stays quiet once you've written. See
  [Home-screen widget and quick replies](#home-screen-widget-and-quick-replies).
- **1.3 Make the garden feel like a game — L.** The loop today is write → coins → buy seed
  → place → water. Good skeleton, thin. In priority order: **streak freezes** (✅ **done**:
  one free a month plus more for coins, up to 2 held; see [Streak freezes](#streak-freezes)),
  an **XP ladder** (✅ **done**: days journaled earn levels, which unlock the shop's
  level-gated seeds; see [XP and levels](#xp-and-levels)), **backdrops you can equip**
  (✅ **done**: one scene per level from 2 to 5, drawn above the plot; see
  [Backdrops](#backdrops)), seasons and weather, rare and evolving plants, garden expansion, and animated milestone
  moments at 7/30/100 days.
- **1.4 Entry experience beyond a text box — M.** **Photos** attached to an entry (✅
  **done**: one per entry, from the gallery or the camera, added or changed later in Edit,
  and written offline like the text; see [Entry photos](#entry-photos)); a **mood** per
  entry (✅ **done**: optional, one of five from rough to great, set when writing or in
  Edit, and it unlocks 1.5; see [Entry moods](#entry-moods)); and (✅ **done**) a daily
  question for the blank-page problem, a view of one day's entries, and lists and line
  breaks that survive a round trip; see [Prompts, days and lists](#prompts-days-and-lists).
  **1.4 is complete.**
- **1.5 Insights / "your year in gratitude" — M.** Once entries carry mood and timestamps:
  themes over time, mood against streak length, "on this day last year", and a shareable
  year-in-review card — also the cheapest organic acquisition channel available to us.
- **1.6 Onboarding that earns the first entry — M.** Today a new user lands on a login
  form. The first 60 seconds should be: what this app is, one seed planted, one entry
  written, *then* an account. Needs local-anonymous entries that migrate on signup, so
  sequence it after 1.1.

### Tier 2 — Trust, reach, and money

- **2.1 Privacy as a feature — M.** Biometric/PIN app lock, an explicit "your entries are
  yours" story, and ideally optional end-to-end encryption of entry text. E2E is a real
  design problem (it breaks server-side search and the admin tooling and demands a key
  recovery story), but "the developer cannot read your journal" is a strong differentiator
  in this exact category.
- **2.2 Export and data portability — S.** JSON / PDF / Markdown export. Easy to build,
  privacy-credible, and it removes the "what if the app dies" objection.
- **2.3 Monetization — M/L.** The garden economy fits a free tier plus a **Gratitude Garden
  Plus** subscription (Play Billing): unlimited photos, premium seeds and decor, insights,
  themes, cloud backup, streak freezes. Deliberately *not* recommended: paywalling the
  journal itself, or ads — ads in a mindfulness app are tonally wrong and will show up in
  reviews. Play Billing brings its own compliance work (management deep links, restore,
  grace periods).
- **2.4 Localization + tablet/foldable layouts — M.** `strings.xml` exists, but copy is
  scattered through Compose literals. Extracting strings then shipping 3–5 languages is
  one of the cheapest reach multipliers on Play. Separately, `GardenScreen.kt` is ~1,100
  lines of phone-portrait-only layout; adaptive layouts open up tablets and Chromebooks.
- **2.5 Wear OS / Quick Settings tile — S/M.** A watch complication showing the streak plus
  a voice-entry tile. Small surface, disproportionate "this app is real" signal, and it
  reuses the existing `SpeechRecognizer` path.

### Tier 3 — Engineering foundation

- **Modularize — M.** `GardenScreen.kt` (~1,100 lines) and `MeScreen.kt` (~760) will not
  survive photos + seasons + insights. Split by feature; extract the garden canvas.
- **Dependency injection — S/M.** `AppContainer` hand-rolls it today; Hilt pays for itself
  once there's a sync layer, a work scheduler, and a billing client.
- **Test depth — M.** Currently 5 unit-test classes (16 cases) + 3 UI tests. A public app needs repository
  tests against a fake backend, sync-conflict tests, and a smoke suite run against the
  **release** build (post-R8).
- **CI/CD — S/M.** GitHub Actions running lint + tests on PRs, Play Console internal-track
  upload on tag. Removes the "did I remember to run the tests" failure mode.
- **Design system pass — M.** Dark mode is handled; a real color/type/spacing system,
  motion polish, and a custom icon set are what separate "student project" from "product"
  in screenshots.

### Suggested sequencing

1. **Foundation first:** 0.1 rebrand, 0.2 signing, 0.3 admin split — ✅ all done. All three get
   strictly more painful the more code exists. Before any feature work.
2. **The structural bet:** 1.1 offline/Room. Everything after is easier with it in place;
   everything built before it has to be retrofitted.
3. **The retention loop:** 1.3 streak freeze (✅ done), 1.2 widget
   (✅ done), 1.4 entry experience (✅ done).
4. **Launch prep:** 0.4 privacy/Data Safety (✅ built), 0.6 crash reporting (✅ done), 0.9 email and reset, 0.7 listing assets → ship
   to a closed track and get ~20 real testers before public release.
5. **Post-launch:** 1.5 insights, 2.1 privacy features, 2.3 monetization, 2.4 localization.

### Open decisions

These block or reshape the work above and should be settled before building.

- **Free forever, or free + subscription?** Decides whether 2.3 shapes 1.3's economy design.
- **Is E2E encryption (2.1) a core promise or a nice-to-have?** It constrains 1.5 insights
  and the admin tooling — a fork in the road, not a later add-on.
- **Solo project, or will others contribute?** Determines how much CI/CD is worth.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose (Material 3) |
| Navigation | `androidx.navigation:navigation-compose` |
| State | `ViewModel` + `StateFlow`, collected with `collectAsStateWithLifecycle` |
| DI | Manual service locator (`AppContainer` held by the `Application`) — no Hilt |
| Backend | Supabase Auth + PostgREST + Storage via `io.github.jan-tennert.supabase` (BOM `3.6.0`) over Ktor/OkHttp |
| Serialization | `kotlinx.serialization` (partial DTOs, `ignoreUnknownKeys = true`) |
| Local prefs | DataStore (reminder on/off + time-of-day) |
| Reminders | `AlarmManager` (inexact, Doze-safe) + `BootReceiver`; no exact-alarm permission |
| Widget | Jetpack Glance (`glance-appwidget` `1.2.0`) |
| Images | Coil 3 (`coil-compose` `3.4.0`), local files only; 3.5+ needs Kotlin 2.4 |
| Crash reports | Sentry (`sentry-android-core` `8.59.0`, Gradle plugin `6.23.0`); off without a DSN |

---

## Project structure

```
app/src/main/java/com/gratitudegarden/app/
├─ GratitudeGardenApplication.kt   # builds AppContainer once per process
├─ MainActivity.kt
├─ di/
│  └─ AppContainer.kt              # SupabaseClient + repositories (manual DI)
├─ data/
│  ├─ GardenRepository.kt          # user-facing reads + RPC calls (auth, journal, garden, shop)
│  └─ EntryPhotos.kt               # photo paths, sizing, and PhotoPreparer (pick -> stored JPEG)
├─ notifications/
│  ├─ ReminderScheduler.kt         # schedules/cancels the daily alarm
│  ├─ ReminderReceiver.kt          # fires -> re-arms, posts unless written today; "Later"
│  ├─ ReplyReceiver.kt             # a thought written inline in the reminder
│  ├─ ReminderNotifications.kt     # channel + notification construction
│  ├─ ReminderPreferences.kt       # DataStore-backed on/off + time-of-day
│  └─ BootReceiver.kt              # re-arms after reboot / app update
├─ widget/
│  ├─ GardenWidget.kt              # the Glance widget and its three layouts
│  ├─ GardenWidgetReceiver.kt      # system entry point; midnight + clock/zone changes
│  ├─ WidgetSync.kt                # keeps the widget's state current, pushes changes
│  ├─ WidgetState.kt               # what it shows, derived from the Garden's rows
│  ├─ GardenSnapshot.kt            # paints the garden card into a bitmap
│  ├─ WidgetMidnight.kt            # the redraw alarm at local midnight
│  └─ WidgetPreviews.kt            # the picker's generated preview (Android 15+)
├─ model/
├─ ui/
│  ├─ GardenApp.kt                 # top-level nav: auth flow vs HomeScaffold + routes
│  ├─ ViewModelExt.kt              # CreationExtras -> repositories
│  ├─ components/                  # BottomNav, PillButton, PgTextField, EntryPhotos (picker, viewer), EntryMood, ...
│  ├─ theme/                       # Color.kt, Theme.kt, Type.kt (Caprasimo / Nunito)
│  ├─ sprites/                     # vector plant / icon drawing
│  ├─ garden/ shop/ journal/ me/   # feature ViewModels + UI state
│  └─ screens/
│     ├─ GardenScreen.kt ShopScreen.kt JournalScreen.kt MeScreen.kt
│     └─ LoginScreen.kt SignUpScreen.kt
└─ util/                           # lifecycle logging, Diagnostics (crash reports + scrubbing)

app/src/staging/java/com/gratitudegarden/app/   # staging flavor only — never in the Play build
├─ data/
│  ├─ AdminModels.kt               # admin table-spec engine + payload builder
│  └─ AdminRepository.kt           # generic admin CRUD over JsonObject + admin check
└─ ui/
   ├─ admin/
   │  ├─ AdminTools.kt             # the seam main calls; the consumer copy is an empty stub
   │  └─ AdminViewModel.kt         # adminUiState + create/save/delete actions
   └─ screens/
      └─ AdminDashboardScreen.kt   # the admin dashboard UI

supabase/migrations/                # source of truth for the schema (22 files)
db/                                 # older hand-written SQL notes (subset of the above)
docs/                               # perf + test-optimization write-ups
profiling/                          # before/after profiling evidence
```

---

## App architecture

- **One `SupabaseClient`** is created in `AppContainer` from `BuildConfig.SUPABASE_URL`
  / `BuildConfig.SUPABASE_ANON_KEY` (read from `local.properties`, never committed).
- Repositories wrap the client:
  - `GardenRepository` — auth (`signIn/signUp/signOut`) and the normal user loop
    (journal entries, garden, plants, stats, wallet, profile, shop). Business logic
    (coins, streaks, provisioning) lives in Postgres `SECURITY DEFINER` **RPCs**; the
    repo just calls them and reads RLS-scoped rows.
  - `AdminRepository` — admin-only generic CRUD, staging flavor only (see below).
- **ViewModels** expose a single immutable `UiState` via `StateFlow`. They are built
  by `viewModelFactory` blocks that pull the repository from the `Application` through
  `CreationExtras` extensions in `ViewModelExt.kt`.
- **Navigation** (`GardenApp.kt`): unauthenticated users see the `login`/`signup`
  graph; authenticated users land in `HomeScaffold`, whose `NavHost` holds the
  `garden`, `shop`, `journal`, `me`, and `admin` routes.

- **Room is the only thing the screens read** (roadmap 1.1). Refreshes pull from Supabase
  into Room, the entries by a delta sync on `updated_at`; a failed refresh leaves the last
  good copy on screen.
- **Journal writes are local first.** Submitting, editing or deleting an entry lands in
  Room at once and queues an op in an `outbox` table. The repository delivers the queue in
  order, straight away when it can, and `OutboxWorker` (WorkManager, network required)
  delivers it later otherwise, even if the app isn't running. Every op is safe to resend:
  the entry id is generated on the device, so the server recognises a replayed submit and
  pays it once. An entry the server refuses keeps its text and is marked "couldn't sync",
  with retry and discard. Economy actions (buy, plant, water, move, dig) stay online-only
  and say so while offline.

---

## Supabase backend

Ten tables, all with **Row-Level Security enabled**:

`profiles`, `user_settings`, `user_stats`, `items`, `coin_wallets`,
`gratitude_entries`, `gardens`, `user_inventory`, `garden_plants`, `streak_frozen_days`.

Enum columns (values used by the admin UI come straight from these):

| Column | Enum | Values |
| --- | --- | --- |
| `items.category` | `item_category` | `seed`, `decor`, `backdrop` |
| `items.rarity` | `item_rarity` | `common`, `uncommon`, `rare`, `epic`, `legendary` |
| `gratitude_entries.input_method` | `input_method` | `text`, `voice_to_text` |
| `garden_plants.growth_stage` | `growth_stage` | `seedling`, `sapling`, `mature` |
| `garden_plants.health` | `plant_health` | `healthy`, `thirsty`, `wilting` |
| `user_settings.theme` | *(text check)* | `light`, `dark`, `system` |

One private Storage bucket, `entry-photos`, holds entry photos in a folder per user (see
[Entry photos](#entry-photos)).

Normal users' RLS policies are scoped to `auth.uid()` (each user only ever sees/edits
their own rows). New accounts are provisioned by the `handle_new_user` trigger, and
gameplay mutations go through `SECURITY DEFINER` RPCs (`submit_gratitude_entry`,
`purchase_item`, `place_plant`, `water_plant`, …).

### Migrations

`supabase/migrations/` holds the complete schema history — every table, enum, RLS policy,
trigger, and RPC above, in the order it was applied. Replaying every file against an
empty project reproduces the backend exactly. The four files under `db/` are older
hand-written notes covering a subset of the same changes; `supabase/migrations/` is the
source of truth.

### Client write surface

After `20260916210000_lock_client_write_surface`, a **non-admin client can perform exactly
three kinds of write**, and every one of them is server-validated:

| Path | How |
| --- | --- |
| The gameplay RPCs | `submit_gratitude_entry`, `edit_gratitude_entry`, `delete_gratitude_entry`, `set_entry_photo`, `purchase_item`, `place_plant`, `move_plant`, `water_plant`, `set_active_backdrop`, `buy_streak_freeze`, `delete_current_user`, `mark_notif_prompt_seen` — all `SECURITY DEFINER`, all deriving the user from `auth.uid()` |
| `garden_plants` DELETE | `garden_plants_delete_own`, scoped through `gardens.user_id`. A DELETE is all-or-nothing, so there is no column subset to abuse |
| Files in `entry-photos` | Upload, overwrite and delete, only inside the caller's own `{user_id}/` folder (`entry_photos_*_own`). A file does nothing until `set_entry_photo` attaches it, and that checks the path |
| Nothing else | There are **no INSERT policies at all**, and after 0.5 there are **no self-serve UPDATE policies at all** |

`anon` holds no table privileges whatsoever, and `authenticated` no longer holds
`TRUNCATE` / `TRIGGER` / `REFERENCES` (TRUNCATE is *not* subject to row security, so that
grant would have bypassed RLS entirely the moment any other SQL path opened up).

### Server-authoritative economy

**The client never names a price or a reward.** This is load-bearing: the anon key ships
inside the APK, so anything the app sends as an economy value is only a suggestion that an
attacker can rewrite before it reaches PostgREST.

Migration `20260910051500_server_authoritative_economy` closed **two independent** ways to
mint coins.

**Hole 1 — the amount came from the client.**

- `submit_gratitude_entry(..., p_coin_reward int default 5)` validated only `p_coin_reward >= 0`,
  then credited the wallet with it. A single POST with `p_coin_reward: 2147483647` minted
  max-int coins.
- `water_plant(..., p_water_cost int default 10)` had the same shape: pass `0`, water forever.

Both parameters are gone. Because Postgres cannot drop a parameter via `CREATE OR REPLACE`,
and because PostgREST picks an overload by matching the JSON body keys against parameter
names — so a surviving 3-arg version would still be reachable by a client that sends
`p_coin_reward`, making the exploit payload its own overload selector — the old signatures
are **dropped**, not superseded.

**Hole 2 — the daily cap wasn't a cap.** `daily_entry_cap` counted only `deleted_at is null`
entries, but `delete_gratitude_entry` just stamps `deleted_at`; it never refunds the coins.
So `submit → delete → submit → delete` earned without limit no matter what a single entry
paid — reachable from the app's own delete button, no crafted request needed. The cap now
counts **every** entry written today, deleted ones included: you were paid for it, so it
still counts. (Refunding coins on delete was the alternative; it can drive a wallet negative
and touches a second RPC, so the tighter fix won.) `GardenRepository.entriesTodayCount()`
mirrors this server-side count so the "thoughts left today" chip can't promise entries the
server will refuse.

**How the reward is derived.** `public.entry_reward(text, streak, first_of_day)` is a pure
`immutable` function, split out so it can be tested on its own
(`select public.entry_reward('…', 14, true);`) and retuned in one place:

| Component | Range | Notes |
| --- | --- | --- |
| Base | `5` | The old flat reward, kept as a floor so nothing became stingier and the 50–220 coin item prices stay meaningful |
| Effort | `0–3` | `+1` per 40 characters, capped at 3 — so it stops paying at 120. Whitespace runs are collapsed first, so padding with newlines buys nothing and a 10,000-character paste earns exactly what a 120-character paragraph does |
| Streak | `0–5` | `+1` per full week, **first entry of the day only** |

The streak bonus is restricted to the day's first entry on purpose: the streak an entry
extends is the same for every entry of a day, so paying the bonus on all ten allowed entries
would multiply it tenfold. Net range is
**5–13** for the first entry of a day and **5–8** after that — verified by brute force across
600 lengths × 400 streak values.

Because `submit_gratitude_entry` now reads `user_stats` before the insert (the reward depends
on the streak and is stored on the row), it takes `coin_wallets FOR UPDATE` **first**, keeping
the `coin_wallets → user_stats` lock order that `water_plant` and `purchase_item` already use.
Acquiring them in the opposite order would open a same-user deadlock window.

**A "day" is the user's local day** (`20260923140000_local_day`). The app sends its IANA zone
(`p_time_zone`) with every entry, and the server stores it on `user_settings.time_zone` and
dates the entry in that zone. Entries used to be dated in UTC, which for a New York user
flipped the day at 8 PM (the reminder's default time): evening entries landed on tomorrow,
and writing on a Monday morning and a Tuesday evening broke the streak. The zone is
client-supplied, so it was also made **monotonic** there: `greatest(local date,
last_entry_date)`, so the most anyone gained from changing zones was one extra day's cap.

**Entries written offline keep the day they were written** (`20260924150000_offline_journal`).
A queued entry may reach the server hours later, so the client now sends the entry's id
(`p_id`), when it was written (`p_written_at`) and the zone it was written in:

- A second submit with the same id returns the first row untouched: no second row, no
  second reward. The id check runs after the wallet and stats locks, so two in-flight
  copies of one submit serialize. An id owned by another user is an error.
- The write time is trusted within `[now() − 36 h, now() + 5 min]`, otherwise `now()`. The
  entry's day is its local date in its zone, and `created_at` is that instant.
- That backdating replaces the monotonic rule, which refused exactly this. The bound still
  holds: a zone moves the date by at most a day and backdating is capped at 36 h, so the
  most anyone gains is yesterday's unused cap. The cap is still per `entry_date`, deleted
  rows included, clamped at 50.
- Entries no longer arrive in date order, so the streak is **recomputed** from the dates
  (`streak_through(user, day)`, gaps and islands) rather than incremented. Deleted entries
  still count, as before.
- A repeated `delete_gratitude_entry` succeeds instead of raising "entry not found".

The client mirrors the day rule in `entryDay()`, so the "thoughts left" chip counts the
same day the cap does, and it checks the cap (mirrored from `user_settings`) before
queueing, so an entry past it is refused on the spot rather than at sync.

Watering is held at a flat server-side `10`, matching the previous cost so the
"Water · 10 coins" label and the affordability gate in `GardenScreen.kt` stay truthful.
Scaling it by growth stage would be a balance change rather than a security fix.

`purchase_item(p_item_id uuid)` never had this problem — it has always read
`items.price_coins` server-side, and is the pattern the other two now follow.

> **Grants are attached to a function signature, so `DROP FUNCTION` discards them.** The
> migration re-applies the `revoke ... from anon` that `07_advisor_fixes` had established on
> both functions; without that step, dropping them would have silently undone existing
> hardening. It also revokes `anon` on `move_plant` and `is_current_user_admin`, which were
> added after `07_advisor_fixes` and never got the same treatment.

### A policy constrains which *row*, never which *columns*

Making the amounts server-authoritative fixed only half the problem. The audit that followed
(migration `20260916210000_lock_client_write_surface`) found three more holes, all one bug:

`profiles_update_own`, `user_settings_update_own`, and `gardens_update_own` were each written
as `using (auth.uid() = <owner>) with check (auth.uid() = <owner>)`. That is a correct
*ownership* check and a complete non-answer to *column* safety — and `authenticated` also held
a table-wide `UPDATE` grant. Postgres `WITH CHECK` cannot reference `OLD`, so a policy is
structurally incapable of saying "this column may not change."

| | What it allowed |
| --- | --- |
| `profiles` | `PATCH {"is_admin": true}` on your own row. That activates the nine `<table>_admin_all` policies — full read/write over **every** table, including every other user's journal entries. It also trivially defeats the work above: an admin writes `coin_wallets.balance` directly. |
| `user_settings` | `PATCH {"daily_entry_cap": 100000}`. `submit_gratitude_entry` reads that column as the daily earnings ceiling — a third coin printer. The economy migration had hardened how the cap is *counted*, but not where it *comes from*. |
| `gardens` | `PATCH {"active_backdrop_item_id": …}` equips an unowned item, bypassing `purchase_item`. `set_active_backdrop` checks ownership correctly; the policy just let the client skip it. `grid_rows`/`grid_cols` were free garden expansion. |

**The fix is to drop the policies, not to restrict the columns.** Column-level `GRANT`s are
the obvious tool and the wrong one here: grants are scoped to a *role*, and admins **are**
members of `authenticated` — there is no separate Postgres role for them, only the
`is_current_user_admin()` predicate. Revoking `update (is_admin)` would take the admin
dashboard down with it. Dropping the policy does not, because admin access comes from the
separate **permissive** `<table>_admin_all` policy, and permissive policies are `OR`'d —
removing one branch of a disjunction cannot affect another. The live proof was already in the
schema: no table has *any* INSERT policy, yet admin create has always worked, purely because
`<table>_admin_all` is `FOR ALL`.

`is_admin` additionally gets a `BEFORE UPDATE` trigger as a second, independent lock, since it
is the single value that unlocks all nine admin policies. It discriminates on `current_user`
(`authenticated` for a user JWT, `postgres` in the SQL editor) rather than on
`is_current_user_admin()` alone — otherwise it would break the documented
[admin-granting procedure](#granting--changing-admins), where `auth.uid()` is null.

> **Expect silence, not a 403.** With the policy gone, a non-admin `PATCH` doesn't error — RLS
> matches zero rows and PostgREST returns `204 No Content`. Verify these by re-reading the row,
> never by asserting on the status code.

---

## Feature notes

### Accessibility (TalkBack)

The app works with **TalkBack**, Android's built-in screen reader, so it is usable by
people who are blind or have low vision.

- **Every icon-only control is labeled.** The password show/hide toggle, the sign-up back
  button, and the garden mic/record buttons announce their action and current state
  (e.g. "Show password" ↔ "Hide password").
- **Interactive controls expose their role and state.** Bottom-nav items are announced as
  tabs with a "selected" state; the sign-up terms box is announced as a checkbox.
- **Informational visuals read as one phrase.** Each journal week-strip day is merged into
  a single label ("Monday, entry logged"), and garden plants announce their name and
  growth stage ("Tulip, mature").
- **Garden plants are operable by screen reader.** Tapping a plant was a raw gesture
  TalkBack could not reach; a semantics click action was added so plants open with a
  TalkBack double-tap. Empty plots announce their grid position ("Empty plot, row 2,
  column 3").
- **Decorative icons stay silent.** Purely visual icons (stat flames, coins, plant sprites)
  expose no description, so TalkBack skips them instead of reading noise.

<details>
<summary>Testing with TalkBack on a physical device</summary>

A physical Android phone is the most reliable way to verify this — an emulator driven with
a mouse does not emulate TalkBack's touch exploration well.

1. **Enable TalkBack:** Settings → Accessibility → TalkBack → toggle **On** and accept the
   prompt. (Holding **both volume keys for 3 seconds** toggles it on/off.)
2. **Learn the gestures** — touch behaves differently while TalkBack is on:
   - **Swipe right / left** — move to the next / previous element (read aloud).
   - **Double-tap anywhere** — activate the focused element.
   - **Two-finger swipe** — scroll.
3. **Walk the app** and confirm every control is announced meaningfully — nothing should
   read as "unlabeled":
   - **Login** — the password **eye** toggle → "Show password" / "Hide password".
   - **Sign-up** — the **back** button → "Back to login"; the **terms** box →
     "checkbox, not ticked / ticked".
   - **Bottom nav** — each tab reads its name and "selected" when active.
   - **Garden** — plants → "\<name\>, \<stage\>" and open on double-tap; empty plots →
     "Empty plot, row X, column Y"; the **mic** → "Record a gratitude note".
   - **Journal** — the week strip → "Monday, entry logged" / "…, no entry".
4. **Turn TalkBack off** the same way (Settings → Accessibility → TalkBack, or the
   volume-key shortcut).

</details>

### Streak freezes

A missed day is forgiven if a freeze is on hand (`20260924200000_streak_freeze`). Losing a
long streak to one bad night is the top reason people quit habit apps for good, and since the
reward scales with streak length, a broken streak also costs coins.

- **Where they come from.** One free freeze per calendar month, plus more for 50 coins in the
  Shop (`buy_streak_freeze()`), up to **2** held. The free one is granted lazily:
  `freezes_on(banked, grant_month, today)` adds it if this month's hasn't been banked, and a
  submit or a buy banks it. No cron job.
- **When they're spent.** Automatically, by the first entry of a day that follows a 1–2 day
  gap, and only if the freezes on hand cover the **whole** gap. A wider gap resets the streak
  as before and spends nothing. The covered days go in `streak_frozen_days`, which the owner
  can read and only the RPCs can write.
- **What a frozen day is worth.** It bridges the run but doesn't lengthen it: day 10, a
  frozen day, then an entry is day 11. `streak_through()` walks entry dates and frozen days
  together to find the run, and counts only the entries. The reward's streak bonus sees the
  bridged run.
- **Late entries.** If an offline entry arrives up to 36 h late and lands on a frozen day,
  the freeze wasn't needed: the frozen day is removed and the freeze handed back. A replayed
  submit returns before any of this, so it can't spend a freeze twice.

On the device, `UserStatsRow.streakOn(today)` mirrors the rule. Between the missed day and
the next entry the streak isn't shown as 0 but as **held**: the flame in the header chips
turns into a snowflake. The Journal's week strip marks frozen days with a ❄, the Me screen
shows the count, and the toast after the entry that spends a freeze says so. `freezesOn()`
copies `freezes_on()`; change both or neither.

### XP and levels

`profiles.level` and `profiles.xp` existed from the first migration, but nothing ever wrote
them, so everyone stayed level 1 and `purchase_item`'s `level_required` check locked Wild
Rose (level 3) and Bright Poppy (level 5) for good. `20260925120000_xp_ladder` makes levels
real:

- **What earns XP.** Days journaled, not volume: **10 XP** for the day's first entry and
  **2** for each extra one, still bounded by the daily cap. "First of the day" counts
  soft-deleted entries, the same way the cap and the streak bonus do, so deleting and
  rewriting can't earn the 10 again. Deleting refunds nothing. A replayed submit returns
  before any of this, so XP is paid once.
- **The curve.** `level = floor(sqrt(xp / 10 + 1))`, so level *n* starts at `10·(n² − 1)`
  XP: 30, 80, 150, 240, 350 … Journaling once a day, that's level 2 in about 3 days and
  level 5 (the highest the catalog asks for) in 3–4 weeks. There's no cap.
- **Level follows XP by trigger.** `profiles_sync_level` sets `level` from
  `level_for_xp(xp)` on every XP write, whether from the RPC, the backfill, or the admin
  dashboard. Only an XP write fires it, so an admin can still set `level` by hand for
  testing.
- **Stored per entry.** `gratitude_entries.xp_awarded` sits next to `coins_awarded`. The
  submit RPC returns the entry row, so the app learns what an entry earned with no
  signature change.
- **Backfilled.** Existing accounts got XP for their past entries by the same rule.

On the device, `Levels.kt` mirrors the curve (`levelForXp()` copies `level_for_xp()`;
change both or neither) for the Me screen's progress bar ("20 / 50 XP to level 3"). The
toast after an entry adds "+10 XP". When the synced level passes the last one celebrated
on this device, the Garden shows a level-up dialog naming any seeds it unlocked, with a
"Visit the shop" button. The last celebrated level is kept per user in a local DataStore
(`LevelPreferences`), so a level reached by an entry the outbox delivered in the
background still gets its moment the next time the Garden opens. The first level a device
sees for an account is recorded silently.

### Backdrops

A backdrop is the scenery behind the garden: a painted scene in a band above the plot. The
server side was there from the first migrations. `purchase_item` sells backdrops like any
item, `set_active_backdrop` equips one the user owns (it's the only non-admin write path to
`gardens.active_backdrop_item_id`), and signup gives every account the starter Cottage
Meadow and equips it. Only the app was missing.

| Backdrop | Level | Price |
| --- | --- | --- |
| Cottage Meadow | 1 | starter, not for sale |
| Misty Forest | 2 | 160 |
| Cherry Grove | 3 | 180 |
| Quiet Shore | 4 | 200 |
| Desert Sunset | 5 | 220 |

- **One per level.** Cherry Grove was seeded at level 3 with a one-week sale window, which
  closed in June, and had since been edited to level 1. `20260928120000_backdrop_ladder`
  makes it permanent at level 3, so levels 2–5 each unlock one scene. The app doesn't
  mirror `available_from`/`available_until` yet, so seasonal items need that first.
- **Buying doesn't equip.** Each RPC does one thing: a bought backdrop's card switches to
  "Use", the way a bought seed switches to "Plant". There's no unequip; switching back to
  the starter is how you go plain.

On the device, the equipped id rides along with the garden row into Room, so the scene
shows offline and on a cold start. `BackdropScenes.kt` draws each scene from Canvas shapes,
picked by slug like the plant sprites. An unknown or missing slug falls back to Cottage
Meadow. The Shop's Backdrops tab has one card per row with a preview, locked by level and
disabled offline. The Garden's band gets only the height the plot leaves (the screen doesn't
scroll), up to 72dp. When the offline banner needs the room, the band hides rather than
squash. Level-ups announce new backdrops alongside seeds.

### Daily streak reminders

The app sends a **daily reminder notification** to help users keep their streak alive.

- **One-time opt-in prompt:** after a user's **first gratitude entry**, a one-time dialog
  asks whether they'd like daily reminders. It's shown **exactly once per account** — the
  seen-flag lives in Supabase (`user_settings.notif_prompt_seen`), so it never re-appears
  on reinstall or a new device. Accepting requests the Android 13+ `POST_NOTIFICATIONS`
  permission and schedules the reminder.
- **Notification settings (top of the Me screen):** an enable/disable **switch** and a
  **time-of-day picker**. If notifications are off for the app at the OS level, the card
  explains this and links to system settings.
- **Only when it's needed.** The reminder is about the streak, so on a day something has
  already been written it stays quiet (and still re-arms for tomorrow). It can be answered
  without opening the app; see [quick replies](#home-screen-widget-and-quick-replies).
- **Scheduling — `AlarmManager`.** `ReminderScheduler` sets the daily alarm;
  `ReminderReceiver` re-arms for the next day, then posts the notification. Because
  AlarmManager alarms are cleared on reboot, `BootReceiver` re-arms on `BOOT_COMPLETED`
  and `MY_PACKAGE_REPLACED`. The alarm is deliberately **inexact**
  (`setAndAllowWhileIdle`): it still wakes the device from Doze but may arrive a few
  minutes late, so the app needs no `SCHEDULE_EXACT_ALARM` permission, which Play reviews
  and Android 14+ denies to new installs by default. AlarmManager was kept over WorkManager on
  purpose (roadmap 0.8): WorkManager is throttled hardest for rarely-opened apps, the users
  a nudge is for, and it declares `RECEIVE_BOOT_COMPLETED` itself anyway.
- **Persistence split:** reminder **preferences** (on/off + time) are stored on-device via
  DataStore; only the once-per-user **prompt flag** is stored in Supabase. Backend change:
  migration `add_notif_prompt_seen_to_user_settings`.

### Home-screen widget and quick replies

Roadmap 1.2: the daily loop without opening the app. Both lean on the offline journal
(1.1): the widget draws from Room, so it's instant and works offline, and a reply is
written like any entry, Room first and then the outbox.

- **The widget.** The garden as it stands (backdrop and plants, painted by the same code
  as the Garden screen), the streak with the flame or, when a freeze is holding it, the
  snowflake, "N thoughts left today", and **Write**. It lays itself out by its real size:
  narrow is the streak and Write, wide puts the garden beside them, tall above. Signed
  out, it asks you to sign in.
- **Write** (the widget's button, or tapping the reminder) opens the app on the Garden
  with the entry sheet up. It waits through sign-in if needed, and isn't replayed by a
  rotation or a relaunch from Recents.
- **Reply inline.** "Plant a thought" on the reminder takes the text right there. It is
  saved and, when there's a connection, planted within a few seconds; the reminder is then
  replaced by what the Garden would have said ("+5 coins · +10 XP · a kind thought
  planted 🌱", or "Saved in your journal…" offline). If it can't be saved (the day's cap,
  signed out), the text is shown back so it isn't lost. A level-up is still celebrated the
  next time the Garden opens.
- **Later** puts the reminder off for about an hour (the alarm is inexact, like the daily
  one). It's skipped if something is written in the meantime, and dropped on sign-out.

On the device, `WidgetSync` holds the widget's state for the life of the process. Room's
invalidation tracker tells it when one of the widget's tables changes (a submit, a sync, a
sign-out), and it pushes the widget only when the state actually differs. "Today" also
moves without the database noticing, so `WidgetMidnight` re-reads just after local
midnight (a non-waking alarm) and the receiver re-reads on a clock or time zone change.
`GardenSnapshot` paints the garden into one bitmap of at most 150k pixels, since it
reaches the launcher over Binder, shared by every layout. Nothing runs unless a widget
exists. `ReplyReceiver` waits up to three seconds for the session's token and four for
delivery, which keeps it inside a receiver's ten; anything slower is left to
`OutboxWorker`.

### Entry photos

Roadmap 1.4, first slice. An entry can carry one photo, chosen from the gallery (the system
photo picker, so no storage permission) or taken with the camera (the camera app writes into
a file the app shares through a `FileProvider`, so no camera permission either). It can be
added, replaced or removed later from **Edit thought**. Text is still what makes an entry: a
photo changes nothing about coins, XP, the cap or the streak.

- **What's stored.** `PhotoPreparer` decodes the pick with `ImageDecoder`, which applies the
  EXIF orientation, scales the long edge down to 1600 px and re-encodes a JPEG at quality 82:
  a few hundred KB. Re-encoding drops every EXIF tag, **the location included**. Nothing
  else about the original leaves the phone.
- **Where.** The private bucket `entry-photos`, at `{user_id}/{entry_id}/{photo_id}.jpg`,
  and the same relative path under `files/photos/` on the device. `photo_id` is new each
  time a photo is set, so a replacement never takes the old one's name, and an upload sent
  twice (with upsert) writes the same bytes to the same name. The bucket accepts JPEG only,
  up to 3 MB.
- **Offline, like the text.** A photo is copied into place with its entry and goes out as
  its own outbox op (`PHOTO`) after the submit, so a slow upload never holds up the reward.
  The op uploads the file, calls `set_entry_photo`, then removes anything else in the
  entry's folder; every step is safe to repeat, so a replay cleans up after itself. Two
  photo changes made while offline fold into one. If the server refuses the change (the
  entry was deleted elsewhere), the upload is deleted again. With no local file (a retried
  entry whose photo came from another device) there's nothing to upload, and
  `set_entry_photo` confirms the server already has it.
- **Reading.** Photos from another device, or from before a reinstall, download the first
  time they're shown (`downloadAuthenticated`) and are kept. Offline, a photo that was never
  downloaded shows a placeholder. Files nothing refers to any more (replaced, removed, or
  deleted with their entry, here or elsewhere) are pruned after each sync. The folder is
  left out of backups and emptied on sign-out, like the database.
- **Server checks** (`20260929120000_entry_photos`). `set_entry_photo` only takes a path in
  the caller's own folder for that entry, and only once the file is really there; a check
  constraint pins the shape. `delete_gratitude_entry` clears the path.

**Deleting goes through the app.** SQL can't delete a storage object (Supabase's
`protect_delete` trigger refuses, because the file would be left behind), so neither a
cascade nor an RPC can. The app does it through the Storage API, and `own_photo_objects`
tells it what to delete: the replaced photo after a change, the entry's folder after a
delete, and every file the user has before their account is deleted.

**Storage budget.** On the Free tier the project has 1 GB of storage, roughly 2–3k photos
at this size. One more reason a real launch probably wants Pro.

**Known limits**, accepted for now:

- **A failed account deletion can still lose the photos.** They have to go first (Supabase
  won't delete a user who owns files), so if the final `delete_current_user` call then
  fails, the account survives without them and its entries show a placeholder where each
  photo was. The user had asked for everything to be deleted, and trying again finishes
  the job. Avoiding it entirely would need the deletion to run server-side, e.g. an Edge
  Function with the service key.
- **Two devices changing one entry's photo at the same moment** can each delete the
  other's upload in their cleanup step, leaving the entry pointing at a missing file (a
  placeholder everywhere). It takes near-simultaneous changes to the same entry from two
  phones. Setting the photo again fixes it.

### Entry moods

Roadmap 1.4, second slice. An entry can carry a mood, one of five in order: **rough, low,
okay, good, great** (stored as 1–5). It's optional: the entry sheet asks "How are you
feeling?" under the text, nothing is picked until a face is tapped, and tapping the picked
one again takes it off. It can be set, changed or cleared later from **Edit thought**, and
shows in the Journal as a small face and its word next to the time. The notification's
quick reply saves with none.

- **Why a scale, not labels.** Five ordered points can be averaged and trended, which is
  what 1.5's insights need (mood against streak length, a year's mood); a set of named
  feelings could only be counted.
- **No reward.** Like a photo, a mood changes nothing about coins, XP, the cap or the
  streak. Text is what makes an entry.
- **It travels with the text** (`20260930120000_entry_moods`). `submit_gratitude_entry`
  takes `p_mood` when the entry is written, and `edit_gratitude_entry` now always sets text
  and mood together: an edit sends the entry's whole state, null meaning no mood, so there's
  no "unchanged" to get wrong and a replayed or folded edit lands the same. Offline, the
  mood rides on the same `SUBMIT` / `EDIT` outbox op as the text and folds with it; a
  retried entry goes out with its mood as it is now. Unlike photos there's no separate op:
  it's one small value.
- **Server checks.** A check constraint keeps the column to 1–5 or null. Only those two RPCs
  write it; there's still no owner UPDATE policy on `gratitude_entries`. Both functions
  gained a parameter, so each was dropped and created again rather than replaced (an old
  overload would stay reachable through PostgREST).
- **Accessibility.** The five faces are a radio group: each reads "Mood: good", with its
  selected state. In the Journal the face and word read as one phrase, "Feeling good".

### Prompts, days and lists

Roadmap 1.4, the last slice. All of it is on the device: no migration and no RPC change.

- **A question a day** (`model/Prompts.kt`). The entry sheet shows one of 30 questions
  above the text ("Who helped you today, even in a small way?"), and **Another** steps to
  the next. The question is picked by date, so the reminder shows the same one: under its
  text when expanded, and as the label of its reply field. It's only a nudge. It isn't saved
  with the entry and pays nothing, so leaving it unanswered costs nothing.
- **One day at a time.** Several entries a day already worked (the server's daily cap,
  10 by default, with the first paying more XP), and the Journal already grouped them by
  day. Tapping a day's heading, or a day with a check in the week strip, now opens that day
  by itself, oldest thought first, with each entry's mood and photo. Entries open the same
  Edit / Delete menu as in the list. For today, while thoughts are left, **Write another**
  goes to the Garden with the entry sheet open. Today also takes an entry the server dated
  tomorrow after a zone change, as the list does.
- **Lists stay plain text** (`util/EntryLists.kt`). A line starting `• `, `- ` or `* ` is a
  list item, shown as a bullet with a hanging indent; the text itself is never rewritten, so
  an entry reads the same in the reply notification, a future export or the admin
  dashboard. Enter at the end of an item starts the next one, Enter on an empty item ends
  the list, and the **• List** button under the field makes the current line an item or a
  plain line again. Newlines already survived the trip: Postgres's `trim()` only strips
  spaces, and the reward counts collapsed whitespace, so lines earn nothing extra.
- **The text box grows** from 96 dp to 240 dp with what's written, then scrolls, in both
  the entry sheet and Edit; the whole box takes a tap.
- **A scrub fix found on the way.** `scrub()` cut a quoted row at the first line starting
  `URL:`. A multi-line entry can have one of its own, and the rest of its text would have
  gone to Sentry. It now cuts to the last one, which is supabase-kt's. `ScrubTest` has the
  case.

### Crash reports and the funnel

Roadmap 0.6. Two halves, chosen so the app collects as little as it can.

**Crash reports go to Sentry** (`util/Diagnostics.kt`), started first thing in the
`Application`. Crashes and ANRs are automatic. Two kinds of caught error are reported too: a
failure a screen shows as its fallback copy (`toUserMessage`), unless it was a lost
connection or one of the server errors raised on purpose; and an outbox op the server
refuses, other than the daily cap or an entry deleted elsewhere, which the Journal already
explains. Each event's `environment` is the flavor and build type (`consumer-release`).

- **Off without a DSN.** `SENTRY_DSN` in `local.properties` turns it on; without it nothing
  is initialised and every call is a no-op. Sentry's own startup provider is switched off in
  the manifest, so it can't start without the scrubbing below.
- **Nothing that names the person.** No account ID is attached (Sentry's `user.id` is a
  random install ID), `sendDefaultPii` stays off so the SDK sends no IP (the server side is
  below), and there are no screenshots or view hierarchies. Only `sentry-android-core` is
  included: no NDK, and no session replay, which would record the screen.
- **Scrubbed on the device.** `beforeSend` and `beforeBreadcrumb` pass every message through
  `scrub()`, because the raw text isn't safe: supabase-kt's exceptions carry the request URL
  and a `Headers:` line (API key and the user's token), and when Postgres rejects a row it
  quotes the row, **journal text included** (`Failing row contains (…)`). Removed: header
  lines, quoted rows and key values, tokens, URL query strings (Postgrest filters carry IDs),
  UUIDs (user, entry and photo IDs; photo paths are made of them) and email addresses.
  `ScrubTest` covers each one with the real message shapes.
- **The Gradle plugin does only the mapping.** It stamps each release build with the ID of
  its R8 mapping, so traces deobfuscate, and uploads the mapping when `SENTRY_AUTH_TOKEN` is
  set. Its bytecode instrumentation (network, database, file I/O, logcat) is off: those would
  capture request URLs and log lines.
- **In the Sentry project settings** (Security & Privacy), three things, none optional:
  *Prevent Storing of IP Addresses*, the server-side data scrubber, and an **Advanced Data
  Scrubbing rule: Remove · Anything · `$user.geo.**`**. Even with `sendDefaultPii` off,
  Sentry reads the connection's IP at ingest and derives a city from it; the IP setting drops
  the IP but keeps the city (a known Sentry limitation, getsentry/sentry#92201). Both test
  events on 2026-10-02 came in with "US, Powell", the second after the IP setting was on;
  the third, after the rule (dataset Errors / Transactions / Attachments), had none. Nothing
  on the client can stop it.
- **Mapping upload needs an organization auth token** (`sntrys_…`, Settings → Auth Tokens),
  which also carries the EU region. A token that's set but rejected fails the release build;
  with none set, the upload is skipped.

**The funnel is SQL** (`20261001120000_signup_funnel`), built from data the server already
has, so the app sends no analytics events and there's nothing new to declare:

```sql
select * from analytics.signup_funnel;    -- by signup week
select * from analytics.signup_journeys;  -- one row per person
```

Signed up → wrote an entry → wrote on day 1 → wrote on day 2 → wrote on day 7, with day 1
being the local date of signup in the user's time zone. "Came back" means *wrote* again,
not opened the app. The rates count only people whose day 2 or day 7 is already over, so a
week-old cohort isn't read as churned. Admins (today, the test accounts) are left out. The
views live in an `analytics` schema that PostgREST doesn't serve, and no API role has any
grant on it; read them from the dashboard's SQL editor.

### Account deletion

Users can delete their own account, and deletion is *complete* — it removes the login and
wipes every associated row.

- **Self-service delete (Me screen):** an understated red **"Delete account"** button under
  *Log out* opens a **two-step confirmation** — an "are you sure, this can't be undone"
  warning, then a **password prompt**. The password is re-verified (a wrong password aborts
  before anything is deleted). On success the session clears and the app returns to login.
- **Complete wipe:** deletion removes the `auth.users` row, and the existing
  `ON DELETE CASCADE` foreign keys erase everything owned by the account (profile,
  settings, stats, wallet, entries, garden + plants, inventory).
- **Photos first.** Supabase refuses to delete a user who still owns storage objects, and
  SQL can't delete them, so before calling `delete_current_user` the app lists the user's
  files (`own_photo_objects`, asked again until none are left, since one answer holds at
  most 1000) and deletes them through the Storage API. If that fails partway, the account
  is still there and the user sees the error; see [Entry photos](#entry-photos) for what a
  failure after this step costs.
- **Admin dashboard "Danger zone":** admins get a real *Delete entire account* action per
  user (via `admin_delete_user`). **Known gap:** it fails for a user who has photos, since
  an admin can't list or delete another user's files. Staging-only, so it doesn't block
  release; the fix is an admin storage policy plus an admin variant of `own_photo_objects`.
- **Backend (`db/account_deletion.sql`):** two `SECURITY DEFINER` RPCs —
  `delete_current_user()` (self) and `admin_delete_user(uuid)` (admin-gated by
  `is_current_user_admin()`). Both pin `search_path` and are executable only by
  `authenticated`. Consistent with the project's model: **no service-role key ever ships
  in the app.**

- **On the web, without the app:** Play requires a public deletion URL, so
  `site/delete-account.html` does the same three steps in the browser with supabase-js:
  sign in, delete the photos, `delete_current_user`. It keeps no session in the browser.
  Someone who can't sign in (the app has no password reset yet) is sent to the contact
  address instead. See [Privacy policy and Data Safety](#privacy-policy-and-data-safety).
- **Deleted elsewhere, still on a phone:** an app signed in to an account deleted on the
  web keeps showing its cached garden. Its token stays valid for up to an hour after the
  user is gone, and offline-first deliberately keeps sessions alive. The page tells people
  to log out of or uninstall the app; noticing the missing user is a possible follow-up.

### Privacy policy and Data Safety

Roadmap 0.4. Play needs a hosted privacy policy, a public account-deletion URL and an
accurate Data Safety form.

- **The pages** are plain HTML in `site/` (no build step) and are published by GitHub Pages
  from the public `gratitude-garden-site` repo, because this repo is private. `site/` here is
  the source: commit a change, then run `.\scripts\publish-site.ps1`, which copies it over
  the public repo and pushes. Edit here, not on GitHub; the script refuses to publish over
  a commit it didn't make (see its help). The URLs live in `util/Links.kt` and in Play Console.
  - `privacy.html` is the policy. It is written from the code, so a change to what the app
    collects, where it goes or how long it stays means changing the policy in the same PR.
  - `delete-account.html` + `delete.js` delete an account without the app (see
    [Account deletion](#account-deletion)). supabase-js is pinned and loaded with an
    integrity hash; the URL and publishable key are the public ones the APK already holds.
  - The policy names Braedon Salisbury as the developer and GratitudeGardenApp@protonmail.com
    as the contact, on every page. Change them in all of `site/` together.
- **The Data Safety answers** are in `docs/play-data-safety.md`, one row per question with
  the reason, plus the App content answers and a pre-submission checklist.
- **In the app:** sign-up says "I've read the privacy policy." with the policy linked, and
  the box starts unticked (it used to come pre-ticked, which isn't consent). The Me screen
  links the policy under *Delete account*.
- **Deleting an entry erases its words.** `delete_gratitude_entry` used to only stamp
  `deleted_at`, so the text and mood stayed on the server until the account went. It now
  also sets the text to `(deleted)` and clears the mood
  (`20261004120000_scrub_deleted_entries`). The row stays because the daily cap, the streak
  and the funnel count deleted entries by day. It's a marker rather than null because
  `entry_text` is `not null` and every released APK reads it as a `String`. The app never
  shows a deleted row, and its sync leaves alone an entry with a pending or refused change,
  so the marker never replaces text a device still holds.
- **Breached passwords** (`util/PwnedPasswords.kt`). Supabase only checks Have I Been Pwned
  on Pro, so sign-up does it: only the first 5 hex characters of the password's SHA-1 are
  sent, and the match happens on the device. It fails open (offline, a 5 s timeout or an
  error skips it), which is acceptable only because this check protects users from reusing
  a leaked password; getting past it harms nobody else. The minimum length (8, with all
  four character classes) is the Supabase Auth setting, mirrored by `util/PasswordRules.kt`.

### Admin CRUD dashboard

> **Staging flavor only** (roadmap 0.3). None of this is compiled into the `consumer` build
> that ships to Play, where `AdminTools.AVAILABLE` is `false` and the Me-screen button never
> renders. RLS is still the actual guard; this just keeps the admin UI out of the store APK.

An **admin-only** surface demonstrating **Create / Read / Update / Delete for every one of
the nine tables** from inside the app. It is reached from a button at the bottom of the
**Me** screen that is **only rendered for admin accounts**. Two tabs:

- **Users** — pick a user (by display name + short UUID), then edit their `profiles`,
  `user_settings`, `user_stats`, `coin_wallets`, `gratitude_entries`, `gardens`,
  `user_inventory`, and `garden_plants` rows.
- **Items Catalog** — full CRUD over the global `items` table.

#### Security model (no service-role key)

**No service-role key is ever placed in the app.** Every admin request still uses the anon
key plus the signed-in user's JWT. Cross-user access is granted *only* by Row-Level
Security:

1. `profiles.is_admin boolean` flags admin accounts.
2. `public.is_current_user_admin()` — a `SECURITY DEFINER`, `STABLE` SQL function with a
   pinned `search_path` — returns whether `auth.uid()` is an admin. `SECURITY DEFINER`
   lets it read `profiles` without recursing back into the profiles RLS policy.
3. Each table gets an **additive, permissive** `FOR ALL` policy (`<table>_admin_all`) with
   `USING (public.is_current_user_admin())` and a matching `WITH CHECK`. Because these are
   permissive, they can only ever *grant extra* access to admins — they never widen what a
   normal user can do. Existing per-user policies are left untouched.

Verified live: an admin JWT sees all profiles; a non-admin JWT sees only its own row.

#### Database changes

Applied via the Supabase migration `admin_flag_and_policies`, checked into the repo at
**`db/admin_dashboard.sql`** (idempotent — safe to re-run):

- `ALTER TABLE public.profiles ADD COLUMN is_admin boolean NOT NULL DEFAULT false`
- `CREATE FUNCTION public.is_current_user_admin() … SECURITY DEFINER`
- `<table>_admin_all` policies on all nine tables
- A commented-out update showing how to grant an admin account

#### Android pieces

| File | Purpose |
| --- | --- |
| `data/AdminModels.kt` | `AdminTableSpec` / `AdminField` describing each table; `buildPayload()` turns editor strings into a typed `JsonObject`; `ADMIN_USER_TABLES` + `ADMIN_ITEMS_TABLE`. |
| `data/AdminRepository.kt` | Generic `rows / insert / update / delete` over `JsonObject`, plus `isCurrentUserAdmin()` and `users()`. |
| `ui/admin/AdminViewModel.kt` | `AdminUiState` (users, selected user, per-table rows, item options, garden id) and `create/save/delete` actions with friendly error mapping. |
| `ui/screens/AdminDashboardScreen.kt` | Mode tabs, user picker, and a data-driven editor that renders the right input per field type. |
| `data/GardenRepository.kt` | `ProfileRow` reads `is_admin`. |
| `ui/me/MeViewModel.kt` | `MeUiState.isAdmin` loaded from the profile. |
| `ui/screens/MeScreen.kt` | Admin-only **Admin Dashboard** button near the bottom. |
| `ui/GardenApp.kt` | `admin` route + nav callback passed into `MeScreen`. |
| `di/AppContainer.kt`, `ui/ViewModelExt.kt` | Expose `AdminRepository`. |

#### How the data-driven CRUD engine works

Rather than hand-coding nine forms, each table is described once as an `AdminTableSpec`
(its scope, primary key, whether the id is DB-generated, and a list of typed
`AdminField`s). Rows flow through the repository as plain `JsonObject`s.

- The screen renders an editor per field **by type**: text fields, number fields, `Switch`
  toggles for booleans, dropdowns for enums (using the real Supabase enum values), and item
  pickers for foreign-key columns (`item_id`, `active_backdrop_item_id`).
- On save, `AdminTableSpec.buildPayload()` rebuilds a correctly **typed** JSON body (ints
  as numbers, bools as booleans, blank-and-nullable → explicit JSON `null`,
  blank-and-required → omitted so existing values / DB defaults stand).
- The `ViewModel` injects owner columns the editor doesn't expose: `id`/`user_id` for
  user-scoped tables, and `garden_id` for `garden_plants` (resolved from the selected
  user's garden).
- Destructive deletes require an in-app confirmation dialog. For `gratitude_entries`, a
  soft delete is available by setting `deleted_at` instead of removing the row.

<details>
<summary>Walkthrough script</summary>

1. Show Supabase **Auth → Users** and the table schema.
2. Sign into the app as the **admin** account.
3. Open **Me** → the **Admin Dashboard** button is visible (hidden for non-admins).
4. **Users tab → select a user**, then demonstrate CRUD per section:
   - `profiles` — edit display name / level / xp / `is_admin` → **Save** (Update).
   - `user_settings`, `user_stats`, `coin_wallets`, `gardens` — single-row: **Save**,
     **Delete**, or **Create** the row if missing.
   - `gratitude_entries` — **Add** (Create), edit text/coins/date (Update), **Delete**
     (or soft-delete via `deleted_at`).
   - `user_inventory` — assign an item to the user (Create) / remove it (Delete).
   - `garden_plants` — place a plant via the item picker + grid/stage/health (Create),
     edit (Update), remove (Delete).
5. **Items Catalog tab** — create / edit / delete a catalog `items` row.
6. Show the corresponding rows changing in the Supabase table editor.
7. Sign into a **non-admin** account and show the Admin Dashboard button is absent.

</details>

---

## Setup & build

1. **Android SDK** — set `sdk.dir` in `local.properties` (created automatically by
   Android Studio), e.g. `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`.
2. **Supabase keys** — add to the same `local.properties` (gitignored):
   ```properties
   SUPABASE_URL=https://wllqgdjkkhztbefdvvsf.supabase.co
   SUPABASE_ANON_KEY=<your anon key>
   ```
   These are surfaced to code as `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.
   Optionally, crash reports (see [Crash reports and the funnel](#crash-reports-and-the-funnel)):
   ```properties
   SENTRY_DSN=<the project's DSN>        # without it, nothing is reported
   SENTRY_ORG=<org slug>                 # these three upload each release's R8 mapping
   SENTRY_PROJECT=<project slug>
   SENTRY_AUTH_TOKEN=<org auth token>
   ```
3. **Build and test:**
   ```bash
   ./gradlew assembleStagingDebug      # build the debug APK (staging flavor)
   ./gradlew installStagingDebug       # install on a connected device/emulator
   ./gradlew testStagingDebugUnitTest  # unit tests
   ./gradlew connectedCheck            # instrumented UI tests, every flavor (needs a device)
   ```

   There are two **product flavors**. `consumer` is the build that ships to Play. `staging` is
   the same app plus the admin dashboard, which lives only in `app/src/staging`. It installs
   next to the consumer app as `com.gratitudegarden.app.staging`. `main` reaches the dashboard
   through `ui/admin/AdminTools`, a same-named object in each flavor. The consumer version is
   an empty stub, so no admin code is compiled into the store APK.

> `local.properties` is gitignored on purpose — keys are never committed.

**Release builds** are R8-minified and signed with the Play upload key. The keystore lives
outside the repo; point at it from `local.properties` (or the same names as environment
variables):

```properties
UPLOAD_STORE_FILE=C:/path/to/gratitude-garden-upload.jks   # forward slashes
UPLOAD_STORE_PASSWORD=...
UPLOAD_KEY_ALIAS=upload
UPLOAD_KEY_PASSWORD=...
```

```bash
./gradlew bundleConsumerRelease    # the AAB for Play: app/build/outputs/bundle/consumerRelease/
./gradlew assembleConsumerRelease  # an installable APK of the same build, for walkthroughs
```

Without those properties release still builds, just unsigned. Only ever upload `consumer`;
`staging` carries the admin dashboard. Crash stack traces from release are obfuscated. With the Sentry
token set, each build's mapping is uploaded and Sentry shows real names; either way, keep
`app/build/outputs/mapping/consumerRelease/mapping.txt` for every build that gets uploaded,
to retrace Play Console's traces by hand. **Back up the keystore and its password**: losing them means
never shipping an update under this application ID again.

---

## Run on an emulator

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-emulator.ps1
```

That boots the virtual device, installs the debug build, and opens the app — one command
from a cold machine to the login screen. It is safe to re-run: if an emulator is already
running it is reused rather than starting a second one.

The script finds the SDK itself (`ANDROID_HOME` → `ANDROID_SDK_ROOT` → `sdk.dir` from
`local.properties`), so nothing needs to be on your `PATH`. It also points `JAVA_HOME` at
Android Studio's bundled JBR 21 when `JAVA_HOME` is unset, because
`gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to Java 21.

| Flag | Effect |
| --- | --- |
| `-Consumer` | Install and open the consumer flavor (the Play build, no admin tools) instead of staging. |
| `-Avd <name>` | Boot a different AVD (default `Medium_Phone`). |
| `-ColdBoot` | Ignore the saved snapshot and boot from scratch — fixes a wedged device. |
| `-SkipInstall` | Don't rebuild or reinstall; just launch what's on the device. |
| `-NoLaunch` | Leave the emulator running without starting the app. |
| `-Screenshot <path>` | Save a PNG of the screen once the app is up. |

**The AVD.** Any image at or above API 28 works (`minSdk = 28`). The one used here is
`Medium_Phone` — API 37, x86_64, Play Store. To create another, use Android Studio's
**Device Manager**; the `avdmanager` CLI lives in the optional *Android SDK Command-line
Tools* package, which isn't required for any of the above. `emulator -list-avds` shows what
you already have.

Doing it by hand instead:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone   # boot
.\gradlew.bat installStagingDebug                                           # install
adb shell am start -n com.gratitudegarden.app.staging/com.gratitudegarden.app.MainActivity
```

> **Behind a VPN, the emulator can't reach Supabase.** The UI renders fine and you can
> browse the pre-auth screens, but signing in fails with a request timeout. Disconnect the
> VPN or use a physical device — see [`docs/perf-nfr-journal.md`](docs/perf-nfr-journal.md),
> which hit the same wall during profiling.

**Give the AVD 4 GB of RAM.** A Play Store system image spends most of its memory on Google
services, so on a 2 GB device the debug build thrashes during startup and Android kills it
before it draws a frame:

```
E ActivityManager: ANR in com.gratitudegarden.app
  Reason: Process ... failed to complete startup
```

Set it in **Device Manager → Edit → Show Advanced Settings → RAM** (or `hw.ramSize=4096` in
the AVD's `config.ini`), then cold boot — a snapshot saved at the old size is not reusable.
Measured on this project: 2 GB left ~180 MB free and the app either ANR'd or took 16 s to
appear; 4 GB leaves ~3 GB free and it appears in under 3 s. The script warns when free
memory is low, and `-ColdBoot` clears a device that has accumulated junk.

With a device attached, `./gradlew connectedCheck` runs the instrumented tests — all four
pass. They don't need Supabase; they only exercise pre-auth screens and the new-entry sheet
in isolation.

The UI tests drive the app through `testTag`s rather than on-screen text, so they survive
copy changes. `GgTextField` and `PillButton` take an optional `testTag` that is applied to
the node carrying the real semantics — the inner `BasicTextField` and the clickable button
face — rather than to the outer layout wrapper, which would have no text-input or enabled
state to assert against.

---

## Granting / changing admins

Current admins are `Admin Demo`, `reminderTest1`, and `TestAdmin`, the account used for
emulator walkthroughs. Its credentials live only in the gitignored `local.properties`
(`TEST_ADMIN_EMAIL` / `TEST_ADMIN_PASSWORD`), never in the repo. Admin only does
anything in the `staging` flavor. To change who is an admin, run in the Supabase SQL editor:

```sql
select id, display_name, is_admin from public.profiles;          -- find the id
update public.profiles set is_admin = true  where id = '<uuid>';  -- promote
update public.profiles set is_admin = false where id = '<uuid>';  -- demote
```

Admins can also flip the `is_admin` toggle on any user from the dashboard's **Profile**
section. The change takes effect the next time that user's Me screen loads.
