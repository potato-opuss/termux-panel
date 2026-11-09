package com.termuxpanel;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements ResultBus.Listener {

    private static final int REQUEST_RUN_COMMAND_PERMISSION = 1001;
    private static final long COMMAND_TIMEOUT_MS = 25000L;

    private static final int STATE_UNKNOWN = 0;
    private static final int STATE_OK = 1;
    private static final int STATE_ERROR = 2;

    private static final String DEFAULT_COMMAND =
            "echo \"Merhaba Termux!\"; echo \"Kullanıcı: $(whoami)\"; uname -a; date";

    private TextView statusPill;
    private TextView statusDetails;
    private TextView outputMeta;
    private TextView outputText;
    private TextView opencodeHint;
    private EditText commandInput;
    private Button btnRefresh;
    private Button btnGrantPermission;
    private Button btnRun;
    private Button btnCopy;
    private Button btnOpenCode;
    private Button btnOpenTermux;
    private ScrollView rootScroll;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int connectionState = STATE_UNKNOWN;
    private int requestCounter = 0;
    private int pendingRequestId = -1;
    private boolean hasOutput = false;
    private boolean permissionRequested = false;
    private String currentOutput = "";
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupWindow();
        bindViews();
        setupInsets();
        setupListeners();

        commandInput.setText(DEFAULT_COMMAND);
        outputText.setText(R.string.output_empty);
        ResultBus.setListener(this);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        cancelTimeout();
        ResultBus.clearListener(this);
        super.onDestroy();
    }

    private void setupWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (window.getInsetsController() != null) {
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                window.getInsetsController().setSystemBarsAppearance(appearance, appearance);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void bindViews() {
        rootScroll = findViewById(R.id.rootScroll);
        statusPill = findViewById(R.id.statusPill);
        statusDetails = findViewById(R.id.statusDetails);
        commandInput = findViewById(R.id.commandInput);
        btnRun = findViewById(R.id.btnRun);
        btnCopy = findViewById(R.id.btnCopy);
        outputMeta = findViewById(R.id.outputMeta);
        outputText = findViewById(R.id.outputText);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
        btnOpenCode = findViewById(R.id.btnOpenCode);
        btnOpenTermux = findViewById(R.id.btnOpenTermux);
        opencodeHint = findViewById(R.id.opencodeHint);
    }

    private void setupInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        final int baseLeft = rootScroll.getPaddingLeft();
        final int baseTop = rootScroll.getPaddingTop();
        final int baseRight = rootScroll.getPaddingRight();
        final int baseBottom = rootScroll.getPaddingBottom();
        rootScroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(
                        baseLeft + bars.left,
                        baseTop + bars.top,
                        baseRight + bars.right,
                        baseBottom + bars.bottom);
                return insets;
            }
        });
        rootScroll.requestApplyInsets();
    }

    private void setupListeners() {
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTestCommand();
            }
        });

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyOutput();
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateStatus();
            }
        });

        btnGrantPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestRunPermission();
            }
        });

        btnOpenCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchOpenCode();
            }
        });

        btnOpenTermux.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTermux();
            }
        });
    }

    private void updateStatus() {
        boolean termuxInstalled = TermuxClient.isInstalled(this, TermuxClient.TERMUX_PACKAGE);
        boolean apiInstalled = TermuxClient.isInstalled(this, TermuxClient.TERMUX_API_PACKAGE);
        boolean permitted = TermuxClient.hasRunCommandPermission(this);
        String termuxVersion = TermuxClient.versionName(this, TermuxClient.TERMUX_PACKAGE);

        StringBuilder details = new StringBuilder();
        details.append("Termux: ");
        if (termuxInstalled) {
            details.append("kurulu");
            if (termuxVersion != null && !termuxVersion.isEmpty()) {
                details.append(" (v").append(termuxVersion).append(")");
            }
        } else {
            details.append("bulunamadı");
        }
        details.append("\nTermux:API: ").append(apiInstalled ? "kurulu" : "kurulu değil");
        details.append("\nKomut izni: ").append(permitted ? "verildi" : "verilmedi");
        statusDetails.setText(details.toString());

        btnGrantPermission.setVisibility(termuxInstalled && !permitted ? View.VISIBLE : View.GONE);

        if (!termuxInstalled) {
            setPill(getString(R.string.status_missing), "#C62828", "#FFEBEE");
        } else if (!permitted) {
            setPill(getString(R.string.status_permission), "#8D5A00", "#FFF3E0");
        } else if (connectionState == STATE_OK) {
            setPill(getString(R.string.status_active), "#1B5E20", "#E8F5E9");
        } else if (connectionState == STATE_ERROR) {
            setPill(getString(R.string.status_error), "#C62828", "#FFEBEE");
        } else {
            setPill(getString(R.string.status_ready), "#01579B", "#E1F5FE");
        }
    }

    private void setPill(String text, String textColor, String backgroundColor) {
        statusPill.setText(text);
        statusPill.setTextColor(Color.parseColor(textColor));
        Drawable background = statusPill.getBackground();
        if (background != null) {
            background.mutate().setTint(Color.parseColor(backgroundColor));
        }
    }

    private boolean ensureTermuxReady() {
        if (!TermuxClient.isInstalled(this, TermuxClient.TERMUX_PACKAGE)) {
            connectionState = STATE_ERROR;
            showError(getString(R.string.status_missing));
            return false;
        }
        if (!TermuxClient.hasRunCommandPermission(this)) {
            updateStatus();
            requestRunPermission();
            return false;
        }
        return true;
    }

    private void runTestCommand() {
        if (!ensureTermuxReady()) {
            return;
        }

        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) {
            Toast.makeText(this, R.string.command_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        int requestId = nextRequestId();
        pendingRequestId = requestId;
        PendingIntent resultIntent = CommandResultReceiver.createPendingIntent(this, requestId);
        Intent intent = TermuxClient.buildRunIntent(
                this,
                command,
                true,
                "Termux Paneli Test",
                TermuxClient.RUNNER_APP_SHELL,
                resultIntent);

        try {
            TermuxClient.send(this, intent);
            setBusy(true);
            hasOutput = false;
            currentOutput = "";
            outputMeta.setText(R.string.sending);
            outputText.setText(R.string.waiting_result);
            scheduleTimeout(requestId);
        } catch (Exception e) {
            pendingRequestId = -1;
            setBusy(false);
            connectionState = STATE_ERROR;
            showError(getString(R.string.send_error, safeMessage(e)));
        }
    }

    private void scheduleTimeout(final int requestId) {
        cancelTimeout();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (pendingRequestId != requestId) {
                    return;
                }
                pendingRequestId = -1;
                setBusy(false);
                connectionState = STATE_ERROR;
                updateStatus();
                String message = getString(R.string.timeout);
                outputMeta.setText(R.string.status_error);
                setOutput(message);
            }
        };
        handler.postDelayed(timeoutRunnable, COMMAND_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    @Override
    public void onCommandResult(int requestId, CommandResult result) {
        if (requestId != pendingRequestId) {
            return;
        }

        pendingRequestId = -1;
        cancelTimeout();
        setBusy(false);
        connectionState = STATE_OK;
        updateStatus();

        StringBuilder display = new StringBuilder();
        if (!result.stdout.isEmpty()) {
            display.append(result.stdout);
        }
        if (!result.stderr.isEmpty()) {
            if (display.length() > 0) {
                display.append("\n");
            }
            display.append("[stderr]\n").append(result.stderr);
        }
        if (!result.errorMessage.isEmpty()) {
            if (display.length() > 0) {
                display.append("\n");
            }
            display.append("[Termux]\n").append(result.errorMessage);
        }
        if (display.length() == 0) {
            display.append(getString(R.string.no_output));
        }

        outputMeta.setText(getString(R.string.output_meta, result.exitCode,
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())));
        setOutput(display.toString());
    }

    private void showError(String message) {
        updateStatus();
        outputMeta.setText(R.string.status_error);
        setOutput(message);
    }

    private void setOutput(String text) {
        currentOutput = text;
        hasOutput = true;
        outputText.setText(text);
    }

    private void copyOutput() {
        if (!hasOutput || currentOutput.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Termux Paneli", currentOutput));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchOpenCode() {
        if (!ensureTermuxReady()) {
            return;
        }

        Intent intent = TermuxClient.buildRunIntent(
                this,
                "if command -v opencode >/dev/null 2>&1; then exec opencode; "
                        + "else echo \"OpenCode bulunamadi. Kurulum icin: "
                        + "bash ~/opencode-termux/install.sh\"; exec bash; fi",
                false,
                "Termux Paneli: OpenCode",
                TermuxClient.RUNNER_TERMINAL_SESSION,
                null);
        intent.putExtra(TermuxClient.EXTRA_SESSION_ACTION,
                TermuxClient.SESSION_ACTION_NEW_WITHOUT_OPENING);

        try {
            TermuxClient.send(this, intent);
            opencodeHint.setText(R.string.opencode_launching);
            openTermux();
        } catch (Exception e) {
            opencodeHint.setText(getString(R.string.opencode_send_error, safeMessage(e)));
        }
    }

    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TermuxClient.TERMUX_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, R.string.termux_open_error, Toast.LENGTH_SHORT).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(this, R.string.termux_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestRunPermission() {
        if (TermuxClient.hasRunCommandPermission(this)) {
            updateStatus();
            return;
        }

        boolean canAsk = !permissionRequested
                || shouldShowRequestPermissionRationale(TermuxClient.RUN_COMMAND_PERMISSION);
        if (!canAsk) {
            openAppSettings();
            return;
        }

        permissionRequested = true;
        requestPermissions(new String[]{TermuxClient.RUN_COMMAND_PERMISSION},
                REQUEST_RUN_COMMAND_PERMISSION);
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RUN_COMMAND_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                updateStatus();
            }
        }
    }

    private int nextRequestId() {
        requestCounter = (requestCounter % 100000) + 1;
        return requestCounter;
    }

    private void setBusy(boolean busy) {
        btnRun.setEnabled(!busy);
        btnRun.setText(busy ? R.string.running : R.string.run);
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
