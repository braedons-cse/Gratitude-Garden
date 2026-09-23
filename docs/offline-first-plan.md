# Offline-first plan (roadmap 1.1)

Status: **plan, not started.** Written 2026-09-23. Decisions below were made with the
project owner; everything else is a default that can be revisited.

## Why now: what happens offline today

Checked on an emulator (airplane mode, cold start, signed-in account with a planted garden):

- **The garden looks wiped.** Every read in `GardenRepository` is a live PostgREST call, and
  a failed load leaves the UI at its defaults: "My Garden", 0 coins, 0-day streak, no
  plants. That is indistinguishable from a brand-new account.
- **It doesn't come back.** After reconnecting, the garden stayed empty through tab switches
  and 30 s of waiting. ViewModels reload only on `init` or on `repo.changes`, which fires
  only after a successful write. The user has to write an entry or restart the app.
- **Writing loses the text.** A failed submit closes the entry sheet; the typed thought is
  gone. The error is now a plain "can't reach the garden" message (commit `3813881`), but
  the words don't survive.
- **Auto-backup copies the session.** `allowBackup="true"` with the template
  `backup_rules.xml` backs up all app data, including supabase-kt's stored session (the
  refresh token). A Room database would be swept in too.

## Decisions

| Question | Decision |
| --- | --- |
| What works offline | **Journal**: write, edit, delete, read. **Everything else is read-only offline**: buy, plant, water, move and dig stay online-only with a visible "needs a connection" state. Coins stay server-authoritative (0.5); there is no locally predicted balance to reconcile. |
| Which day an offline entry counts for | **When it was written**, trusted within a **36-hour** window. An entry written at 11 PM and synced the next morning counts for the night it was written. Accepted side effect: anyone can log "yesterday" until mid-morning, a built-in grace period that overlaps 1.3's streak freeze. |

## Architecture

```
 Compose UI ──observes──▶ ViewModel ──observes──▶ Room (source of truth for reads)
                              │                       ▲
                              │ writes                │ refresh / sync results
                              ▼                       │
                         Repository ──enqueue──▶ outbox table ──▶ SyncWorker ──▶ Supabase RPCs
                              │                                        ▲
                              └── economy actions (online only) ───────┘
```

- **Room is the only thing the UI reads.** Repositories expose `Flow`s from Room. A refresh
  fetches from Supabase and writes into Room; the UI updates because Room emitted, never
  because a network call returned. A failed refresh changes nothing on screen: stale
  beats empty.
- **Refresh triggers:** app start, pull-to-refresh, connectivity regained, and after each
  successful sync. This alone fixes "the garden looks wiped" and "it doesn't come back".
- **Journal writes go through an outbox.** Submit, edit and delete write to Room immediately
  (the entry appears at once, marked pending) and append an operation to an `outbox`
  table. `SyncWorker` drains it in order.
- **`SyncWorker` runs on WorkManager** with a `CONNECTED` constraint, as unique work
  (`KEEP`). This is the job WorkManager is for: deferrable, must eventually happen,
  survives process death. (Contrast with 0.8, where a time-of-day nudge was the wrong fit.)
  Also attempt a sync immediately after each write when online, so the common case feels
  instant.
- **Economy actions stay direct RPCs**, gated on a `ConnectivityMonitor` `StateFlow`, and
  their results trigger a refresh into Room.

## Data model (Room)

Mirror the server rows the UI already uses; no new concepts beyond sync state.

| Entity | Source | Notes |
| --- | --- | --- |
| `EntryEntity` | `gratitude_entries` | `id` is a **client-generated UUID** (see idempotency). Adds `syncState` (`PENDING`, `SYNCED`, `FAILED`), `writtenAt`. `coinsAwarded` and `entryDate` are null until the server answers: the server decides both. |
| `OutboxOp` | local only | `SUBMIT` / `EDIT` / `DELETE`, `entryId`, payload, `attempts`, `lastError`, `createdAt`. |
| `ProfileEntity`, `StatsEntity`, `WalletEntity`, `SettingsEntity` | one row each | Replaced wholesale on refresh. |
| `GardenEntity`, `PlantEntity` | `gardens`, `garden_plants` | Replaced on refresh. |
| `ItemEntity`, `InventoryEntity` | `items`, `user_inventory` | Catalog plus ownership. |

**Outbox coalescing**, so a queue never replays something the user already undid:
an edit of a still-pending submit rewrites the submit's text; a delete of a still-pending
submit drops both and the entry never reaches the server.

**Journal paging:** a heavy user writes ~3,600 short rows a year, so read from Room with a
growing `LIMIT`, not Paging 3. The network side keeps the existing keyset pagination to
backfill history into Room.

## Server changes (one migration, phase 2)

1. **Idempotent submit.** `submit_gratitude_entry` takes `p_id uuid`. If a row with that id
   already exists for this user, return it unchanged: no second insert, no second reward.
   If it exists for a different user, raise an error. Without this, a request that reaches
   the server but whose response is lost gets retried and pays twice.
2. **Client write time, 36-hour window.** New `p_written_at timestamptz`. Trusted only
   within `[now() - 36h, now() + 5 min]`, otherwise `now()`. The entry day is the local
   date of that instant in `p_time_zone`.
3. **Drop the monotonic-day rule from `105b15c`.** `greatest(local date, last_entry_date)`
   can't coexist with legitimate backdating, since a synced offline entry is often older
   than the latest one. The zone-hopping bound it protected still holds another way: zones
   move the date by at most ±1 day, and backdating is capped at 36 h, so the most anyone
   can gain is using yesterday's unused cap. The cap is still per `entry_date` and still
   clamped at 50. The client's `entryDay()` mirror and its tomorrow tolerance change to
   match.
4. **Recompute the streak instead of incrementing it.** `next_streak(prev_date, …)` assumes
   entries arrive in date order, and out-of-order sync breaks that. Compute the current run
   from the user's distinct `entry_date`s (gaps-and-islands over the existing
   `(user_id, entry_date desc)` index); `longest_streak = greatest(longest, current)`.
5. **Idempotent delete.** `delete_gratitude_entry` on an already-deleted entry returns
   success instead of raising "entry not found", so a replayed delete isn't an error.
   Edit is already idempotent.
6. Drop `gratitude_entries.entry_date`'s leftover UTC column default. The RPC always sets
   the date explicitly, and a default that disagrees with the rules is a trap.

The same traps as every migration here apply: drop the old signature rather than
overloading it, then re-apply `revoke … from public, anon` and `grant … to authenticated`.

## Client-side pieces beyond Room

- **Auth while offline.** Today an offline launch stayed `Authenticated`, but supabase-kt
  also has `RefreshFailure`, and `GardenApp` treats anything but `Authenticated` as
  signed out. Treat `RefreshFailure` caused by the network as signed in (cached UI, writes
  queue) and `Initializing` as a splash, never the login form.
- **Sign-out wipes Room.** It's this user's journal on a possibly shared device. If the
  outbox isn't empty, warn with the count ("2 thoughts haven't synced yet") and offer
  *Sync now* / *Sign out anyway*.
- **Backups.** Exclude the Room database and the supabase-kt session store from
  `backup_rules.xml` and `data_extraction_rules.xml` (cloud backup and device transfer).
  After a restore the user signs in and the journal re-downloads.
- **At rest.** Room lives in app-private storage, which device encryption covers. SQLCipher
  is deferred to 2.1 (privacy), where it belongs alongside the E2E decision.
- **UI states.** Pending entries show a small "will sync" mark and no coin amount. Economy
  buttons show "Needs a connection" when offline. A slim offline banner sits on the Garden.
  `FAILED` ops (e.g. text rejected) surface on the entry with retry and discard.

## Phases

| Phase | Size | Delivers | Done when |
| --- | --- | --- | --- |
| **1. Read cache** | M | Room + entities, repositories write-through, ViewModels observe Flows, reconnect refresh, auth-state handling, backup exclusions | Airplane-mode cold start shows the real garden and journal; reconnecting refreshes without user action |
| **2. Offline journal** | M | Outbox, client UUIDs, `SyncWorker`, the server migration above, pending UI, sign-out guard | Entries written offline across midnight sync to the right days with correct streak and coins; replaying the queue twice changes nothing |
| **3. Polish** | S | Offline banner, economy disabled states, failed-op UI | Every screen behaves sensibly offline with nothing misleading |

Phase 1 is independently valuable: it fixes the wiped-garden bug even before any offline
writing exists, and it can ship alone.

## Testing

- **Repository tests against a fake remote** (roadmap Tier 3): refresh failure leaves Room
  untouched; outbox coalescing; replay idempotency.
- **Room migration tests** from the first schema version on (`exportSchema = true`).
- **SQL checks for the migration**, run as the test admin like the local-day change:
  duplicate `p_id` pays once; `p_written_at` outside the window falls back to now;
  out-of-order dates give the right streak; double delete succeeds.
- **Emulator walkthroughs in airplane mode**, as in this session: cold start, write offline,
  cross midnight (zone switch), reconnect, verify server rows.

## Dependencies

Room (`room-runtime`, `room-ktx`, `room-compiler` via KSP; the KSP version must match
Kotlin 2.2.10) and `work-runtime-ktx`. Both ship their own R8 consumer rules; re-walk the
signed release build after phase 1, as in 0.2.

## Open, deliberately deferred

- **Offline garden actions** (buying, planting, watering). Revisit only if users ask; it
  needs optimistic coins and rollback.
- **Hilt.** `AppContainer` can hold the database, a `ConnectivityMonitor` and WorkManager
  wiring for now. Tier 3 revisits DI once billing arrives too.
- **1.6 onboarding** (entries before signup) builds directly on phase 2's outbox: local
  entries with no user yet, attached on signup.
