# Non-UI unit tests & the optimizations they enabled

This document describes three **local JVM unit tests** (`app/src/test/…`, JUnit4, no
device/emulator) added for existing app logic, and the optimization made to each piece
of code under test. The tests run on the host in milliseconds:

```bash
./gradlew testDebugUnitTest
```

| # | Code under test | File | Test | Optimization |
|---|-----------------|------|------|--------------|
| 1 | `ReminderNotifications.pickMessageIndex` | `notifications/ReminderNotifications.kt` | `PickMessageIndexTest` | Allocation-free O(1) pick (no per-call `List`) |
| 2 | `UserStatsRow.effectiveStreakOn` | `data/GardenRepository.kt` | `EffectiveStreakTest` | Clock injected → deterministic & testable |
| 3 | `formatTime` | `util/TimeFormat.kt` | `FormatTimeTest` | Extracted to a pure util + locale bug fixed |

**Result:** 9 test methods across the 3 features, all passing (0 failures, 0 skipped).

A note on why these live in `src/test` and not `src/androidTest`: local unit tests load
the Kotlin **file-facade** class, whose static initializer runs any top-level `val`s in
that file. `GardenRepository.kt`'s only top-level member is a getter-only property (no
static init), so it is safe to test directly. `MeScreen.kt`, by contrast, has top-level
`44.dp` / `WheelVisibleCount` initializers that would pull Compose into a plain-JVM test —
which is exactly why optimization #3 moves `formatTime` out into its own pure file.

---

## 1. `pickMessageIndex` — allocation-free, same behavior

Picks the next daily-reminder message variant, never repeating the one shown last so the
notification doesn't read identically two days running.

**Before** — a throwaway `List` of candidate indices is built on every call:

```kotlin
fun pickMessageIndex(lastIndex: Int, random: Random = Random.Default): Int {
    if (MESSAGES.size <= 1) return 0
    val choices = MESSAGES.indices.filter { it != lastIndex }   // allocates a List each call
    return choices[random.nextInt(choices.size)]
}
```

**After** — draw uniformly from the `n − 1` non-repeating indices and hop over the gap:

```kotlin
fun pickMessageIndex(lastIndex: Int, random: Random = Random.Default): Int {
    val n = MESSAGES.size
    if (n <= 1) return 0
    if (lastIndex !in 0 until n) return random.nextInt(n)   // -1 "nothing yet" → any message
    val draw = random.nextInt(n - 1)
    return if (draw < lastIndex) draw else draw + 1
}
```

**Why it's equivalent.** The old code chooses uniformly among all indices `!= lastIndex`.
The new code draws `draw ∈ [0, n-2]` and maps it onto exactly that set: values below
`lastIndex` pass through, values at/above it shift up by one — so `lastIndex` is never
produced and every other index is equally likely. For an out-of-range `lastIndex`
(e.g. `-1`), both versions consider all `n` messages.

**Benefit.** No per-call heap allocation or Integer boxing; O(1) instead of O(n) filter.
Small in isolation, but this runs on every alarm fire from a cold-started broadcast
receiver, where avoiding garbage is worthwhile.

**Proof of preservation.** `PickMessageIndexTest` asserts *behavioral invariants* (never
repeats `lastIndex`, result always in range, every other message reachable, `-1` allows
all). The suite was run **green against the original implementation, then green again
after the rewrite** — the optimization changed the algorithm without changing behavior.

---

## 2. `effectiveStreakOn` — inject the clock for determinism

Decides whether a stored gratitude streak is still "alive". The stored `current_streak`
is only rewritten when an entry is submitted, so between submits it goes stale; a run is
only still alive while the last entry was **today or yesterday** (UTC), otherwise the
effective streak is 0.

**Before** — the function read the clock internally, so it was non-deterministic and
could not be unit-tested without depending on the day the suite happened to run:

```kotlin
val UserStatsRow.effectiveStreak: Int
    get() {
        val last = lastEntryDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return 0
        val today = LocalDate.now(ZoneOffset.UTC)   // hidden dependency on the system clock
        return if (last == today || last == today.minusDays(1)) currentStreak else 0
    }
```

**After** — the pure core takes `today` as a parameter; the property is a thin wrapper
that supplies the real date, so **every existing call site is unchanged**:

```kotlin
fun UserStatsRow.effectiveStreakOn(today: LocalDate): Int {
    val last = lastEntryDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return 0
    return if (last == today || last == today.minusDays(1)) currentStreak else 0
}

val UserStatsRow.effectiveStreak: Int
    get() = effectiveStreakOn(LocalDate.now(ZoneOffset.UTC))
```

**Benefit.** The clock is now an explicit input rather than a hidden side effect. The
logic is deterministic and testable in isolation, and the seam makes the today/yesterday
boundary auditable. Behavior is identical — the property reads the UTC date exactly as
before.

**Coverage.** `EffectiveStreakTest` pins a fixed `today` and checks: alive today, alive
yesterday, broken after a missed day, `null` date → 0, unparseable date → 0 (no crash),
and a future date → 0.

---

## 3. `formatTime` — extracted to a pure util, locale bug fixed

Formats a 24-hour `hour:minute` as a 12-hour clock string like `"8:00 PM"` for the daily
reminder time shown on the Me screen.

**Before** — a `private` function inside the ~750-line `MeScreen.kt` Compose file, using
the locale-default formatter:

```kotlin
private fun formatTime(hour: Int, minute: Int): String {
    val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    val amPm = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(h12, minute, amPm)   // uses Locale.getDefault()
}
```

**After** — moved to `util/TimeFormat.kt` (pure, host-testable) with the locale pinned:

```kotlin
internal fun formatTime(hour: Int, minute: Int): String {
    val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
}
```

**The bug.** `"%d".format(...)` formats with the JVM's default locale. Under a number
system such as Arabic-Indic (`ar-EG-u-nu-arab`), `%d` emits localized digits, so the
reminder time rendered as e.g. `٨:٠٠ PM` instead of `8:00 PM`. This is UI chrome, not
localized content, so `Locale.US` is pinned to always produce ASCII digits. The
12-hour / AM-PM arithmetic is untouched.

This was verified directly: under `ar-EG-u-nu-arab`, `String.format("%d:%02d %s", 8, 0,
"PM")` (old) and `String.format(Locale.US, …)` (new) produce **different** strings — the
old one uses non-ASCII digits, the new one stays `8:00 PM`.

**Benefit.** (a) Correct, locale-independent output on every device; (b) pure logic lifted
out of the Compose screen so it's unit-testable on the host and reusable.

**Coverage.** `FormatTimeTest` checks the boundaries (midnight → `12:00 AM`, noon →
`12:00 PM`, afternoon subtraction, zero-padded minutes, end of day) and — the key one —
`usesAsciiDigitsUnderNonLatinLocale`, which sets the default locale to Arabic-Indic and
asserts ASCII output. That test **fails against the original implementation** and passes
after the fix.
