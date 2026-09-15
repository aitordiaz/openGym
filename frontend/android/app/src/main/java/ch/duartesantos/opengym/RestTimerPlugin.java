package ch.duartesantos.opengym;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

@CapacitorPlugin(
    name = "RestTimer",
    permissions = {
        @Permission(
            alias = "notifications",
            strings = { Manifest.permission.POST_NOTIFICATIONS }
        )
    }
)
public class RestTimerPlugin extends Plugin {
    public static RestTimerPlugin instance;

    @Override
    public void load() {
        super.load();
        instance = this;
    }

    @Override
    protected void handleOnDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.handleOnDestroy();
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        checkAndShowBackgroundNotification();
    }

    @Override
    protected void handleOnStop() {
        super.handleOnStop();
        checkAndShowBackgroundNotification();
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        // Clear countdown notification when returning to foreground
        Context context = getContext();
        if (context != null) {
            RestTimerReceiver.cancelCountdownNotification(context);
        }
    }

    private void checkAndShowBackgroundNotification() {
        Context context = getContext();
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(RestTimerReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        long endsAt = prefs.getLong(RestTimerReceiver.PREF_ENDS_AT, 0);
        boolean isSkipped = prefs.getBoolean(RestTimerReceiver.PREF_SKIPPED, false);

        if (!isSkipped && endsAt > System.currentTimeMillis()) {
            String title = prefs.getString(RestTimerReceiver.PREF_TITLE, "Descanso");
            String body = prefs.getString(RestTimerReceiver.PREF_BODY, "");
            String add15Label = prefs.getString(RestTimerReceiver.PREF_ADD15_LABEL, "+15s");
            String skipLabel = prefs.getString(RestTimerReceiver.PREF_SKIP_LABEL, "Saltar");

            RestTimerReceiver.showCountdownNotification(context, endsAt, title, body, add15Label, skipLabel);
            RestTimerReceiver.scheduleFinishedAlarm(context, endsAt);
        }
    }

    @PluginMethod
    public void setRestTimer(PluginCall call) {
        Double endsAtVal = call.getDouble("endsAt");
        if (endsAtVal == null) {
            call.reject("endsAt is required");
            return;
        }
        long endsAt = endsAtVal.longValue();
        String title = call.getString("title", "Descanso");
        String body = call.getString("body", "");
        String add15Label = call.getString("add15Label", "+15s");
        String skipLabel = call.getString("skipLabel", "Saltar");
        String finishedTitle = call.getString("finishedTitle", "¡Descanso terminado — siguiente serie!");
        String finishedBody = call.getString("finishedBody", "openGym");

        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(RestTimerReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putLong(RestTimerReceiver.PREF_ENDS_AT, endsAt)
            .putString(RestTimerReceiver.PREF_TITLE, title)
            .putString(RestTimerReceiver.PREF_BODY, body)
            .putString(RestTimerReceiver.PREF_ADD15_LABEL, add15Label)
            .putString(RestTimerReceiver.PREF_SKIP_LABEL, skipLabel)
            .putString(RestTimerReceiver.PREF_FINISHED_TITLE, finishedTitle)
            .putString(RestTimerReceiver.PREF_FINISHED_BODY, finishedBody)
            .putBoolean(RestTimerReceiver.PREF_SKIPPED, false)
            .apply();

        call.resolve();
    }

    @PluginMethod
    public void show(PluginCall call) {
        setRestTimer(call);
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(RestTimerReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        long endsAt = prefs.getLong(RestTimerReceiver.PREF_ENDS_AT, 0);
        String title = prefs.getString(RestTimerReceiver.PREF_TITLE, "Descanso");
        String body = prefs.getString(RestTimerReceiver.PREF_BODY, "");
        String add15Label = prefs.getString(RestTimerReceiver.PREF_ADD15_LABEL, "+15s");
        String skipLabel = prefs.getString(RestTimerReceiver.PREF_SKIP_LABEL, "Saltar");

        RestTimerReceiver.showCountdownNotification(context, endsAt, title, body, add15Label, skipLabel);
        RestTimerReceiver.scheduleFinishedAlarm(context, endsAt);

        call.resolve();
    }

    @PluginMethod
    public void clear(PluginCall call) {
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(RestTimerReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(RestTimerReceiver.PREF_SKIPPED, true).apply();
        RestTimerReceiver.cancelCountdown(context);
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences(RestTimerReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        long endsAt = prefs.getLong(RestTimerReceiver.PREF_ENDS_AT, 0);
        boolean isSkipped = prefs.getBoolean(RestTimerReceiver.PREF_SKIPPED, false);

        JSObject ret = new JSObject();
        ret.put("endsAt", (double) endsAt);
        ret.put("isSkipped", isSkipped);
        call.resolve(ret);
    }

    public void onTimerAdjusted(JSObject data) {
        notifyListeners("timerAdjusted", data);
    }

    public void onTimerSkipped() {
        notifyListeners("timerSkipped", new JSObject());
    }

    public void onTimerFinished() {
        notifyListeners("timerFinished", new JSObject());
    }
}
