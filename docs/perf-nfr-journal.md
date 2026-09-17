# Performance NFR — Journal screen (lazy evaluation + pagination)

**Non-functional requirement:** reduce CPU and/or memory use on one part of the app by
at least 5–10%, demonstrated with before/after profiler snapshots on the *same* screen.

**Result:** on the **Journal** screen, rendering jank dropped from **52.4% → 10.1%** of
frames (median frame time **30 ms → 14 ms**) and steady-state memory dropped **~19% total
PSS / ~28% Java heap**. Both far exceed the 5–10% target.

---

## What was slow, and why

The Journal renders the user's gratitude history. Two things made it expensive, and both
grow with history size:

1. **Eager UI composition.** `JournalScreen` laid the list out in a
   `Column(Modifier.verticalScroll())` and did `sections.forEach { entries.forEach { EntryCard() } }`.
   A scrollable `Column` composes, measures, and lays out **every** child up front — so all
   N entry cards (each drawing a `MaturePlant` Canvas sprite) were built even though only
   ~6 are on screen.
2. **Unbounded data fetch.** `GardenRepository.entries()` selected the user's **entire**
   `gratitude_entries` history with no limit, then filtered soft-deleted rows and sorted
   **in memory** on the client.

## The change

| Layer | File | Before | After |
|-------|------|--------|-------|
| UI (lazy eval) | `ui/screens/JournalScreen.kt` | `Column.verticalScroll` + nested `forEach` | `LazyColumn` with `item`/`items` — only visible cards are composed. Load-more is triggered by a `snapshotFlow` on the `LazyListState` when the user nears the end. |
| Data (pagination) | `data/GardenRepository.kt` | `entries()` — fetch all, filter + sort client-side | `entriesPage(limit, createdBefore)` — **server-side** order + `deleted_at is null` filter + `limit`; **keyset** pagination (`created_at < createdBefore`). Plus `recentEntryDates(7)`, a narrow query for the week-strip instead of scanning all history. |
| ViewModel | `ui/journal/JournalViewModel.kt` | one `load()` of everything | first page + `loadMore()` accumulation, with `endReached`/`loadingMore` state. |

`entries()` is retained for the Garden screen; only the Journal path was changed.

## Why it's faster

- **CPU:** per frame during a scroll, Compose now composes/measures/lays out ~10–15 visible
  cards instead of all 300. Far less main-thread work per frame → far fewer dropped frames.
- **Memory:** only visible cards (plus a small offscreen buffer) have live
  `LayoutNode`/modifier/text-layout objects, and only the pages loaded so far are held in the
  ViewModel — instead of 300 fully-composed cards and the whole history in memory.

---

## Methodology (reproducible)

- **Device:** motorola moto g play – 2024, Android 14 (API 34), 720×1600. Physical device
  (the emulator can't reach Supabase behind the dev machine's VPN).
- **Build:** same `debug` build for both runs; the only difference is the Journal
  optimization. (Absolute numbers would be lower in a release/R8 build; the relative delta
  holds.)
- **Dataset:** a throwaway account seeded with **300 entries** across 50 days (~6/day),
  so the list is long enough for the difference to be measurable.
- **Workload:** identical scripted scroll via adb — `input swipe 360 1250 360 450 220`
  ×14 (down) then ×6 (up). Frame stats are reset immediately before each run.
- **Tools (all adb / SDK, no GUI needed to capture):**
  - `dumpsys gfxinfo <pkg>` — rendering / main-thread cost (frame count, jank %, frame-time
    percentiles). Reset with `dumpsys gfxinfo <pkg> reset`.
  - `dumpsys meminfo <pkg>` — memory (PSS, Java/native heap), captured at steady state.
  - `am dumpheap <pkg> …` → `hprof-conv` — a heap snapshot importable into the
    **Android Studio Profiler** for the before/after memory screenshots.

---

## Results

### CPU / rendering — `dumpsys gfxinfo`, identical scripted scroll

| Metric | Before | After | Change |
|--------|-------:|------:|-------:|
| **Janky frames** | 162 / 309 = **52.43%** | 42 / 415 = **10.12%** | **−80.7%** (relative jank rate) |
| Frame time — 50th %ile (median) | 30 ms | 14 ms | **−53%** |
| Frame time — 90th %ile | 36 ms | 26 ms | −28% |
| Frame time — 95th %ile | 38 ms | 38 ms | ~0% |
| Frame time — 99th %ile | 42 ms | 77 ms | +83% (see caveat) |
| Slow UI-thread frames | 162 | 42 | −74% |
| Missed Vsync | 12 | 5 | −58% |

> Frame *counts* differ (309 vs 415) because the smoother "after" scroll renders more frames
> within the same gesture — so jank is compared as a **percentage**, which normalizes for that.

### Memory — `dumpsys meminfo`, steady state after the scroll

| Metric | Before | After | Change |
|--------|-------:|------:|-------:|
| **TOTAL PSS** | 151,315 KB | 122,188 KB | **−19.2%** |
| **Java Heap** | 34,056 KB | 24,416 KB | **−28.3%** |
| Native Heap | 20,236 KB | 10,828 KB | −46.5% |
| TOTAL RSS | 253,628 KB | 220,444 KB | −13.1% |
| Heap-dump size (retained-object proxy) | 71.9 MB | 55.3 MB | −23.1% |

Both the CPU (jank, median frame time) and memory (PSS, Java heap) improvements clear the
5–10% target by a wide margin.

### Honest caveats

- **99th-percentile frame time rose (42 → 77 ms).** This is an occasional single-frame hitch
  when a new page's 20 items are appended mid-scroll (a recomposition burst). It's past the
  95th percentile (i.e. rare), and a fair trade for halving the median and cutting jank ~80%.
  It could be smoothed by prefetching the next page earlier or appending in smaller chunks.
- **PSS is inherently noisy;** it was measured the same way (steady state, post-scroll) for
  both runs, and the Java-heap and heap-dump-size deltas corroborate the reduction.

---

## Artifacts & how to view the profiler snapshots

Files live under `profiling/` (heap dumps are git-ignored — too large to commit):

> These captures were taken before the 0.1 namespace rename, when the app was still
> `com.cse5236.gratitudegarden`. The package name in the committed `.txt` dumps was
> rewritten to `com.gratitudegarden.app` for consistency; the measurements themselves
> are untouched. The `.hprof` files are git-ignored and still carry the old name.

```
profiling/before/before-converted.hprof   ← open in Android Studio Profiler (memory)
profiling/before/before-gfxinfo.txt
profiling/before/before-meminfo.txt
profiling/after/after-converted.hprof     ← open in Android Studio Profiler (memory)
profiling/after/after-gfxinfo.txt
profiling/after/after-meminfo.txt
profiling/after/journal-after-bottom.png  ← shows pagination ("Loading more…") working
```

**To produce the before/after memory screenshots for the report:** in Android Studio,
*File ▸ Open* each `*-converted.hprof` (or drag it into an editor tab). The heap-dump viewer
opens; sort by **Retained Size** or search for `androidx.compose.ui.node.LayoutNode` and
compare the instance counts between the two dumps — the "after" dump holds far fewer, which
is the memory reduction made visible. Screenshot both.

**To reproduce the numbers**, re-run the workload with the commands in *Methodology* above
against the seeded account.
