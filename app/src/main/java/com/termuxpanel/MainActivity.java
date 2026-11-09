package com.termuxpanel;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
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

    private static final String OPENCODE_COMMAND =
            "if command -v opencode >/dev/null 2>&1; then exec opencode; "
                    + "else echo \"OpenCode bulunamadi. Kurulum icin: "
                    + "bash ~/opencode-termux/install.sh\"; exec bash; fi";

    private LinearLayout rootLayout;
    private TextView statusText;
    private LinearLayout statusBanner;
    private TextView bannerText;
    private Button bannerAction;
    private Button btnOpenCode;
    private ImageButton btnMore;
    private ImageButton btnSend;
    private EditText messageInput;
    private ScrollView chatScroll;
    private LinearLayout messagesContainer;
    private LinearLayout emptyState;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int connectionState = STATE_UNKNOWN;
    private int requestCounter = 0;
    private int pendingRequestId = -1;
    private int maxBubbleWidth;
    private boolean pending = false;
    private boolean permissionRequested = false;
    private View pendingBubble;
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupWindow();
        bindViews();
        setupInsets();
        setupListeners();

        maxBubbleWidth = Math.min(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.84f),
                dp(560));
        ResultBus.setListener(this);
        updateStatus();
        updateSendState();
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
        rootLayout = findViewById(R.id.rootLayout);
        statusText = findViewById(R.id.statusText);
        statusBanner = findViewById(R.id.statusBanner);
        bannerText = findViewById(R.id.bannerText);
        bannerAction = findViewById(R.id.bannerAction);
        btnOpenCode = findViewById(R.id.btnOpenCode);
        btnMore = findViewById(R.id.btnMore);
        btnSend = findViewById(R.id.btnSend);
        messageInput = findViewById(R.id.messageInput);
        chatScroll = findViewById(R.id.chatScroll);
        messagesContainer = findViewById(R.id.messagesContainer);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        final int baseLeft = rootLayout.getPaddingLeft();
        final int baseTop = rootLayout.getPaddingTop();
        final int baseRight = rootLayout.getPaddingRight();
        final int baseBottom = rootLayout.getPaddingBottom();
        rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(
                        baseLeft + bars.left,
                        baseTop + bars.top,
                        baseRight + bars.right,
                        baseBottom + bars.bottom);
                return insets;
            }
        });
        rootLayout.requestApplyInsets();
    }

    private void setupListeners() {
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        btnOpenCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchOpenCode();
            }
        });

        btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu();
            }
        });

        bannerAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestRunPermission();
            }
        });

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSendState();
            }
        });

        messageInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });

        messageInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    scrollToBottomSoon();
                }
            }
        });
    }

    private void showMoreMenu() {
        PopupMenu popup = new PopupMenu(this, btnMore);
        popup.getMenu().add(0, 1, 0, getString(R.string.open_termux));
        popup.getMenu().add(0, 2, 1, getString(R.string.refresh_status));
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) {
                    openTermux();
                    return true;
                }
                if (item.getItemId() == 2) {
                    updateStatus();
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    private void updateStatus() {
        boolean termuxInstalled = TermuxClient.isInstalled(this, TermuxClient.TERMUX_PACKAGE);
        boolean permitted = TermuxClient.hasRunCommandPermission(this);
        String version = TermuxClient.versionName(this, TermuxClient.TERMUX_PACKAGE);

        String label;
        int color;
        if (!termuxInstalled) {
            label = getString(R.string.status_missing);
            color = getColor(R.color.error);
        } else if (!permitted) {
            label = getString(R.string.status_permission);
            color = getColor(R.color.warning_text);
        } else if (connectionState == STATE_OK) {
            label = getString(R.string.status_active);
            color = getColor(R.color.success);
        } else if (connectionState == STATE_ERROR) {
            label = getString(R.string.status_error);
            color = getColor(R.color.error);
        } else {
            label = getString(R.string.status_ready);
            color = getColor(R.color.primary);
        }

        if (termuxInstalled && version != null && !version.isEmpty()) {
            statusText.setText(getString(R.string.status_with_version, label, version));
        } else {
            statusText.setText(getString(R.string.status_without_version, label));
        }
        statusText.setTextColor(color);

        if (!termuxInstalled) {
            showBanner(getString(R.string.termux_missing_banner), false, true);
        } else if (!permitted) {
            showBanner(getString(R.string.permission_banner), true, false);
        } else {
            statusBanner.setVisibility(View.GONE);
        }
    }

    private void showBanner(String text, boolean showAction, boolean isError) {
        statusBanner.setVisibility(View.VISIBLE);
        statusBanner.setBackgroundColor(getColor(
                isError ? R.color.error_bg : R.color.warning_bg));
        bannerText.setText(text);
        bannerText.setTextColor(getColor(
                isError ? R.color.error : R.color.warning_text));
        bannerAction.setVisibility(showAction ? View.VISIBLE : View.GONE);
    }

    private void sendMessage() {
        if (pending) {
            return;
        }

        String command = messageInput.getText().toString().trim();
        if (command.isEmpty()) {
            return;
        }

        if (!TermuxClient.isInstalled(this, TermuxClient.TERMUX_PACKAGE)) {
            updateStatus();
            addSystemMessage(getString(R.string.termux_not_found_msg), true);
            return;
        }

        if (!TermuxClient.hasRunCommandPermission(this)) {
            updateStatus();
            addSystemMessage(getString(R.string.permission_required_msg), true);
            requestRunPermission();
            return;
        }

        messageInput.setText("");
        addUserMessage(command);
        startCommand(command);
    }

    private void startCommand(String command) {
        int requestId = nextRequestId();
        pendingRequestId = requestId;
        addPendingBubble();
        setPending(true);

        PendingIntent resultIntent = CommandResultReceiver.createPendingIntent(this, requestId);
        Intent intent = TermuxClient.buildRunIntent(
                this,
                command,
                true,
                "Termux Paneli",
                TermuxClient.RUNNER_APP_SHELL,
                resultIntent);

        try {
            TermuxClient.send(this, intent);
            scheduleTimeout(requestId);
        } catch (Exception e) {
            pendingRequestId = -1;
            cancelTimeout();
            setPending(false);
            removePendingBubble();
            connectionState = STATE_ERROR;
            updateStatus();
            addAssistantMessage(getString(R.string.send_error, safeMessage(e)), -1, true);
        }
    }

    private void scheduleTimeout(final int requestId) {
        cancelTimeout();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                timeoutRunnable = null;
                if (pendingRequestId != requestId) {
                    return;
                }
                pendingRequestId = -1;
                setPending(false);
                removePendingBubble();
                connectionState = STATE_ERROR;
                updateStatus();
                addAssistantMessage(getString(R.string.timeout), -1, true);
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
        setPending(false);
        removePendingBubble();
        connectionState = STATE_OK;
        updateStatus();

        boolean isError = result.exitCode != 0 || !result.errorMessage.isEmpty();
        addAssistantMessage(formatOutput(result), result.exitCode, isError);
    }

    private String formatOutput(CommandResult result) {
        StringBuilder display = new StringBuilder();
        if (!result.stdout.isEmpty()) {
            display.append(result.stdout);
        }
        if (!result.stderr.isEmpty()) {
            if (display.length() > 0) {
                display.append("\n");
            }
            display.append(result.stderr);
        }
        if (!result.errorMessage.isEmpty()) {
            if (display.length() > 0) {
                display.append("\n");
            }
            display.append(result.errorMessage);
        }
        if (display.length() == 0) {
            display.append(getString(R.string.no_output));
        }
        return display.toString();
    }

    private void addUserMessage(String text) {
        hideEmptyState();
        LinearLayout row = createMessageRow(Gravity.END);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14.5f);
        bubble.setTextColor(getColor(R.color.white));
        bubble.setLineSpacing(dp(2), 1f);
        bubble.setPadding(dp(16), dp(11), dp(16), dp(11));
        bubble.setBackgroundResource(R.drawable.bg_bubble_user);
        bubble.setTextIsSelectable(true);
        bubble.setMaxWidth(maxBubbleWidth);

        row.addView(bubble, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        messagesContainer.addView(row);
        scrollToBottom();
    }

    private void addAssistantMessage(String text, int exitCode, boolean isError) {
        hideEmptyState();
        final String output = text;
        LinearLayout row = createMessageRow(Gravity.START);
        row.addView(createAssistantLabel(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView bubble = new TextView(this);
        bubble.setText(output);
        bubble.setTextSize(13.5f);
        bubble.setTypeface(Typeface.MONOSPACE);
        bubble.setTextColor(getColor(isError ? R.color.error : R.color.text_primary));
        bubble.setLineSpacing(dp(2), 1f);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackgroundResource(isError
                ? R.drawable.bg_bubble_error
                : R.drawable.bg_bubble_assistant);
        bubble.setTextIsSelectable(true);
        bubble.setMaxWidth(maxBubbleWidth);
        row.addView(bubble, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setPadding(dp(6), dp(5), dp(6), 0);

        TextView meta = new TextView(this);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        meta.setText(exitCode >= 0
                ? getString(R.string.exit_meta, exitCode, time)
                : getString(R.string.error_meta, time));
        meta.setTextSize(10.5f);
        meta.setTextColor(getColor(isError ? R.color.error : R.color.text_secondary));
        metaRow.addView(meta, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView copy = createCopyAction(output);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        copyParams.leftMargin = dp(12);
        metaRow.addView(copy, copyParams);

        row.addView(metaRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        messagesContainer.addView(row);
        scrollToBottom();
    }

    private void addSystemMessage(String text, boolean isError) {
        hideEmptyState();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        rowParams.bottomMargin = dp(6);
        row.setLayoutParams(rowParams);

        TextView message = new TextView(this);
        message.setText(text);
        message.setTextSize(11.5f);
        message.setTextColor(getColor(isError ? R.color.error : R.color.text_secondary));
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(2), 1f);
        message.setPadding(dp(14), dp(8), dp(14), dp(8));
        message.setBackgroundResource(R.drawable.bg_pill);
        message.setMaxWidth(maxBubbleWidth);
        Drawable background = message.getBackground();
        if (background != null) {
            background.mutate().setTint(getColor(
                    isError ? R.color.error_bg : R.color.output_bg));
        }

        row.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        messagesContainer.addView(row);
        scrollToBottom();
    }

    private void addPendingBubble() {
        hideEmptyState();
        LinearLayout row = createMessageRow(Gravity.START);
        row.addView(createAssistantLabel(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_bubble_assistant);
        bubble.setPadding(dp(16), dp(13), dp(18), dp(13));

        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleSmall);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(getColor(R.color.primary)));
        bubble.addView(progress, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView label = new TextView(this);
        label.setText(R.string.running);
        label.setTextSize(13.5f);
        label.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(10);
        bubble.addView(label, labelParams);

        row.addView(bubble, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        messagesContainer.addView(row);
        pendingBubble = row;
        scrollToBottom();
    }

    private LinearLayout createMessageRow(int gravity) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(gravity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        row.setLayoutParams(params);
        return row;
    }

    private TextView createAssistantLabel() {
        TextView label = new TextView(this);
        label.setText(R.string.assistant_name);
        label.setTextSize(9.5f);
        label.setLetterSpacing(0.12f);
        label.setTextColor(getColor(R.color.text_secondary));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setPadding(dp(6), 0, 0, dp(4));
        return label;
    }

    private TextView createCopyAction(final String output) {
        TextView copy = new TextView(this);
        copy.setText(R.string.copy);
        copy.setTextSize(10.5f);
        copy.setTextColor(getColor(R.color.primary));
        copy.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.setGravity(Gravity.CENTER);
        copy.setMinHeight(dp(32));
        copy.setPadding(dp(8), 0, dp(8), 0);
        copy.setClickable(true);
        copy.setFocusable(true);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(output);
            }
        });
        return copy;
    }

    private void removePendingBubble() {
        if (pendingBubble != null && pendingBubble.getParent() == messagesContainer) {
            messagesContainer.removeView(pendingBubble);
        }
        pendingBubble = null;
    }

    private void hideEmptyState() {
        if (emptyState.getVisibility() != View.GONE) {
            emptyState.setVisibility(View.GONE);
        }
    }

    private void scrollToBottom() {
        chatScroll.post(new Runnable() {
            @Override
            public void run() {
                chatScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void scrollToBottomSoon() {
        chatScroll.postDelayed(new Runnable() {
            @Override
            public void run() {
                chatScroll.fullScroll(View.FOCUS_DOWN);
            }
        }, 240L);
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || text == null || text.isEmpty()) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void launchOpenCode() {
        if (!TermuxClient.isInstalled(this, TermuxClient.TERMUX_PACKAGE)) {
            updateStatus();
            Toast.makeText(this, R.string.termux_not_found_msg, Toast.LENGTH_LONG).show();
            return;
        }
        if (!TermuxClient.hasRunCommandPermission(this)) {
            updateStatus();
            Toast.makeText(this, R.string.permission_needed_for_opencode,
                    Toast.LENGTH_LONG).show();
            requestRunPermission();
            return;
        }

        Intent intent = TermuxClient.buildRunIntent(
                this,
                OPENCODE_COMMAND,
                false,
                "Termux Paneli: OpenCode",
                TermuxClient.RUNNER_TERMINAL_SESSION,
                null);
        intent.putExtra(TermuxClient.EXTRA_SESSION_ACTION,
                TermuxClient.SESSION_ACTION_NEW_WITHOUT_OPENING);

        try {
            TermuxClient.send(this, intent);
            Toast.makeText(this, R.string.opencode_launching, Toast.LENGTH_SHORT).show();
            openTermux();
        } catch (Exception e) {
            Toast.makeText(this,
                    getString(R.string.opencode_launch_error, safeMessage(e)),
                    Toast.LENGTH_LONG).show();
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
            if (!(grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
            }
            updateStatus();
        }
    }

    private int nextRequestId() {
        requestCounter = (requestCounter % 100000) + 1;
        return requestCounter;
    }

    private void setPending(boolean value) {
        pending = value;
        updateSendState();
    }

    private void updateSendState() {
        boolean enabled = !pending
                && messageInput.getText().toString().trim().length() > 0;
        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1f : 0.45f);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
