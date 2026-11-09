package com.termuxpanel;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Thin wrapper around the Termux RUN_COMMAND intent API. */
public final class TermuxClient {

    public static final String TERMUX_PACKAGE = "com.termux";
    public static final String TERMUX_API_PACKAGE = "com.termux.api";
    public static final String RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";

    public static final String RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND";
    public static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    public static final String EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
    public static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    public static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    public static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    public static final String EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION";
    public static final String EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";
    public static final String EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    public static final String EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER";

    public static final String RUNNER_APP_SHELL = "app-shell";
    public static final String RUNNER_TERMINAL_SESSION = "terminal-session";
    public static final String SESSION_ACTION_NEW_AND_OPEN = "0";
    public static final String SESSION_ACTION_NEW_WITHOUT_OPENING = "2";

    public static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    public static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";

    private TermuxClient() {
    }

    public static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static String versionName(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static boolean hasRunCommandPermission(Context context) {
        return context.checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static Intent buildRunIntent(Context context, String command, boolean background,
                                        String label, String runner, PendingIntent resultIntent) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE));
        intent.setAction(RUN_COMMAND_ACTION);
        intent.putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH);
        intent.putExtra(EXTRA_ARGUMENTS, new String[]{"-c", command});
        intent.putExtra(EXTRA_WORKDIR, TERMUX_HOME);
        intent.putExtra(EXTRA_BACKGROUND, background);
        intent.putExtra(EXTRA_COMMAND_LABEL, label);
        intent.putExtra(EXTRA_RUNNER, runner);
        if (resultIntent != null) {
            intent.putExtra(EXTRA_PENDING_INTENT, resultIntent);
        }
        return intent;
    }

    public static void send(Context context, Intent intent) {
        try {
            context.startService(intent);
        } catch (IllegalStateException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                throw e;
            }
        }
    }
}
