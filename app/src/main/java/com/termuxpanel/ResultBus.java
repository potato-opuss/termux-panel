package com.termuxpanel;

/** Keeps the command result path independent from the Activity instance. */
final class ResultBus {

    interface Listener {
        void onCommandResult(int requestId, CommandResult result);
    }

    private static Listener listener;

    private ResultBus() {
    }

    static void setListener(Listener newListener) {
        listener = newListener;
    }

    static void clearListener(Listener oldListener) {
        if (listener == oldListener) {
            listener = null;
        }
    }

    static void dispatch(int requestId, CommandResult result) {
        Listener current = listener;
        if (current != null) {
            current.onCommandResult(requestId, result);
        }
    }
}
