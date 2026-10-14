# Play Store listing

Roadmap 0.7. What goes into Play Console → *Grow users → Store presence → Main store
listing*, ready to paste. The graphics are in `docs/store/`: the icon and the feature
graphic come from `scripts/draw-brand.py`, and the screenshots from the emulator.

Keep the copy about **journalling**. Mood is declared in Data Safety as part of the
journal, not as health information (`docs/play-data-safety.md` → *Is mood "Health info"?*).
Pitching the app as mental-health tracking would change that answer and could bring in
Play's health-app policy.

## App name (30 characters max)

```
Gratitude Garden
```

## Short description (80 characters max)

```
Write one kind thought a day and watch a garden grow from your gratitude.
```

## Full description (4,000 characters max)

```
Gratitude Garden is a gratitude journal that grows a garden as you write.

Each day, write down something you're thankful for. Every thought earns coins, and coins buy seeds, water and scenery for your own little garden. Keep writing and watch it fill with tulips, sunflowers, lavender and more.

WRITE YOUR WAY
• A gentle question each day, if you'd like a nudge: "Who helped you today, even in a small way?"
• Type, or tap the microphone and say it.
• Add a photo to remember the moment.
• Note how the day felt, from rough to great.
• Make lists, write several thoughts a day, and edit any of them later.

GROW YOUR GARDEN
• Plant seeds, water them, and watch them grow from sprout to bloom.
• Move plants around and arrange the garden however you like.
• Level up as you keep writing to unlock new flowers and backdrops.
• Keep a streak going. Missed a day? A streak freeze forgives it, and you get one free every month.

ALWAYS WITH YOU
• A home-screen widget shows your garden and streak, with a button to write.
• A daily reminder at the time you choose. Reply from the notification without opening the app.
• Works offline. Write on a plane or underground; your thoughts sync when you're back online.
• Look back by day, with each thought's photo and feeling.

YOUR JOURNAL IS YOURS
• No ads, and no advertising or analytics trackers.
• Export your whole journal, photos included, whenever you like.
• Delete your account and everything in it from the app or the web, at any time.
• Photos are stripped of location data before they leave your phone.

One kind word a day grows a garden.
```

## Category and contact

| Field | Value |
| --- | --- |
| App or game | App |
| Category | Lifestyle |
| Tags | Journal, Self-care (pick from Play's list; both fit without a health claim) |
| Email | GratitudeGardenApp@protonmail.com |
| Website | https://braedons-cse.github.io/gratitude-garden-site/ |
| Privacy policy | the policy URL (see `docs/play-data-safety.md`) |

## Graphics

| Asset | File | Play's rule |
| --- | --- | --- |
| App icon | `docs/store/icon-512.png` | 512×512 PNG, Play rounds the corners |
| Feature graphic | `docs/store/feature-graphic.png` | 1024×500, no text near the edges |
| Phone screenshots | `docs/store/screenshots/` | 2–8, 320–3840 px a side, the long side at most twice the short |

Regenerate the icon, feature graphic and framed screenshots after changing the plant in
`GgSprites.kt` or a capture: `python scripts/draw-brand.py` (needs Pillow).

## Screenshots

Six, in this order, each framed at 1080×1920 with its caption (the captions live in
`CAPTIONS` in `scripts/draw-brand.py`). Play takes 9:16 and wants at least four that size
for its recommendations; a raw 1080×2400 capture is refused, being more than 2:1.

| File | Shows | Caption |
| --- | --- | --- |
| `01-garden.png` | the Garden, Cherry Grove backdrop, plants at every stage | Every kind thought grows your garden |
| `02-write.png` | the entry sheet: question, text, mood, photo | Type it, say it, or add a photo |
| `03-journal.png` | the Journal: week strip, a list entry, moods, coins | Look back on the little things |
| `04-shop.png` | Shop → Backdrops | Spend your coins on seeds and scenery |
| `05-level-up.png` | the level-up dialog | Level up and unlock new flowers |
| `06-me.png` | Me: level, stats, freezes, reminder, export | Reminders, streak freezes and your journal to keep |

**How they were taken** (2026-10-14), to redo them after a UI change:

- **The account** is `gg-demo@example.com`, display name Maya, signed up in the app; its
  password is `DEMO_PASSWORD` in `local.properties`. Its history was written with SQL on the
  live database (after a rolled-back dry run): 15 entries over 1–13 October with moods and
  two lists, 134 XP (level 3), 86 coins, a 13-day streak with one freeze, 12 plants from
  seedling to mature, and Cherry Grove bought and in use. Its time zone is New York. The
  streak and "yesterday" are relative to the day it was seeded, so a retake on a later day
  needs the dates moved forward first.
- **The build** is the signed consumer release (no admin button, the real icon), on the
  `Medium_Phone` emulator at 1080×2400.
- **The status bar** is in demo mode: `adb shell settings put global sysui_demo_allowed 1`,
  then `am broadcast -a com.android.systemui.demo -e command …` with `enter`,
  `clock -e hhmm 0900`, `battery -e level 100 -e plugged false`,
  `network -e wifi show -e level 4 -e fully true`, `network -e mobile hide`,
  `notifications -e visible false`, and `exit` afterwards. The emulator's own shield icon
  survives demo mode, so it's painted over in the bar's colour.
- Captures go to `docs/store/screenshots/raw/` under the names above, then
  `scripts/draw-brand.py` frames them.
