package com.termuxpanel;

public final class CommandResult {
    public final String stdout;
    public final String stderr;
    public final String errorMessage;
    public final int exitCode;

    public CommandResult(String stdout, String stderr, String errorMessage, int exitCode) {
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.exitCode = exitCode;
    }
}
