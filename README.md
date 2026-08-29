# Dad's Victory

An Android app to help one dad permanently quit **vaping/nicotine** and **alcohol**.

> **You don't have to be perfect. You just have to keep choosing freedom.**

It is a coach, a progress tracker, a Bible-based encouragement app and — most
importantly — an emergency companion for the ten minutes when a craving is at its
worst. It never shames him for a slip. The whole design follows one rule:

> *"One bad moment does not erase your progress. Get back up and win the next decision."*

---

## Health and safety, up front

**This app is not a doctor and says so on the first screen and throughout.**

- **Nicotine is highly addictive.** Stopping can cause cravings, irritability,
  anxiety, restlessness, poor concentration, disturbed sleep and increased
  appetite. Research indicates these generally ease with time.
- **Alcohol withdrawal can be dangerous.** If you drink heavily or every day,
  stopping suddenly can sometimes cause dangerous withdrawal. **Speak with a
  doctor or healthcare professional before stopping abruptly.** During setup, the
  app shows a prominent "Before you stop" screen when the answers suggest heavy or
  daily drinking — it prompts for advice, it does **not** diagnose anything.
- **Emergency:** if you or someone else has severe confusion, seizures,
  hallucinations, difficulty staying conscious, severe vomiting, or another medical
  emergency, seek emergency medical help immediately.

Every health statement in the app carries a named source, and where a figure is
quoted the app also states the period and the population it describes. Where the
science is unsettled — the long-term effects of vaping, for instance — the app
says so rather than inventing certainty. Sources include CDC, NHS, UK Chief
Medical Officers, NIAAA, NIDA, IARC and the 2020 Surgeon General's report; the
full list is on the in-app **Sources & health information** screen.

---

## Getting the APK

The APK is built by GitHub Actions, because it needs the Android SDK.

1. Go to the repository's **Actions** tab.
2. Open the most recent **Build Dad's Victory APK** run for this branch.
3. Download the **DadsVictory-apk** artifact and unzip it to get `DadsVictory.apk`.

### Installing it on the phone

This is a debug-signed APK installed outside the Play Store, so Android will ask
for permission once:

1. Copy `DadsVictory.apk` to the phone (email, cable, Google Drive, whatever is easiest).
2. Open it with the phone's **Files** app.
3. Android will say installing from this source is not allowed. Tap **Settings**,
   then turn on **Allow from this source** for the app you opened it with.
4. Go back and tap **Install**.
5. On first launch, allow notifications when asked — the three daily
   encouragements will not arrive otherwise.

To update later, install the new APK over the top. Data is kept as long as the
APK is signed with the same key (every CI build is, since they all use Android's
standard debug key).

### Building it yourself

Needs JDK 17 and the Android SDK (Android Studio installs both):

```bash
./gradlew :app:assembleDebug
# APK appears at app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in Android Studio and press Run.

---

## Permissions, and why each one is there

The app asks for **two** permissions, and nothing else:

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | The three daily encouragements. Requested on Android 13+ on first launch. Declining it costs you the daily messages; everything else still works. |
| `RECEIVE_BOOT_COMPLETED` | Alarms are wiped when a phone restarts. Without this, notifications would silently stop after the first reboot. |

**There is deliberately no `INTERNET` permission.** The app has no networking code
at all, and without that permission Android would refuse to let it send anything
anywhere even if it tried. That is what turns the privacy promise into something
enforced by the operating system rather than something you have to take on trust.

Things that might look like they need a permission, but do not:

- **Opening a support website** hands the URL to your browser.
- **Calling a helpline** uses `ACTION_DIAL`, which only *opens* the dialler with
  the number filled in. The app can never place a call itself.
- **Choosing a family photo** goes through the Android photo picker.
- **Saving or restoring a backup** goes through the system file picker.

---

## Privacy

- No account, no sign-in, no cloud.
- No advertising, no tracking, no analytics — and no code that could add them
  without also adding the internet permission.
- Journal entries, check-ins, health information and the family photo are stored
  only in the app's private storage, which other apps cannot read.
- Automatic cloud backup and phone-to-phone transfer are both switched **off**
  (`allowBackup="false"` plus `data_extraction_rules.xml`).
- The journal can be locked with a PIN. The PIN is never stored — only a salted
  PBKDF2-SHA256 hash of it — so there is no back door, including for us.
- **Export/import**: the only way data leaves the device. It produces one file
  encrypted with AES-256-GCM using a key derived from your passphrase via PBKDF2
  (210,000 iterations). Lose the passphrase and the file is gone; the app says so
  before you create one.
- **Delete everything** in Settings removes the database, the preferences and the
  photo, leaving the app exactly as it was on first install.

---

## What's in it

**Onboarding** — welcome, what you're quitting, where you are (drives which
health service and emergency numbers you see; UK by default), when to begin
(today or a custom date and time, past or future), why you're doing it, what you
were spending, how much you were vaping, how much you were drinking, the alcohol
safety screen where relevant, and your notification times. The flow adapts: quit
alcohol only and you never see the vaping questions.

**Home** — a time-aware greeting, the big streak number, the craving button,
separate nicotine and alcohol cards, money saved, cravings defeated, health days,
your savings goal, today's verse, and a way into everything else.

**Craving emergency mode** — the most important screen. A ten-minute countdown
with a message that changes as it runs, then six steps: ten slow breaths with an
animated breathing circle (4 in, 2 hold, 6 out), a glass of water, move, *your*
reason with your own photo and messages, a verse, and then the only real decision:
**I BEAT THE CRAVING** or **I STILL NEED HELP** — which opens the country-specific
help screen rather than a lecture.

**Progress** — current streak, longest streak, total free days, cravings
defeated, days on the journey, slips (tracked separately, never as a mark against
you), seven-day charts of mood, cravings and stress, and a sourced health
milestone timeline.

**Faith** — verse of the day, eight themes (strength, perseverance, self-control,
hope, courage, peace, renewal, not giving up), 40 verses in two public-domain
translations, and favourites.

**Everything else** — Today's Victory Plan (customisable, resets daily), a private
lockable journal, achievements including six you tick yourself because the app
cannot honestly detect them, a trigger tracker that generates a specific plan for
each trigger you pick, the daily check-in, money and savings goals, "Why quit?",
sources, and the crisis help screen.

**The slip system** — thank you for being honest → what happened → what was going
on → what you still learned, showing the streak you built and the best streak that
is *still yours* → **I'M STARTING AGAIN NOW**. Best streaks and earned badges are
never taken away. There is a badge for getting back up, because that is the one
most people never give themselves.

---

## How it's built

Kotlin · Jetpack Compose · Material 3 · MVVM · Room · DataStore · AlarmManager
`minSdk 26` (Android 8.0) · `targetSdk 35` · offline-first, no network code at all.

```
app/src/main/java/com/dadsvictory/
├── domain/          Pure Kotlin. No android.* or androidx.* imports at all.
│   ├── Streaks.kt   Streak arithmetic
│   ├── Money.kt     Savings estimates, parsing and formatting
│   ├── Achievements.kt
│   ├── NotificationSchedule.kt
│   ├── backup/      Encrypted backup format
│   └── content/     Motivation, scripture, sourced facts, milestones, support
├── data/            Room, DataStore, photo storage, repository
├── notifications/   AlarmManager scheduling, receivers, channels
└── ui/              Compose screens, theme, components
tools/logic-verify/  A JVM-only Gradle build that compiles and tests domain/
```

### Why AlarmManager rather than WorkManager

The three notifications need to arrive at a time of day the user chose, and
WorkManager's windows drift. They use `setAndAllowWhileIdle`, which survives Doze
and does **not** require the `SCHEDULE_EXACT_ALARM` permission — that permission is
meant for alarm-clock-grade timing, and an encouragement arriving a few minutes
late is fine. Each firing schedules the next one, a boot receiver rebuilds them
after a restart, and a timezone receiver keeps "8am" meaning 8am after a flight.

### Testing

The entire `domain` package is free of Android imports on purpose, which means the
logic that matters most can be compiled and unit-tested on a plain JVM in seconds,
with no emulator:

```bash
./gradlew -p tools/logic-verify test
```

**118 tests** currently cover streak arithmetic (including midnight rollover,
daylight saving, timezone changes, future start dates and a clock wound
backwards), money estimates and currency parsing/formatting, achievement rules
(including the rule that a slip never takes a badge away), the content rotation
engine, notification scheduling across both clock changes, the encrypted backup
round trip (wrong passphrase, tampered file, not-a-backup file), and the content
itself — that every fact resolves to a real source, that every quantified figure
names its period and population, that the UK experience never shows a US emergency
number, and that no motivational message anywhere uses shaming language.

The build fails if anything in `domain/` ever starts importing Android, so that
guarantee cannot quietly rot.

CI runs those tests, then builds the APK.

---

## Licensing of the Bible text

Passages are from the **World English Bible** (public domain worldwide) and the
**King James Version** (public domain in the United States; Crown copyright in the
UK, which permits short quotations of the kind used here). Only short passages are
included — no complete copyrighted translation is bundled. This note is also shown
in the app.

---

## A note on the numbers

Money saved, "vapes avoided" and "drinks avoided" are **estimates** built from the
figures entered during setup, and the app labels them as such everywhere they
appear. The app deliberately does **not** estimate nicotine intake in milligrams:
it cannot know that, and a fabricated health number would be worse than none.

Streaks count completed 24-hour periods rather than calendar dates, so the number
cannot jump around when the clocks change or you travel.
