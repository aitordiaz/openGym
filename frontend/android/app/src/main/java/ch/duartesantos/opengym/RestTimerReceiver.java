package ch.duartesantos.opengym;

import android.app.AlarmManager;
import android.app.Notification;
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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSObject;
import java.util.Locale;

public class RestTimerReceiver extends BroadcastReceiver {
    private static final String TAG = "RestTimerReceiver";

    public static final String ACTION_SUB_15 = "ch.duartesantos.opengym.ACTION_SUB_15";
    public static final String ACTION_ADD_15 = "ch.duartesantos.opengym.ACTION_ADD_15";
    public static final String ACTION_SKIP = "ch.duartesantos.opengym.ACTION_SKIP";
    public static final String ACTION_FINISHED = "ch.duartesantos.opengym.ACTION_FINISHED";

    public static final String PREFS_NAME = "openGym_rest_timer";
    public static final String PREF_ENDS_AT = "ends_at";
    public static final String PREF_TOTAL = "total";
    public static final String PREF_TITLE = "title";
    public static final String PREF_BODY = "body";
    public static final String PREF_SUB15_LABEL = "sub15_label";
    public static final String PREF_ADD15_LABEL = "add15_label";
    public static final String PREF_SKIP_LABEL = "skip_label";
    public static final String PREF_FINISHED_TITLE = "finished_title";
    public static final String PREF_FINISHED_BODY = "finished_body";
    public static final String PREF_SKIPPED = "is_skipped";

    public static final int NOTIFICATION_ID_COUNTDOWN = 7001;
    public static final int NOTIFICATION_ID_FINISHED = 7002;
    public static final String CHANNEL_ID_COUNTDOWN = "rest_timer_countdown_v2";
    public static final String CHANNEL_ID_FINISHED = "rest_timer_finished_v2";

    private static Handler tickerHandler;
    private static Runnable tickerRunnable;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        Log.i(TAG, "onReceive action: " + action);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (ACTION_SUB_15.equals(action)) {
            long endsAt = prefs.getLong(PREF_ENDS_AT, 0);
            if (endsAt <= 0) endsAt = System.currentTimeMillis();
            endsAt -= 15000;
            long now = System.currentTimeMillis();

            if (endsAt <= now) {
                // Taking off more than is left means "I'm ready now" — same as skipping
                prefs.edit().putBoolean(PREF_SKIPPED, true).apply();
                cancelCountdown(context);

                if (RestTimerPlugin.instance != null) {
                    RestTimerPlugin.instance.onTimerSkipped();
                }
            } else {
                long total = Math.max(1, prefs.getLong(PREF_TOTAL, 0) - 15);
                prefs.edit().putLong(PREF_ENDS_AT, endsAt).putLong(PREF_TOTAL, total).putBoolean(PREF_SKIPPED, false).apply();
                String title = prefs.getString(PREF_TITLE, "Descanso");
                String sub15Label = prefs.getString(PREF_SUB15_LABEL, "-15s");
                String add15Label = prefs.getString(PREF_ADD15_LABEL, "+15s");
                String skipLabel = prefs.getString(PREF_SKIP_LABEL, "Saltar");

                showCountdownNotification(context, endsAt, total, title, sub15Label, add15Label, skipLabel);
                scheduleFinishedAlarm(context, endsAt);

                if (RestTimerPlugin.instance != null) {
                    JSObject data = new JSObject();
                    data.put("endsAt", (double) endsAt);
                    data.put("added", -15);
                    RestTimerPlugin.instance.onTimerAdjusted(data);
                }
            }
        } else if (ACTION_ADD_15.equals(action)) {
            long endsAt = prefs.getLong(PREF_ENDS_AT, 0);
            if (endsAt <= 0) endsAt = System.currentTimeMillis();
            endsAt += 15000;
            long total = prefs.getLong(PREF_TOTAL, 0) + 15;
            prefs.edit().putLong(PREF_ENDS_AT, endsAt).putLong(PREF_TOTAL, total).putBoolean(PREF_SKIPPED, false).apply();

            String title = prefs.getString(PREF_TITLE, "Descanso");
            String sub15Label = prefs.getString(PREF_SUB15_LABEL, "-15s");
            String add15Label = prefs.getString(PREF_ADD15_LABEL, "+15s");
            String skipLabel = prefs.getString(PREF_SKIP_LABEL, "Saltar");

            showCountdownNotification(context, endsAt, total, title, sub15Label, add15Label, skipLabel);
            scheduleFinishedAlarm(context, endsAt);

            if (RestTimerPlugin.instance != null) {
                JSObject data = new JSObject();
                data.put("endsAt", (double) endsAt);
                data.put("added", 15);
                RestTimerPlugin.instance.onTimerAdjusted(data);
            }
        } else if (ACTION_SKIP.equals(action)) {
            prefs.edit().putBoolean(PREF_SKIPPED, true).apply();
            cancelCountdown(context);

            if (RestTimerPlugin.instance != null) {
                RestTimerPlugin.instance.onTimerSkipped();
            }
        } else if (ACTION_FINISHED.equals(action)) {
            cancelCountdownNotification(context);

            String finishedTitle = prefs.getString(PREF_FINISHED_TITLE, "¡Descanso terminado — siguiente serie!");
            String finishedBody = prefs.getString(PREF_FINISHED_BODY, "openGym");

            showFinishedNotification(context, finishedTitle, finishedBody);

            if (RestTimerPlugin.instance != null) {
                RestTimerPlugin.instance.onTimerFinished();
            }
        }
    }

    public static int getNotificationSmallIcon(Context context) {
        int resId = context.getResources().getIdentifier("ic_stat_rest_timer", "drawable", context.getPackageName());
        if (resId == 0) {
            resId = ch.duartesantos.opengym.R.drawable.ic_stat_rest_timer;
        }
        if (resId == 0) {
            resId = context.getResources().getIdentifier("ic_stat_icon", "drawable", context.getPackageName());
        }
        if (resId == 0) {
            resId = android.R.drawable.ic_dialog_info;
        }
        return resId;
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            try {
                nm.deleteNotificationChannel("rest_timer_countdown");
                nm.deleteNotificationChannel("rest_timer_finished");
            } catch (Exception ignored) {}

            NotificationChannel countdownChannel = new NotificationChannel(
                CHANNEL_ID_COUNTDOWN,
                "Rest Countdown",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            countdownChannel.setDescription("Shows active rest timer countdown");
            countdownChannel.setShowBadge(true);
            countdownChannel.setSound(null, null);
            countdownChannel.enableVibration(false);
            countdownChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
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
            finishedChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(finishedChannel);
        }
    }

    public static void showCountdownNotification(Context context, long endsAt, long totalSeconds, String title, String sub15Label, String add15Label, String skipLabel) {
        createNotificationChannels(context);

        long now = System.currentTimeMillis();
        int leftSeconds = (int) Math.max(0, (endsAt - now + 999) / 1000);
        if (totalSeconds <= 0) totalSeconds = leftSeconds;
        if (totalSeconds < leftSeconds) totalSeconds = leftSeconds;

        int min = leftSeconds / 60;
        int sec = leftSeconds % 60;
        String formattedTime = String.format(Locale.getDefault(), "%d:%02d", min, sec);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setAction(Intent.ACTION_MAIN);
        openIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent sub15Intent = new Intent(context, RestTimerReceiver.class);
        sub15Intent.setAction(ACTION_SUB_15);
        PendingIntent sub15PendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            sub15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent add15Intent = new Intent(context, RestTimerReceiver.class);
        add15Intent.setAction(ACTION_ADD_15);
        PendingIntent add15PendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            add15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent skipIntent = new Intent(context, RestTimerReceiver.class);
        skipIntent.setAction(ACTION_SKIP);
        PendingIntent skipPendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        int icon = getNotificationSmallIcon(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_COUNTDOWN)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(formattedTime)
            .setSubText("openGym")
            .setContentIntent(contentPendingIntent)
            .setProgress((int) Math.max(totalSeconds, leftSeconds), leftSeconds, false)
            .setWhen(endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, sub15Label, sub15PendingIntent)
            .addAction(0, add15Label, add15PendingIntent)
            .addAction(0, skipLabel, skipPendingIntent);

        boolean enabled = NotificationManagerCompat.from(context).areNotificationsEnabled();
        Log.i(TAG, "showCountdownNotification: areNotificationsEnabled=" + enabled + ", icon=" + icon + ", endsAt=" + endsAt + ", left=" + leftSeconds + "/" + totalSeconds);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COUNTDOWN, builder.build());
            Log.i(TAG, "NotificationManager.notify completed successfully");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to show countdown notification", e);
        }

        startTicker(context);
    }

    public static synchronized void startTicker(Context context) {
        stopTicker();
        Context appCtx = context.getApplicationContext();
        tickerHandler = new Handler(Looper.getMainLooper());
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean isSkipped = prefs.getBoolean(PREF_SKIPPED, false);
                long endsAt = prefs.getLong(PREF_ENDS_AT, 0);
                long now = System.currentTimeMillis();
                if (!isSkipped && endsAt > now) {
                    long total = prefs.getLong(PREF_TOTAL, 0);
                    String title = prefs.getString(PREF_TITLE, "Descanso");
                    String sub15Label = prefs.getString(PREF_SUB15_LABEL, "-15s");
                    String add15Label = prefs.getString(PREF_ADD15_LABEL, "+15s");
                    String skipLabel = prefs.getString(PREF_SKIP_LABEL, "Saltar");

                    int left = (int) Math.max(0, (endsAt - now + 999) / 1000);
                    int min = left / 60;
                    int sec = left % 60;
                    String formattedTime = String.format(Locale.getDefault(), "%d:%02d", min, sec);

                    Intent openIntent = new Intent(appCtx, MainActivity.class);
                    openIntent.setAction(Intent.ACTION_MAIN);
                    openIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent contentPendingIntent = PendingIntent.getActivity(
                        appCtx, 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                    );

                    Intent sub15Intent = new Intent(appCtx, RestTimerReceiver.class);
                    sub15Intent.setAction(ACTION_SUB_15);
                    PendingIntent sub15PendingIntent = PendingIntent.getBroadcast(
                        appCtx, 1, sub15Intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                    );

                    Intent add15Intent = new Intent(appCtx, RestTimerReceiver.class);
                    add15Intent.setAction(ACTION_ADD_15);
                    PendingIntent add15PendingIntent = PendingIntent.getBroadcast(
                        appCtx, 2, add15Intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                    );

                    Intent skipIntent = new Intent(appCtx, RestTimerReceiver.class);
                    skipIntent.setAction(ACTION_SKIP);
                    PendingIntent skipPendingIntent = PendingIntent.getBroadcast(
                        appCtx, 3, skipIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                    );

                    NotificationCompat.Builder b = new NotificationCompat.Builder(appCtx, CHANNEL_ID_COUNTDOWN)
                        .setSmallIcon(getNotificationSmallIcon(appCtx))
                        .setContentTitle(title)
                        .setContentText(formattedTime)
                        .setSubText("openGym")
                        .setContentIntent(contentPendingIntent)
                        .setProgress((int) Math.max(total, left), left, false)
                        .setWhen(endsAt)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setShowWhen(true)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                        .addAction(0, sub15Label, sub15PendingIntent)
                        .addAction(0, add15Label, add15PendingIntent)
                        .addAction(0, skipLabel, skipPendingIntent);

                    try {
                        NotificationManagerCompat.from(appCtx).notify(NOTIFICATION_ID_COUNTDOWN, b.build());
                    } catch (Throwable ignored) {}

                    if (tickerHandler != null) {
                        tickerHandler.postDelayed(this, 1000);
                    }
                } else {
                    stopTicker();
                }
            }
        };
        tickerHandler.postDelayed(tickerRunnable, 1000);
    }

    public static synchronized void stopTicker() {
        if (tickerHandler != null && tickerRunnable != null) {
            tickerHandler.removeCallbacks(tickerRunnable);
            tickerHandler = null;
            tickerRunnable = null;
        }
    }

    public static void showFinishedNotification(Context context, String title, String body) {
        createNotificationChannels(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setAction(Intent.ACTION_MAIN);
        openIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        int icon = getNotificationSmallIcon(context);
        Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_FINISHED)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSound)
            .setVibrate(new long[]{0, 250, 150, 250})
            .setCategory(NotificationCompat.CATEGORY_ALARM);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FINISHED, builder.build());
        } catch (Throwable e) {
            Log.e(TAG, "Failed to show finished notification", e);
        }
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
        stopTicker();
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_COUNTDOWN);
        } catch (Exception ignored) {}
    }

    public static void cancelCountdown(Context context) {
        stopTicker();
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
