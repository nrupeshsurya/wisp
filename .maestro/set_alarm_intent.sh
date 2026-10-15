#!/bin/sh
# What an assistant does for "set an alarm for 6:45 called Gym": the standard AlarmClock intent.
adb shell am start -a android.intent.action.SET_ALARM \
  --ei android.intent.extra.alarm.HOUR 6 --ei android.intent.extra.alarm.MINUTES 45 \
  --es android.intent.extra.alarm.MESSAGE "Gym" --ez android.intent.extra.alarm.SKIP_UI true \
  -n com.alarmclockpro.debug/com.alarmclockpro.IntentHandlerActivity
