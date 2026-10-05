# Play Console answers: Data safety and App content

What to enter in Play Console for roadmap 0.4, question by question, and why. It has to
match `site/privacy.html` and the code; when either changes, change this too. Written
2026-10-04 against the consumer build.

**URLs** (also in `util/Links.kt`):
- Privacy policy: `https://braedons-cse.github.io/gratitude-garden-site/privacy.html`
- Account deletion: `https://braedons-cse.github.io/gratitude-garden-site/delete-account.html`

## App content → Data safety

### Data collection and security

| Question | Answer | Why |
| --- | --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** | Account, journal, photos, crash data. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Supabase and Sentry are HTTPS only; target SDK 36 blocks cleartext and there is no `networkSecurityConfig` opting back in. |
| Which account creation methods does your app support? | **Username and password** (email + password) | The Google / Apple buttons on the login screen are stubs and sign in nobody. Remove them, or update this, before submitting. |
| Account deletion URL | the deletion URL above | Sign in, type DELETE, gone. |
| Do you provide a way for users to request that some or all of their data is deleted, without deleting their account? | **Yes** | Entries (text, mood, photo) can be deleted one by one in the app. |

### Data types

Everything below is **collected, not shared**. Supabase and Sentry are service providers
processing on our behalf, which Play's definition excludes from "sharing". Nothing is
processed ephemerally (it's all stored).

| Category → type | Collected | Required / optional | Purposes |
| --- | --- | --- | --- |
| Personal info → **Name** | Yes | Optional. A blank name falls back to the email's local part | App functionality, Account management |
| Personal info → **Email address** | Yes | Required | App functionality, Account management |
| Photos and videos → **Photos** | Yes | Optional | App functionality |
| App activity → **Other user-generated content** (entry text, mood) | Yes | Required (writing is the app) | App functionality, Analytics (the sign-up funnel counts entry days) |
| App activity → **Other actions** (garden: plants, purchases, streaks, XP) | Yes | Required | App functionality |
| App info and performance → **Crash logs** | Yes | Required (no opt-out today) | Analytics |
| App info and performance → **Diagnostics** (ANRs, unexpected handled errors) | Yes | Required | Analytics |
| Device or other IDs → **Device or other IDs** (Sentry's random installation ID) | Yes | Required | Analytics |

**Declared not collected, and why:**
- **Location.** No GPS or network location. The IANA time zone (`user_settings.time_zone`)
  is coarser than Play's approximate location (an area of 3 km² or more) and is used only to
  decide "today". Photos are re-encoded on the device with all EXIF stripped, GPS included.
- **Audio.** Voice entries use `SpeechRecognizer`; the system recognition service (usually
  Google's) receives the audio, and the app only ever gets text. The policy says so.
- **Financial info.** Coins are virtual and there is no Play Billing yet. Revisit with 2.3.
- **Health.** See the open call below.
- **Passwords.** Play has no password type. Supabase stores only a hash. The breach check
  sends a 5-character prefix of the password's SHA-1, which identifies no one.
- **App interactions / in-app search / web history.** No analytics SDK, no event tracking.

### Open call for the owner: is mood "Health info"?

Play's *Health and fitness → Health info* means "information about health, such as medical
records or symptoms". A 1–5 "how was your day" rating is a journal field, not a symptom
log, so the recommendation is to keep it under **Other user-generated content** with the
entry text. If the listing ever pitches the app as mental-health tracking, move mood to
Health info, and check whether the store's health-app policy then applies.

## Elsewhere in App content

| Section | Answer |
| --- | --- |
| Privacy policy | the policy URL above |
| Ads | **No ads** |
| App access | Everything is behind sign-in: give reviewers a test account (not `TestAdmin`, which is an admin and can read every journal) |
| Target audience | **13 and over** (the policy says it isn't meant for under-13s). Choosing under-13 brings in Families policy |
| Content rating | Questionnaire: no violence, no gambling (coins can't be bought or cashed out), users don't interact or share content with each other |
| Health apps declaration | Not a health app (see the mood call above) |

## Before submitting

- Check the developer name and contact on the pages (Braedon Salisbury,
  GratitudeGardenApp@protonmail.com) match the Play developer account.
- Confirm the Supabase project region in the dashboard (the policy says the United States;
  the database host resolves to AWS us-west-2).
- Confirm Sentry's *Prevent Storing of IP Addresses* and the `$user.geo.**` scrubbing rule
  are on in the production project (README → Crash reports and the funnel).
- Delete or demote `TestAdmin`. The policy says we don't browse journals; an admin account
  anyone could sign in to undermines that.
- Remove or wire up the Google / Apple buttons and "Forgot password?" on the login screen.
