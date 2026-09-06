# Class schedule — One UI 8.5 home screen widget

Mohammed Rabah Al-Harbi · Al-Ansar High School · summer timetable

## Run it (5 steps)

1. Android Studio → **Open** → pick this folder (not a subfolder).
2. When it asks, click **Trust Project**. It downloads Gradle and the SDK
   packages by itself; first sync takes a few minutes.
3. Plug in the S24 Ultra with USB debugging on → press **Run ▶**.
4. Long-press the home screen → **Widgets** → *Class schedule* → drag out the
   4×2. Resize taller to 4×3 or 4×4 for the full-day list.
5. Settings → Battery → *Class schedule* → **Unrestricted**. Without this,
   Samsung's Sleeping apps can swallow the minute tick and the countdown
   freezes mid-period.

No `local.properties` is included — Studio writes it with your own SDK path on
first open.

## What you edit later

Everything that changes lives in **one file**:
`app/src/main/java/com/mrabah/oneuischedule/data/ScheduleData.kt`

- `BellTimes` — arrival, assembly, and the seven periods.
- `Schedule.week` — which section you teach each period, and standby duty.

For the winter timetable, copy `BellTimes` to `WinterBellTimes` and switch the
reference rather than overwriting — you'll want to switch back in May.

## What's where

| Path | Role |
|---|---|
| `data/ScheduleData.kt` | Bell times, weekly duty table, state engine (no Android deps) |
| `widget/ScheduleWidget.kt` | Glance UI, theme, receiver, refresh alarms |
| `MainActivity.kt` | Full-week reference screen the widget taps into |
| `res/drawable/` + `drawable-night/` | Glass panel, hero card, rows, chips |
| `res/xml/schedule_widget_info.xml` | Sizes, resize limits, update period |

## Design decisions worth keeping

- The **class section** (`2/3`) is the largest element — you teach one subject
  to four sections, so the section is the answer to "where am I going".
- **حصة انتظار is labelled Standby**, styled as a dashed outline: assigned, but
  not teaching. A filled card would read as a lesson.
- **No bundled font.** The device's system font *is* One UI Sans — inheriting it
  is both correct and licence-clean.
- Refresh fires on **bell boundaries**, not a fixed cycle, so the card flips
  exactly when the bell rings. One-minute ticks only while a period is running.

## Daily widget styles

The widget picker also offers three visual treatments backed by the same live
schedule engine:

- **Luxury glass** — deep violet glass, gold focus, cyan upcoming rails.
- **Cards** — a bright, high-contrast agenda with one card per period.
- **Compact** — a 4×2 glance with the current period and the next one.

## Two platform limits, handled honestly

- Widgets can't cast real shadows → depth is faked with a 1dp top hairline and
  layered opacity.
- Widgets can't sample the wallpaper → no true blur. If you want it, turn on
  One UI's *transparent widget background* (Home screen settings) and set the
  `bg_widget_glass` solid to `#00000000`; the launcher blurs behind it.
