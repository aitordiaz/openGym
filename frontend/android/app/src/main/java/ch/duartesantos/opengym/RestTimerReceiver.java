package ch.duartesantos.opengym;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSObject;

public class RestTimerReceiver extends BroadcastReceiver {
    public static final String ACTION_ADD_15 = "ch.duartesantos.opengym.ACTION_ADD_15";
    public static final String ACTION_SKIP = "ch.duartesantos.opengym.ACTION_SKIP";
    public static final String ACTION_FINISHED = "ch.duartesantos.opengym.ACTION_FINISHED";

    public static final String PREFS_NAME = "openGym_rest_timer";
    public static final String PREF_ENDS_AT = "ends_at";
    public static final String PREF_TITLE = "title";
    public static final String PREF_BODY = "body";
    public static final String PREF_ADD15_LABEL = "add15_label";
    public static final String PREF_SKIP_LABEL = "skip_label";
    public static final String PREF_FINISHED_TITLE = "finished_title";
    public static final String PREF_FINISHED_BODY = "finished_body";
    public static final String PREF_SKIPPED = "is_skipped";

    public static final int NOTIFICATION_ID_COUNTDOWN = 7001;
    public static final int NOTIFICATION_ID_FINISHED = 7002;
    public static final String CHANNEL_ID_COUNTDOWN = "rest_timer_countdown";
    public static final String CHANNEL_ID_FINISHED = "rest_timer_finished";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (ACTION_ADD_15.equals(action)) {
            long endsAt = prefs.getLong(PREF_ENDS_AT, 0);
            if (endsAt <= 0) endsAt = System.currentTimeMillis();
            endsAt += 15000;
            prefs.edit().putLong(PREF_ENDS_AT, endsAt).putBoolean(PREF_SKIPPED, false).apply();

            String title = prefs.getString(PREF_TITLE, "Descanso");
            String body = prefs.getString(PREF_BODY, "");
            String add15Label = prefs.getString(PREF_ADD15_LABEL, "+15s");
            String skipLabel = prefs.getString(PREF_SKIP_LABEL, "Saltar");

            showCountdownNotification(context, endsAt, title, body, add15Label, skipLabel);
            scheduleFinishedAlarm(context, endsAt);

            if (RestTimerPlugin.instance != null) {
                JSObject data = new JSObject();
                data.put("endsAt", (double) endsAt);
                data.put("added", 15);
                RestTimerPlugin.instance.notifyListeners("timerAdjusted", data);
            }
        } else if (ACTION_SKIP.equals(action)) {
            prefs.edit().putBoolean(PREF_SKIPPED, true).apply();
            cancelCountdown(context);

            if (RestTimerPlugin.instance != null) {
                RestTimerPlugin.instance.notifyListeners("timerSkipped", new JSObject());
            }
        } else if (ACTION_FINISHED.equals(action)) {
            cancelCountdownNotification(context);

            String finishedTitle = prefs.getString(PREF_FINISHED_TITLE, "¡Descanso terminado — siguiente serie!");
            String finishedBody = prefs.getString(PREF_FINISHED_BODY, "openGym");

            showFinishedNotification(context, finishedTitle, finishedBody);

            if (RestTimerPlugin.instance != null) {
                RestTimerPlugin.instance.notifyListeners("timerFinished", new JSObject());
            }
        }
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel countdownChannel = new NotificationChannel(
                CHANNEL_ID_COUNTDOWN,
                "Rest Countdown",
                NotificationManager.IMPORTANCE_LOW
            );
            countdownChannel.setDescription("Shows active rest timer countdown");
            countdownChannel.setShowBadge(false);
            countdownChannel.setSound(null, null);
            countdownChannel.enableVibration(false);
            nm.createNotificationChannel(countdownChannel);

            NotificationChannel finishedChannel = new NotificationChannel(
                CHANNEL_ID_FINISHED,
                "Rest Finished",
                NotificationManager.IMPORTANCE_HIGH
            );
            finishedChannel.setDescription("Alerts when rest timer completes");
            finishedChannel.setShowBadge(true);
            finishedChannel.enableVibration(true);
            finishedChannel.setVibrationPattern(new long[]{0, 250, 150, 250});
            nm.createNotificationChannel(finishedChannel);
        }
    }

    public static void showCountdownNotification(Context context, long endsAt, String title, String body, String add15Label, String skipLabel) {
        createNotificationChannels(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent add15Intent = new Intent(context, RestTimerReceiver.class);
        add15Intent.setAction(ACTION_ADD_15);
        PendingIntent add15PendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            add15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent skipIntent = new Intent(context, RestTimerReceiver.class);
        skipIntent.setAction(ACTION_SKIP);
        PendingIntent skipPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        int icon = context.getApplicationInfo().icon != 0 ? context.getApplicationInfo().icon : android.R.drawable.ic_lock_idle_alarm;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_COUNTDOWN)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentPendingIntent)
            .setWhen(endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, add15Label, add15PendingIntent)
            .addAction(0, skipLabel, skipPendingIntent);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COUNTDOWN, builder.build());
        } catch (SecurityException ignored) {}
    }

    public static void showFinishedNotification(Context context, String title, String body) {
        createNotificationChannels(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        int icon = context.getApplicationInfo().icon != 0 ? context.getApplicationInfo().icon : android.R.drawable.ic_lock_idle_alarm;
        Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_FINISHED)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSound)
            .setVibrate(new long[]{0, 250, 150, 250})
            .setCategory(NotificationCompat.CATEGORY_ALARM);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FINISHED, builder.build());
        } catch (SecurityException ignored) {}
    }

    public static void scheduleFinishedAlarm(Context context, long endsAt) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, RestTimerReceiver.class);
        intent.setAction(ACTION_FINISHED);
        PendingIntent pi = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi);
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi);
            }
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, endsAt, pi);
        }
    }

    public static void cancelCountdownNotification(Context context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_COUNTDOWN);
        } catch (Exception ignored) {}
    }

    public static void cancelCountdown(Context context) {
        cancelCountdownNotification(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            Intent intent = new Intent(context, RestTimerReceiver.class);
            intent.setAction(ACTION_FINISHED);
            PendingIntent pi = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }
}
