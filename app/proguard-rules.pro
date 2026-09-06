# The launcher and AlarmManager create these reflectively by class name.
-keep class com.mrabah.oneuischedule.widget.ScheduleWidgetReceiver { *; }
-keep class com.mrabah.oneuischedule.widget.ScheduleWidget { *; }
-keep class com.mrabah.oneuischedule.notify.PeriodAlarmReceiver { *; }
-keep class com.mrabah.oneuischedule.MainActivity { *; }

# Glance builds RemoteViews through generated layout lookups.
-keep class androidx.glance.appwidget.** { *; }
-dontwarn androidx.glance.**

# Bundled Arabic font files are referenced only through R.font
-keep class **.R$font { *; }
