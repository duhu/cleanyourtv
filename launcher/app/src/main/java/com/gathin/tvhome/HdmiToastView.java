package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * "HDMI 2 detected, switching…" (res/layout/hdmi_toast.xml) for 1.5 s over whatever page is on
 * screen, then the switch itself. GONE except while showing.
 */
public class HdmiToastView extends FrameLayout {
    private static final long TOAST_MS = 1500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View panel, progress;
    private final TextView badge, title;

    public HdmiToastView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.hdmi_toast, this, true);
        panel = findViewById(R.id.toastPanel);
        badge = findViewById(R.id.toastBadge);
        title = findViewById(R.id.toastTitle);
        progress = findViewById(R.id.toastProgress);
        progress.setPivotX(0);
        setVisibility(GONE);
    }

    void show(String label, Runnable then) {
        badge.setText(label);
        title.setText(getContext().getString(R.string.standby_hdmi_switching, label));
        setVisibility(VISIBLE);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(200).start();
        progress.setScaleX(0f);
        progress.animate().scaleX(1f).setDuration(TOAST_MS).start();
        handler.postDelayed(() -> {
            panel.animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
            then.run();
        }, TOAST_MS);
    }
}
