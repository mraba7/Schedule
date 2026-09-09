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

## Illustrated designs (native rendering)

The **التصاميم** tab previews the actual Android renderer and lets you pin each
design separately: بطاقة يومك، المدار التقني، المجلة الهادئة، الوحدات الملونة،
المسار الليلي، وقت التركيز، المخطط الهندسي، الجدول الزجاجي. All prior widgets remain available.

- The eight layouts use a shared 360×360 coordinate space, the bundled Arabic
  font, and Canvas-rendered live data. These are not static concept images.
- Best reference size is a square widget (approximately 4×4). Non-square sizes
  preserve proportions with transparent padding rather than distorting text.
- Tap a widget to edit a date-and-period-specific note and preparation task.
  The visible task circle toggles completion; setting a different task clears
  completion. No sample preparation task is silently saved to user data.
- Active lessons show remaining minutes. Future days show the starting time;
  breaks and unassigned time are distinct. Alarms share the original updater.
- Removing the original widget no longer cancels updates for remaining widgets.
- `gradle test` includes native Android rendering across 8 designs × 6 states
  and exports PNGs to `app/build/design-previews`. These are real rendered
  previews, not a claim of physical One UI launcher verification.
- Branch builds are prereleases and point their tag at the build commit.

### Visual scope

The implementation follows the approved color palettes, hierarchy, and eight
distinct compositions. Dynamic labels, empty states, responsive padding and
accessible task hit areas necessarily differ from static sample drawings.
Use the exported previews and a device screenshot for final acceptance;
generated concept imagery is not a pixel-perfect screenshot specification.

- Widgets can't cast real shadows → depth is faked with a 1dp top hairline and
  layered opacity.
- Widgets can't sample the wallpaper → no true blur. If you want it, turn on
  One UI's *transparent widget background* (Home screen settings) and set the
  `bg_widget_glass` solid to `#00000000`; the launcher blurs behind it.

### Approved glass timetable

The glass timetable is listed first in the gallery. Muted class colors remain stable
across days, while champagne gold identifies the active lesson independently.
Upcoming rows include actual bell ranges, breaks and grouped consecutive free
periods up to the final assigned lesson. Times are isolated left-to-right.
The footer opens the schedule tab; the current lesson opens its notes.

### الحصة بوضوح and classroom progress notes

The new first gallery design prioritizes the written period name, start/end clocks
and minutes remaining, without a subject label. The next row includes its class.
Tap the widget to edit the focused class progress note (up to 1000 characters).
Notes persist by class until updated or completed. Saving arms one reminder for
the next actual meeting of that class after saving; holidays and overrides apply.
Notifications need notification permission; exact timing needs exact-alarm access.
Without it Android can delay the alarm. The note remains visible in the widget.
Class reminders work independently of optional school bell sounds and survive
reboots; schedule edits re-evaluate their next occurrence.

The focus widget now includes the displayed day’s total and all assigned period/class
tiles, with gold for the live lesson and muted completed lessons. Up to four tiles
fit per row; full teaching days use two rows. Counts distinguish the teacher’s
assigned lessons from school period numbers. Native previews include a full day.

### Responsive مسار الحصص

An independent widget reflows for compact, wide, balanced and tall allocations
using launcher-provided dp dimensions, not bitmap resolution. It fills its bounds
without square letterboxing. Completed nodes carry checkmarks, live nodes gold,
future nodes hollow. Larger layouts include class notes and the upcoming agenda.
It retains the current day after dismissal and explicitly labels breaks/free time.
Native render tests cover four allocations in live, break, free and completed states;
launcher sizing still needs physical device verification. Previous widgets remain.

### حصصي التفاعلية

A separate widget reveals start/end times by tapping a lesson tile. Only one tile
is expanded per widget; tapping it again closes it, switching tiles resets the
five-second deadline. A separate main-loop timer closes the card without holding the tap broadcast
open; an exact alarm backs it up when available. Expiration is also checked on every redraw. State is scoped by widget ID
and displayed date. Accessible native RemoteViews hit targets track the painted
tiles, including a second row. The lower note area retains classroom note editing.
Tests cover switching/expiry races, isolation, actual RemoteViews inflation and
expanded first/last tiles in four- and seven-lesson days.
