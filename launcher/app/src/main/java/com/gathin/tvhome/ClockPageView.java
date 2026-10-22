package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

/**
 * The clock page of Home (res/layout/clock_page.xml): a large clock with seconds, the date and the
 * current weather. The seconds tick and the once-a-minute drift against burn-in run only while the
 * page is on screen ({@link #setShown}). MainActivity calls in under {@link StandbyFeature#run}.
 */
public class ClockPageView extends FrameLayout {
    private static final long MINUTE_MS = 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content, plainClock, weatherSep;
    private final FlipClockView flipClock;
    private final TextView weather;
    private final float density;
    private boolean shown;
    private StandbyData.StandbySnapshot snapshot = StandbyData.StandbySnapshot.EMPTY;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            if (!shown) return;
            driftContent();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    public ClockPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.clock_page, this, true);
        density = getResources().getDisplayMetrics().density;
        content = findViewById(R.id.clockContent);
        plainClock = findViewById(R.id.clockPlain);
        flipClock = findViewById(R.id.clockFlip);
        weather = findViewById(R.id.clockWeather);
        weatherSep = findViewById(R.id.clockWeatherSep);
    }

    /** @param animations false (the launcher's animations or reduced_motion setting) sets the flip clock without flipping */
    void configure(boolean flipClock, boolean animations) {
        this.flipClock.setAnimated(animations);
        this.flipClock.setVisibility(flipClock ? VISIBLE : GONE);
        plainClock.setVisibility(flipClock ? GONE : VISIBLE);
    }

    /** The page came on screen or left it: the seconds and the drift run only while it shows. */
    void setShown(boolean shown) {
        if (this.shown == shown) return;
        this.shown = shown;
        handler.removeCallbacks(minuteTick);
        if (shown) {
            content.setTranslationX(0f);
            content.setTranslationY(0f);
            if (flipClock.getVisibility() == VISIBLE) flipClock.start();
            handler.postDelayed(minuteTick, MINUTE_MS);
        } else {
            flipClock.stop();
        }
    }

    void render(StandbyData.StandbySnapshot s) {
        snapshot = s == null ? StandbyData.StandbySnapshot.EMPTY : s;
        StandbyData.WeatherInfo w = snapshot.weather;
        if (w == null) {
            weather.setVisibility(GONE);
            weatherSep.setVisibility(GONE);
        } else {
            String label = w.text != null ? w.text : StatusCells.weatherText(getContext(), w.kind);
            weather.setText(label.isEmpty() ? String.format(Locale.getDefault(), "%d°", Math.round(w.tempC))
                    : String.format(Locale.getDefault(), "%s %d°", label, Math.round(w.tempC)));
            weather.setVisibility(VISIBLE);
            weatherSep.setVisibility(VISIBLE);
        }
    }

    /** A small random shift once a minute so the clock never burns in. */
    private void driftContent() {
        float range = 24 * density;
        content.setTranslationX((random.nextFloat() * 2 - 1) * range);
        content.setTranslationY((random.nextFloat() * 2 - 1) * range);
    }
}
