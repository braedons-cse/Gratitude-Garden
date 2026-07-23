# Gratitude Garden 

An Android (Kotlin + Jetpack Compose) gratitude-journaling game for **CSE 5236**.
Users write daily gratitude entries, earn coins, and grow a personal garden.
The backend is **Supabase** (Auth + Postgres/PostgREST), accessed from the app
through the official Supabase Kotlin client using the **anon key** only.

---

## Latest changes — Accessibility (TalkBack screen-reader support)

The app now works with **TalkBack**, Android's built-in screen reader, so it is
usable by people who are blind or have low vision. This is the checkpoint's
**accessibility non-functional requirement (NFR)**.

- **Every icon-only control is labeled.** The password show/hide toggle, the
  sign-up back button, and the garden mic/record buttons now announce their
  action and current state (e.g. "Show password" ↔ "Hide password").
- **Interactive controls expose their role and state.** Bottom-nav items are
  announced as tabs with a "selected" state; the sign-up terms box is announced
  as a checkbox with a checked/unchecked state.
- **Informational visuals read as one phrase.** Each journal week-strip day is
  merged into a single label ("Monday, entry logged"), and garden plants
  announce their name and growth stage ("Tulip, mature").
- **Garden plants are now operable by screen reader.** Tapping a plant was a raw
  gesture TalkBack could not reach; a semantics click action was added so plants
  open with a TalkBack double-tap. Empty plots announce their grid position
  ("Empty plot, row 2, column 3").
- **Decorative icons stay silent.** Purely visual icons (stat flames, coins,
  plant sprites) expose no description, so TalkBack skips them instead of reading
  noise.

### Testing with TalkBack on a physical device

A physical Android phone is the most reliable way to verify this — an emulator
driven with a mouse does not emulate TalkBack's touch exploration well.

1. **Enable TalkBack:** Settings → Accessibility → TalkBack → toggle **On** and
   accept the prompt. (Holding **both volume keys for 3 seconds** toggles it on/off.)
2. **Learn the gestures** — touch behaves differently while TalkBack is on:
   - **Swipe right / left** — move to the next / previous element (read aloud).
   - **Double-tap anywhere** — activate the focused element.
   - **Two-finger swipe** — scroll.
3. **Walk the app** and confirm every control is announced meaningfully — nothing
   should read as "unlabeled":
   - **Login** — the password **eye** toggle → "Show password" / "Hide password".
   - **Sign-up** — the **back** button → "Back to login"; the **terms** box →
     "checkbox, not ticked / ticked".
   - **Bottom nav** — each tab reads its name and "selected" when active.
   - **Garden** — plants → "\<name\>, \<stage\>" and open on double-tap; empty
     plots → "Empty plot, row X, column Y"; the **mic** → "Record a gratitude note".
   - **Journal** — the week strip → "Monday, entry logged" / "…, no entry".
4. **Turn TalkBack off** the same way (Settings → Accessibility → TalkBack, or the
   volume-key shortcut).

---

## Previous update — Daily streak reminders

The app can now send a **daily reminder notification** to help users keep their
gratitude streak alive.

- **One-time opt-in prompt:** after a user's **first gratitude entry**, a one-time
  dialog asks whether they'd like daily reminders. It's shown **exactly once per
  account** — the seen-flag lives in Supabase (`user_settings.notif_prompt_seen`),
  so it never re-appears on reinstall or a new device. Accepting requests the
  Android 13+ `POST_NOTIFICATIONS` permission and schedules the reminder.
- **Notification settings (top of the Me screen):** an enable/disable **switch** and
  a **time-of-day picker** for when the daily nudge fires. If notifications are
  turned off for the app at the OS level, the card explains this and links to system
  settings.
- **Scheduling — WorkManager:** a self-chaining daily worker posts the reminder
  ("keep your streak — type or speak a gratitude entry") and re-arms for the next
  day. The schedule **survives device reboot automatically** (no BootReceiver
  needed); this is the main reason WorkManager was chosen over AlarmManager, whose
  alarms are cleared on reboot.
- **Persistence split:** reminder **preferences** (on/off + time) are stored
  **on-device** via DataStore; only the once-per-user **prompt flag** is stored in
  Supabase. Backend change: migration `add_notif_prompt_seen_to_user_settings` adds
  `user_settings.notif_prompt_seen boolean not null default false`.

---

## Previous update — Account deletion

Users can now **delete their own account**, and account deletion is now *complete*
— it removes the login and wipes every associated row.

- **Self-service delete (Me screen):** an understated red **"Delete account"**
  button under *Log out* opens a **two-step confirmation** — an "are you sure,
  this can't be undone" warning, then a **password prompt**. The password is
  re-verified (a wrong password aborts before anything is deleted). On success
  the session clears and the app returns to the login screen.
- **Complete wipe:** deletion now removes the `auth.users` row, and the existing
  `ON DELETE CASCADE` foreign keys erase everything owned by the account
  (profile, settings, stats, wallet, entries, garden + plants, inventory). The
  user can no longer log in and no data is left behind.
- **Admin dashboard "Danger zone":** admins get a real *Delete entire account*
  action per user (via `admin_delete_user`), replacing the old behaviour that
  only removed the `profiles` row and orphaned the login.
- **Backend (`db/account_deletion.sql`):** two `SECURITY DEFINER` RPCs —
  `delete_current_user()` (self) and `admin_delete_user(uuid)` (admin-gated by
  `is_current_user_admin()`). Both pin `search_path` and are executable only by
  `authenticated`. Consistent with the project's model: **no service-role key
  ever ships in the app.**

---

## Table of contents

- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [App architecture](#app-architecture)
- [Supabase backend](#supabase-backend)
- [Admin CRUD Dashboard (Checkpoint 4)](#admin-crud-dashboard-checkpoint-4)
  - [What it is](#what-it-is)
  - [Security model (no service-role key)](#security-model-no-service-role-key)
  - [Database changes](#database-changes)
  - [Android changes](#android-changes)
  - [How the data-driven CRUD engine works](#how-the-data-driven-crud-engine-works)
  - [Demo script for the grader](#demo-script-for-the-grader)
- [Setup & build](#setup--build)
- [Granting / changing admins](#granting--changing-admins)

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
db/
└─ admin_dashboard.sql             # version-controlled copy of the Supabase admin migration
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
  `garden`, `shop`, `journal`, `me`, and (new) `admin` routes.

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

---

## Admin CRUD Dashboard (Checkpoint 4)

### What it is

A focused, **admin-only** surface that demonstrates **Create / Read / Update / Delete
for every one of the nine tables** from inside the app. It is reached from a button at
the bottom of the **Me** screen that is **only rendered for admin accounts**.

The dashboard has two tabs:

- **Users** — pick a user (by display name + short UUID), then edit their
  `profiles`, `user_settings`, `user_stats`, `coin_wallets`, `gratitude_entries`,
  `gardens`, `user_inventory`, and `garden_plants` rows.
- **Items Catalog** — full CRUD over the global `items` table.

### Security model (no service-role key)

**No service-role key is ever placed in the app.** Every admin request still uses the
anon key plus the signed-in user's JWT. Cross-user access is granted *only* by
Row-Level Security:

1. `profiles.is_admin boolean` flags admin accounts.
2. `public.is_current_user_admin()` — a `SECURITY DEFINER`, `STABLE` SQL function with a
   pinned `search_path` — returns whether `auth.uid()` is an admin. `SECURITY DEFINER`
   lets it read `profiles` without recursing back into the profiles RLS policy.
3. Each table gets an **additive, permissive** `FOR ALL` policy
   (`<table>_admin_all`) with `USING (public.is_current_user_admin())` and a matching
   `WITH CHECK`. Because these are permissive, they can only ever *grant extra* access
   to admins — they never widen what a normal user can do. Existing per-user policies
   are left untouched.

This was verified live: an admin JWT sees all profiles; a non-admin JWT sees only its
own row.

### Database changes

Applied to the live project via the Supabase migration `admin_flag_and_policies`, and
checked into the repo at **`db/admin_dashboard.sql`** (idempotent — safe to re-run):

- `ALTER TABLE public.profiles ADD COLUMN is_admin boolean NOT NULL DEFAULT false`
- `CREATE FUNCTION public.is_current_user_admin() … SECURITY DEFINER`
- `<table>_admin_all` policies on all nine tables
- A data update marking the demo admin account

### Android changes

| File | Purpose |
| --- | --- |
| `data/AdminModels.kt` | `AdminTableSpec` / `AdminField` describing each table; `buildPayload()` turns editor strings into a typed `JsonObject`; `ADMIN_USER_TABLES` + `ADMIN_ITEMS_TABLE` definitions. |
| `data/AdminRepository.kt` | Generic `rows / insert / update / delete` over `JsonObject`, plus `isCurrentUserAdmin()` and `users()`. |
| `ui/admin/AdminViewModel.kt` | `AdminUiState` (users, selected user, per-table rows, item options, garden id) and `create/save/delete` actions with friendly error mapping. |
| `ui/screens/AdminDashboardScreen.kt` | The UI: mode tabs, user picker, and a data-driven editor that renders the right input per field type. |
| `data/GardenRepository.kt` | `ProfileRow` now reads `is_admin`. |
| `ui/me/MeViewModel.kt` | `MeUiState.isAdmin` loaded from the profile. |
| `ui/screens/MeScreen.kt` | Admin-only **Admin Dashboard** button near the bottom. |
| `ui/GardenApp.kt` | New `admin` route + nav callback passed into `MeScreen`. |
| `di/AppContainer.kt`, `ui/ViewModelExt.kt` | Expose `AdminRepository`. |

### How the data-driven CRUD engine works

Rather than hand-coding nine forms, each table is described once as an
`AdminTableSpec` (its scope, primary key, whether the id is DB-generated, and a list of
typed `AdminField`s). Rows flow through the repository as plain `JsonObject`s.

- The screen renders an editor per field **by type**: text fields, number fields,
  `Switch` toggles for booleans, dropdowns for enums (using the real Supabase enum
  values), and item pickers for foreign-key columns (`item_id`, `active_backdrop_item_id`).
- On save, `AdminTableSpec.buildPayload()` rebuilds a correctly **typed** JSON body
  (ints as numbers, bools as booleans, blank-and-nullable → explicit JSON `null`,
  blank-and-required → omitted so existing values / DB defaults stand).
- The `ViewModel` injects owner columns the editor doesn't expose:
  `id`/`user_id` for user-scoped tables, and `garden_id` for `garden_plants`
  (resolved from the selected user's garden).
- Destructive deletes require an in-app confirmation dialog. For `gratitude_entries`,
  a soft delete is available by setting `deleted_at` instead of removing the row.

### Demo script for the grader

1. Show Supabase **Auth → Users** (authentication evidence) and the table schema.
2. Sign into the app as the **admin** account.
3. Open **Me** → the **Admin Dashboard** button is visible (it is hidden for non-admins).
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

---

## Setup & build

1. **Android SDK** — set `sdk.dir` in `local.properties` (created automatically by
   Android Studio), e.g. `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`.
2. **Supabase keys** — add to the same `local.properties` (gitignored):
   ```properties
   SUPABASE_URL=https://aprqarsmrtosvzisvqhw.supabase.co
   SUPABASE_ANON_KEY=<your anon key>
   ```
   These are surfaced to code as `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.
3. **Build:**
   ```bash
   ./gradlew assembleDebug      # build the debug APK
   ./gradlew installDebug       # install on a connected device/emulator
   ```

> `local.properties` is gitignored on purpose — keys are never committed.

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
