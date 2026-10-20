package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

/**
 * The meeting-room standby screen: a clock, the date and weather, and a status bar, that fades in
 * over Home after {@link StandbyConfig#idleSeconds} without a key press and fades out on the next
 * key, which it swallows. Also hosts the HDMI toast shown just before an automatic input switch.
 * MainActivity forwards keys, user interaction and its lifecycle; every call goes through
 * {@link StandbyFeature#run} there, so a failure here switches the feature off, never Home.
 */
public class StandbyView extends FrameLayout {
    private static final long FADE_IN_MS = 600, FADE_OUT_MS = 300, TOAST_MS = 1500, MINUTE_MS = 60_000;
    private static final int MAX_CI_CELLS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View layer, content, toast, toastProgress;
    private final TextView weather, toastBadge, toastTitle;
    private final View weatherSep;
    private final LinearLayout bar;
    private final float density;
    private long idleMs;
    private boolean enabled;
    private boolean showing;
    /** Set while the layer is in its fade-out: the key that woke Home is consumed on key up too. */
    private boolean swallowingKey;
    private StandbyData.StandbySnapshot snapshot = StandbyData.StandbySnapshot.EMPTY;

    private final Runnable showAfterIdle = this::show;
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            if (!showing) return;
            driftContent();
            renderBar();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    public StandbyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.standby, this, true);
        density = getResources().getDisplayMetrics().density;
        layer = findViewById(R.id.standbyLayer);
        content = findViewById(R.id.standbyContent);
        weather = findViewById(R.id.standbyWeather);
        weatherSep = findViewById(R.id.standbyWeatherSep);
        bar = findViewById(R.id.standbyBar);
        toast = findViewById(R.id.standbyToast);
        toastBadge = findViewById(R.id.standbyToastBadge);
        toastTitle = findViewById(R.id.standbyToastTitle);
        toastProgress = findViewById(R.id.standbyToastProgress);
        toastProgress.setPivotX(0);
        setVisibility(GONE);
    }

    void configure(StandbyConfig cfg) {
        enabled = cfg.enabled;
        idleMs = cfg.idleSeconds * 1000L;
    }

    /** A key press or other interaction on Home: the idle clock starts over. */
    void touch() {
        if (!enabled || showing) return;
        handler.removeCallbacks(showAfterIdle);
        handler.postDelayed(showAfterIdle, idleMs);
    }

    /** Home is leaving the screen: nothing of ours stays up, and no fade-in while another app is in front. */
    void cancel() {
        handler.removeCallbacks(showAfterIdle);
        handler.removeCallbacks(minuteTick);
        if (showing) {
            showing = false;
            layer.animate().cancel();
            layer.setAlpha(0f);
            layer.setVisibility(GONE);
        }
        if (toast.getVisibility() != VISIBLE) setVisibility(GONE);
    }

    boolean isShowing() {
        return showing;
    }

    /**
     * Keys while the standby layer is up: the first one hides it and is consumed, as is its key-up
     * and any repeat, so the launcher underneath never sees it.
     */
    boolean consumeKey(KeyEvent event) {
        if (showing) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                swallowingKey = true;
                hide();
            }
            return true;
        }
        if (swallowingKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) swallowingKey = false;
            return true;
        }
        return false;
    }

    void render(StandbyData.StandbySnapshot s) {
        snapshot = s == null ? StandbyData.StandbySnapshot.EMPTY : s;
        StandbyData.WeatherInfo w = snapshot.weather;
        if (w == null) {
            weather.setVisibility(GONE);
            weatherSep.setVisibility(GONE);
        } else {
            String label = w.text != null ? w.text : weatherText(w.kind);
            weather.setText(label.isEmpty() ? String.format(Locale.getDefault(), "%d°", Math.round(w.tempC))
                    : String.format(Locale.getDefault(), "%s %d°", label, Math.round(w.tempC)));
            weather.setVisibility(VISIBLE);
            weatherSep.setVisibility(VISIBLE);
        }
        renderBar();
    }

    /** "HDMI 2 detected, switching…" for 1.5 s over whatever is on screen, then runs the switch. */
    void showHdmiToast(String label, Runnable then) {
        toastBadge.setText(label);
        toastTitle.setText(getContext().getString(R.string.standby_hdmi_switching, label));
        setVisibility(VISIBLE);
        toast.setVisibility(VISIBLE);
        toast.setAlpha(0f);
        toast.animate().alpha(1f).setDuration(200).start();
        toastProgress.setScaleX(0f);
        toastProgress.animate().scaleX(1f).setDuration(TOAST_MS).start();
        handler.postDelayed(() -> {
            toast.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                toast.setVisibility(GONE);
                if (!showing) setVisibility(GONE);
            }).start();
            then.run();
        }, TOAST_MS);
    }

    private void show() {
        if (!enabled || showing) return;
        showing = true;
        setVisibility(VISIBLE);
        layer.setAlpha(0f);
        layer.setVisibility(VISIBLE);
        content.setTranslationX(0f);
        content.setTranslationY(0f);
        renderBar();
        layer.animate().alpha(1f).setDuration(FADE_IN_MS).start();
        handler.postDelayed(minuteTick, MINUTE_MS);
    }

    private void hide() {
        if (!showing) return;
        showing = false;
        handler.removeCallbacks(minuteTick);
        layer.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction(() -> {
            layer.setVisibility(GONE);
            if (toast.getVisibility() != VISIBLE) setVisibility(GONE);
            touch();   // the idle clock starts again from the key that woke Home
        }).start();
    }

    /** A small random shift once a minute so the clock never burns in. */
    private void driftContent() {
        float range = 24 * density;
        content.setTranslationX((random.nextFloat() * 2 - 1) * range);
        content.setTranslationY((random.nextFloat() * 2 - 1) * range);
    }

    /** The status bar: the fixed HDMI cell, then up to MAX_CI_CELLS CI cells from the snapshot. */
    private void renderBar() {
        while (bar.getChildCount() > 1) bar.removeViewAt(bar.getChildCount() - 1);
        long now = System.currentTimeMillis();
        int n = 0;
        for (StandbyData.CiInfo ci : snapshot.ci) {
            if (n++ >= MAX_CI_CELLS) break;
            bar.addView(ciCell(ci, now));
        }
    }

    private View ciCell(StandbyData.CiInfo ci, long now) {
        Context ctx = getContext();
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (14 * density);
        cell.setPadding(pad, 0, pad, 0);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        View divider = new View(ctx);
        divider.setBackgroundColor(0x40FFFFFF);
        cell.addView(divider, new LinearLayout.LayoutParams(1, (int) (20 * density)));

        View dot = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(statusColor(ci.status));
        dot.setBackground(d);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams((int) (7 * density), (int) (7 * density));
        dp.setMarginStart(pad);
        cell.addView(dot, dp);

        TextView name = new TextView(ctx);
        name.setText(ci.name);
        name.setTextColor(0xE6FFFFFF);
        name.setTextSize(13f);
        name.setSingleLine(true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        np.setMarginStart((int) (9 * density));
        cell.addView(name, np);

        if (ci.updatedAtMillis > 0) {
            TextView age = new TextView(ctx);
            age.setText(ageText(StandbyData.age(ci.updatedAtMillis, now)));
            age.setTextColor(0x8CFFFFFF);
            age.setTextSize(11f);
            age.setSingleLine(true);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ap.setMarginStart((int) (8 * density));
            cell.addView(age, ap);
        }
        return cell;
    }

    private static int statusColor(StandbyData.CiStatus s) {
        switch (s) {
            case SUCCESS: return 0xFF4CD964;
            case FAILED: return 0xFFFF5A5A;
            case RUNNING: return 0xFFFFB340;
            default: return 0x66FFFFFF;
        }
    }

    private String ageText(StandbyData.Age a) {
        Context ctx = getContext();
        switch (a.unit) {
            case StandbyData.Age.MINUTES: return ctx.getString(R.string.standby_age_minutes, a.count);
            case StandbyData.Age.HOURS: return ctx.getString(R.string.standby_age_hours, a.count);
            case StandbyData.Age.DAYS: return ctx.getString(R.string.standby_age_days, a.count);
            default: return ctx.getString(R.string.standby_age_now);
        }
    }

    private String weatherText(StandbyData.WeatherKind k) {
        Context ctx = getContext();
        switch (k) {
            case CLEAR: return ctx.getString(R.string.weather_clear);
            case CLOUDY: return ctx.getString(R.string.weather_cloudy);
            case OVERCAST: return ctx.getString(R.string.weather_overcast);
            case FOG: return ctx.getString(R.string.weather_fog);
            case RAIN: return ctx.getString(R.string.weather_rain);
            case SNOW: return ctx.getString(R.string.weather_snow);
            case THUNDER: return ctx.getString(R.string.weather_thunder);
            default: return "";
        }
    }
}
