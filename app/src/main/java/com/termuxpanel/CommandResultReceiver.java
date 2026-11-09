package com.termuxpanel;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class CommandResultReceiver extends BroadcastReceiver {

    private static final String ACTION_RESULT = "com.termuxpanel.action.COMMAND_RESULT";
    private static final String EXTRA_REQUEST_ID = "com.termuxpanel.extra.REQUEST_ID";
    private static final String RESULT_BUNDLE = "result";

    public static PendingIntent createPendingIntent(Context context, int requestId) {
        Intent intent = new Intent(context, CommandResultReceiver.class);
        intent.setAction(ACTION_RESULT);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestId, intent, flags);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        int requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1);
        Bundle bundle = intent.getBundleExtra(RESULT_BUNDLE);
        if (bundle == null) {
            bundle = intent.getExtras();
        }

        String stdout = "";
        String stderr = "";
        String errmsg = "";
        int exitCode = -1;

        if (bundle != null) {
            stdout = stringValue(bundle, "stdout");
            stderr = stringValue(bundle, "stderr");
            errmsg = stringValue(bundle, "errmsg");
            if (bundle.containsKey("exitCode")) {
                exitCode = bundle.getInt("exitCode", -1);
            }
        }

        ResultBus.dispatch(requestId, new CommandResult(stdout, stderr, errmsg, exitCode));
    }

    private static String stringValue(Bundle bundle, String key) {
        Object value = bundle.get(key);
        return value instanceof String ? (String) value : "";
    }
}
