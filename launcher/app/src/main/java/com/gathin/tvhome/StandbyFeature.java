package com.gathin.tvhome;

import android.util.Log;

/**
 * The guard around the meeting-room standby feature (StandbyView, StandbyFeed, InputWatcher): every
 * call from the home screen into it goes through {@link #run}. The first failure that is safe to
 * catch is logged once and switches the feature off for the rest of the process, so Home keeps
 * working exactly as with standby and hdmi_auto_switch false. Same shape as {@link CardFeature},
 * with its own state. Out of memory and other virtual machine errors are not caught.
 */
final class StandbyFeature {
    static final String TAG = "TvHome.Standby";

    interface Step<T> {
        T run() throws Exception;
    }

    interface Reporter {
        void failed(Throwable t);
    }

    private static boolean broken;
    static Reporter reporter = t -> Log.w(TAG, "standby switched off for this session: " + t);

    private StandbyFeature() {
    }

    static boolean working() {
        return !broken;
    }

    static <T> T run(Step<T> step, T fallback) {
        if (broken) return fallback;
        try {
            return step.run();
        } catch (Exception | LinkageError e) {
            broken = true;
            try {
                reporter.failed(e);
            } catch (Exception ignored) {
            }
            return fallback;
        }
    }

    /** For void steps. A lambda that only throws, or whose body is an expression, fits both overloads: give those a block body with a statement, or use the two-argument form. */
    static void run(Runnable step) {
        run(() -> {
            step.run();
            return null;
        }, null);
    }

    /** For tests only: forget a failure. */
    static void reset() {
        broken = false;
    }
}
