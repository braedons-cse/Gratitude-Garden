# Gratitude Garden

An Android (Kotlin + Jetpack Compose) gratitude-journaling game. Users write daily
gratitude entries, earn coins, and grow a personal garden. The backend is **Supabase**
(Auth + Postgres/PostgREST), accessed from the app through the official Supabase Kotlin
client using the **anon key** only.

Gratitude Garden started as a course project for **CSE 5236** and is now being taken
toward a public release on **Google Play**. The feature set below is complete and
working; the [roadmap](#roadmap-to-google-play) is the plan for turning it into a
shippable product.

| | |
| --- | --- |
| Status | Feature-complete course build; pre-release |
| `applicationId` | `com.cse5236.gratitudegarden` — **must be renamed before first publish** (see [0.1](#tier-0--release-blockers)) |
| Version | `versionCode 1` / `versionName 1.0` — never published |
| Min / target SDK | 28 / 36 |
| Release build | Unsigned, `isMinifyEnabled = false` — not yet shippable (see [0.2](#tier-0--release-blockers)) |

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
  - [Daily streak reminders](#daily-streak-reminders)
  - [Account deletion](#account-deletion)
  - [Admin CRUD dashboard](#admin-crud-dashboard)
- [Setup & build](#setup--build)
- [Granting / changing admins](#granting--changing-admins)

---

## Roadmap to Google Play

Sizes: **S** = a session, **M** = a few sessions, **L** = a multi-week arc.

### Tier 0 — Release blockers

Cannot publish without these. Most are unglamorous, and several are fine for a class demo
but disqualifying on a public store listing.

| # | Item | Size | Why it blocks |
| --- | --- | --- | --- |
| 0.1 | **Rebrand off the course namespace** | M | `applicationId` and the whole `com.cse5236.*` package tree are baked into the manifest and every test. The application ID is **permanent once published**. Pick the real name first, then rename in one pass — this only gets more expensive with every feature added. |
| 0.2 | **Signing, minification, real release build** | S/M | No signing config exists and R8 is off. Needs an upload keystore (stored outside the repo and **backed up** — losing it means never updating the app again), Play App Signing enrollment, R8 with keep rules for the Supabase/Ktor/kotlinx-serialization models, and an AAB we actually install and walk before uploading. Serialization + R8 is the classic first-crash-in-production combo. |
| 0.3 | **Get the admin dashboard out of the consumer build** | S | `AdminDashboardScreen.kt` is a generic CRUD editor over nine tables. RLS is the real guard, but shipping the client-side admin surface to every user is unnecessary attack surface and a reviewer red flag. Preference: a `staging` flavor, so we keep the tooling without shipping it. |
| 0.4 | **Privacy policy, Data Safety form, account-deletion URL** | M | Play requires all three, and this app trips several categories at once: email + password, free-text personal reflections, microphone, notifications. The deletion *backend* already exists (`deleteOwnAccount` + RPCs); the **publicly reachable web page** for deletion requests and the hosted policy do not. |
| 0.5 | **Harden secrets and key handling** | S | ✅ **Done** — two coin-minting holes closed, then the policy audit found three more (a privilege escalation among them) and closed those too; see [Server-authoritative economy](#server-authoritative-economy) and [Client write surface](#client-write-surface). |
| 0.6 | **Crash reporting and basic analytics** | S | Zero production visibility today. Without Crashlytics (or Sentry) plus Play Vitals we learn about an ANR from a one-star review. Add crash reporting and a small funnel (signup completed, first entry, day-2 return) *before* there are users to lose. |
| 0.7 | **Store listing assets** | M | Feature graphic, 4–8 phone screenshots (ideally a short video), 512px icon, short + full description, content rating questionnaire. The launcher icon is still the Android Studio template — that alone reads as "unfinished" in search results. Design work, routinely underestimated. |
| 0.8 | **Notification & alarm permission posture** | S | We hold `SCHEDULE_EXACT_ALARM` with a documented inexact fallback. Play scrutinizes exact alarms and a daily journaling nudge is unlikely to qualify for an exemption. Move the reminder fully to WorkManager and drop the permission (and `RECEIVE_BOOT_COMPLETED` with it). Fewer sensitive permissions = smoother review. |

### Tier 1 — Retention features

A gratitude journal lives or dies on day-7 retention. Roughly ordered by expected impact
per unit of work.

- **1.1 Offline-first with a local database — L — *highest impact*.** Every read in
  `GardenRepository` is a live PostgREST call: no network means no journal, no garden, no
  writing an entry. For a habit app that is fatal — people journal in bed, on planes, on
  the subway. Add Room as the source of truth, render from it instantly, sync to Supabase
  in the background with a queue and conflict resolution. Also kills the loading spinners.
  Do this **first** in this tier: it gets harder with every feature layered onto the
  current direct-to-network pattern.
- **1.2 Home-screen widget + richer notifications — M.** The fastest path to a daily habit
  is not opening the app. A Glance widget showing the streak, today's plant, and a one-tap
  "add entry" field puts the loop on the home screen; notification actions (inline reply,
  "remind me tonight") let an entry be logged without a cold start.
- **1.3 Make the garden feel like a game — L.** The loop today is write → coins → buy seed
  → place → water. Good skeleton, thin. In priority order: **streak insurance/freeze**
  (one forgiven day per month — retention gold and the cheapest item here), seasons and
  weather, rare and evolving plants, garden expansion, and animated milestone moments at
  7/30/100 days.
- **1.4 Entry experience beyond a text box — M.** Photos attached to an entry (Supabase
  Storage) — the most requested journaling feature; a mood tag per entry, which unlocks
  1.5; rotating daily prompts for the blank-page problem; multiple entries per day with a
  day-detail view; and lists/line breaks that survive a round trip.
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

1. **Foundation first:** 0.1 rebrand, 0.2 signing, 0.3 admin split. All three get strictly
   more painful the more code exists. Before any feature work.
2. **The structural bet:** 1.1 offline/Room. Everything after is easier with it in place;
   everything built before it has to be retrofitted.
3. **The retention loop:** 1.3 streak freeze (cheapest win on the list), 1.2 widget,
   1.4 photos + mood.
4. **Launch prep:** 0.4 privacy/Data Safety, 0.5 RLS audit, 0.6 crash reporting, 0.7 listing
   assets → ship to a closed track and get ~20 real testers before public release.
5. **Post-launch:** 1.5 insights, 2.1 privacy features, 2.3 monetization, 2.4 localization.

### Open decisions

These block or reshape the work above and should be settled before building.

- **What's the real package name / brand?** Blocks 0.1, and 0.1 blocks everything.
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
| Backend | Supabase Auth + PostgREST via `io.github.jan-tennert.supabase` (BOM `3.6.0`) over Ktor/OkHttp |
| Serialization | `kotlinx.serialization` (partial DTOs, `ignoreUnknownKeys = true`) |
| Local prefs | DataStore (reminder on/off + time-of-day) |
| Reminders | `AlarmManager` + `BootReceiver` (planned move to WorkManager — see 0.8) |

---

## Project structure

```
app/src/main/java/com/cse5236/gratitudegarden/
├─ GratitudeGardenApplication.kt   # builds AppContainer once per process
├─ MainActivity.kt
├─ di/
│  └─ AppContainer.kt              # SupabaseClient + repositories (manual DI)
├─ data/
│  ├─ GardenRepository.kt          # user-facing reads + RPC calls (auth, journal, garden, shop)
│  ├─ AdminModels.kt               # admin table-spec engine + payload builder
│  └─ AdminRepository.kt           # generic admin CRUD over JsonObject + admin check
├─ notifications/
│  ├─ ReminderScheduler.kt         # schedules/cancels the daily alarm
│  ├─ ReminderReceiver.kt          # fires -> posts the notification, re-arms
│  ├─ ReminderNotifications.kt     # channel + notification construction
│  ├─ ReminderPreferences.kt       # DataStore-backed on/off + time-of-day
│  └─ BootReceiver.kt              # re-arms after reboot / app update
├─ model/
├─ ui/
│  ├─ GardenApp.kt                 # top-level nav: auth flow vs HomeScaffold + routes
│  ├─ ViewModelExt.kt              # CreationExtras -> repositories
│  ├─ components/                  # BottomNav, PillButton, PgTextField, ...
│  ├─ theme/                       # Color.kt, Theme.kt, Type.kt (Caprasimo / Nunito)
│  ├─ sprites/                     # vector plant / icon drawing
│  ├─ garden/ shop/ journal/ me/   # feature ViewModels + UI state
│  ├─ admin/
│  │  └─ AdminViewModel.kt         # adminUiState + create/save/delete actions
│  └─ screens/
│     ├─ GardenScreen.kt ShopScreen.kt JournalScreen.kt MeScreen.kt
│     ├─ LoginScreen.kt SignUpScreen.kt
│     └─ AdminDashboardScreen.kt   # the admin dashboard UI
└─ util/                           # lifecycle logging helpers

supabase/migrations/                # source of truth for the schema (14 files)
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
  - `AdminRepository` — admin-only generic CRUD (see below).
- **ViewModels** expose a single immutable `UiState` via `StateFlow`. They are built
  by `viewModelFactory` blocks that pull the repository from the `Application` through
  `CreationExtras` extensions in `ViewModelExt.kt`.
- **Navigation** (`GardenApp.kt`): unauthenticated users see the `login`/`signup`
  graph; authenticated users land in `HomeScaffold`, whose `NavHost` holds the
  `garden`, `shop`, `journal`, `me`, and `admin` routes.

> Note for roadmap item 1.1: there is currently **no local cache**. Every screen reads
> straight from PostgREST, so the app is unusable offline. That is the single biggest
> structural change ahead of it.

---

## Supabase backend

Nine tables, all with **Row-Level Security enabled**:

`profiles`, `user_settings`, `user_stats`, `items`, `coin_wallets`,
`gratitude_entries`, `gardens`, `user_inventory`, `garden_plants`.

Enum columns (values used by the admin UI come straight from these):

| Column | Enum | Values |
| --- | --- | --- |
| `items.category` | `item_category` | `seed`, `decor`, `backdrop` |
| `items.rarity` | `item_rarity` | `common`, `uncommon`, `rare`, `epic`, `legendary` |
| `gratitude_entries.input_method` | `input_method` | `text`, `voice_to_text` |
| `garden_plants.growth_stage` | `growth_stage` | `seedling`, `sapling`, `mature` |
| `garden_plants.health` | `plant_health` | `healthy`, `thirsty`, `wilting` |
| `user_settings.theme` | *(text check)* | `light`, `dark`, `system` |

Normal users' RLS policies are scoped to `auth.uid()` (each user only ever sees/edits
their own rows). New accounts are provisioned by the `handle_new_user` trigger, and
gameplay mutations go through `SECURITY DEFINER` RPCs (`submit_gratitude_entry`,
`purchase_item`, `place_plant`, `water_plant`, …).

### Migrations

`supabase/migrations/` holds the complete schema history — every table, enum, RLS policy,
trigger, and RPC above, in the order it was applied. Replaying all fourteen files against an
empty project reproduces the backend exactly. The four files under `db/` are older
hand-written notes covering a subset of the same changes; `supabase/migrations/` is the
source of truth.

### Client write surface

After `20260916210000_lock_client_write_surface`, a **non-admin client can perform exactly
three kinds of write**, and every one of them is server-validated:

| Path | How |
| --- | --- |
| The gameplay RPCs | `submit_gratitude_entry`, `edit_gratitude_entry`, `delete_gratitude_entry`, `purchase_item`, `place_plant`, `move_plant`, `water_plant`, `set_active_backdrop`, `delete_current_user`, `mark_notif_prompt_seen` — all `SECURITY DEFINER`, all deriving the user from `auth.uid()` |
| `garden_plants` DELETE | `garden_plants_delete_own`, scoped through `gardens.user_id`. A DELETE is all-or-nothing, so there is no column subset to abuse |
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

The streak bonus is restricted to the day's first entry on purpose: `next_streak` returns
`greatest(prev_streak, 1)` when the date is unchanged, so a streak never advances within a
day, and paying the bonus on all ten allowed entries would multiply it tenfold. Net range is
**5–13** for the first entry of a day and **5–8** after that — verified by brute force across
600 lengths × 400 streak values.

Because `submit_gratitude_entry` now reads `user_stats` before the insert (the reward depends
on the streak and is stored on the row), it takes `coin_wallets FOR UPDATE` **first**, keeping
the `coin_wallets → user_stats` lock order that `water_plant` and `purchase_item` already use.
Acquiring them in the opposite order would open a same-user deadlock window.

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
- **Scheduling — `AlarmManager`.** `ReminderScheduler` sets the daily alarm;
  `ReminderReceiver` posts the notification and re-arms for the next day. Because
  AlarmManager alarms are cleared on reboot, `BootReceiver` re-arms on `BOOT_COMPLETED`
  and `MY_PACKAGE_REPLACED`. We check `canScheduleExactAlarms()` at runtime and fall back
  to an inexact (still Doze-proof) alarm when it's denied, so we avoid the Play-restricted
  `USE_EXACT_ALARM`. **Roadmap 0.8 replaces all of this with WorkManager** so the
  `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` permissions can be dropped before
  store review.
- **Persistence split:** reminder **preferences** (on/off + time) are stored on-device via
  DataStore; only the once-per-user **prompt flag** is stored in Supabase. Backend change:
  migration `add_notif_prompt_seen_to_user_settings`.

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
- **Admin dashboard "Danger zone":** admins get a real *Delete entire account* action per
  user (via `admin_delete_user`).
- **Backend (`db/account_deletion.sql`):** two `SECURITY DEFINER` RPCs —
  `delete_current_user()` (self) and `admin_delete_user(uuid)` (admin-gated by
  `is_current_user_admin()`). Both pin `search_path` and are executable only by
  `authenticated`. Consistent with the project's model: **no service-role key ever ships
  in the app.**

> Play also requires a **web-based** account-deletion request URL for apps with in-app
> accounts. The backend above satisfies the in-app half; the hosted page is roadmap 0.4.

### Admin CRUD dashboard

> **Roadmap 0.3:** this surface must be moved out of the consumer build (a `staging`
> flavor is the preferred option) before public release.

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
- A data update marking the demo admin account

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
<summary>Course demo script (CSE 5236 checkpoint 4)</summary>

1. Show Supabase **Auth → Users** (authentication evidence) and the table schema.
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
3. **Build and test:**
   ```bash
   ./gradlew assembleDebug      # build the debug APK
   ./gradlew installDebug       # install on a connected device/emulator
   ./gradlew testDebugUnitTest  # unit tests
   ./gradlew connectedCheck     # instrumented UI tests (needs a device/emulator)
   ```

> `local.properties` is gitignored on purpose — keys are never committed.

**Release builds are not yet configured.** There is no signing config and `isMinifyEnabled`
is `false`, so `assembleRelease` produces an unsigned, unminified artifact that must not be
uploaded anywhere. See roadmap [0.2](#tier-0--release-blockers).

---

## Granting / changing admins

The demo admin is the account with `display_name = 'Jeremy2'`. To change it, run in the
Supabase SQL editor:

```sql
select id, display_name, is_admin from public.profiles;          -- find the id
update public.profiles set is_admin = true  where id = '<uuid>';  -- promote
update public.profiles set is_admin = false where id = '<uuid>';  -- demote
```

Admins can also flip the `is_admin` toggle on any user from the dashboard's **Profile**
section. The change takes effect the next time that user's Me screen loads.
