package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The weather on the home screen (config.xml home_weather): a band with the current conditions,
 * today's range and the next hours, and a row of cards for the next days, all from the standby feed's
 * snapshot. Nothing here takes focus. Hidden until the first weather arrives, and whenever the
 * feature is off.
 */
public class HomeWeatherView extends LinearLayout {
    private static final int MAX_HOURS = 8, MAX_DAYS = 3;

    private final ImageView nowIcon;
    private final TextView nowTemp, nowCond, nowHiLo;
    private final View divider;
    private final LinearLayout hours, days;
    private final float density;
    private final Typeface digits;
    private boolean enabled;
    private StandbyData.StandbySnapshot snapshot = StandbyData.StandbySnapshot.EMPTY;

    public HomeWeatherView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.home_weather, this, true);
        density = getResources().getDisplayMetrics().density;
        digits = getResources().getFont(R.font.outfit_light);
        nowIcon = findViewById(R.id.wxNowIcon);
        nowTemp = findViewById(R.id.wxNowTemp);
        nowCond = findViewById(R.id.wxNowCond);
        nowHiLo = findViewById(R.id.wxNowHiLo);
        divider = findViewById(R.id.wxDivider);
        hours = findViewById(R.id.wxHours);
        days = findViewById(R.id.wxDays);
        setVisibility(GONE);
    }

    void configure(boolean enabled) {
        this.enabled = enabled;
        redraw();
    }

    void render(StandbyData.StandbySnapshot s) {
        snapshot = s == null ? StandbyData.StandbySnapshot.EMPTY : s;
        redraw();
    }

    /** Once a minute: the "now" column and the day names follow the clock. */
    void tick() {
        redraw();
    }

    private void redraw() {
        StandbyData.WeatherInfo w = snapshot.weather;
        if (!enabled || w == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        Context ctx = getContext();
        nowIcon.setImageResource(StatusCells.icon(w.kind));
        nowTemp.setText(degrees(w.tempC));
        String label = w.text != null ? w.text : StatusCells.weatherText(ctx, w.kind);
        nowCond.setText(label);

        StandbyData.Forecast f = snapshot.forecast;
        boolean range = f != null && !Double.isNaN(f.todayMaxC) && !Double.isNaN(f.todayMinC);
        nowHiLo.setVisibility(range ? VISIBLE : GONE);
        if (range) nowHiLo.setText(ctx.getString(R.string.home_hilo, (int) Math.round(f.todayMaxC), (int) Math.round(f.todayMinC)));

        hours.removeAllViews();
        days.removeAllViews();
        LocalDateTime now = LocalDateTime.now();
        List<StandbyData.HourInfo> next = f == null ? java.util.Collections.emptyList() : f.hoursFrom(now, MAX_HOURS);
        divider.setVisibility(next.isEmpty() ? GONE : VISIBLE);
        DateTimeFormatter hourFormat = DateTimeFormatter.ofPattern(ctx.getString(R.string.home_hour_pattern), Locale.getDefault());
        for (int i = 0; i < next.size(); i++) {
            StandbyData.HourInfo h = next.get(i);
            hours.addView(hourColumn(i == 0 ? ctx.getString(R.string.home_hour_now) : h.time.format(hourFormat), h));
        }

        LocalDate today = now.toLocalDate();
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern(ctx.getString(R.string.home_day_pattern), Locale.getDefault());
        int n = 0;
        if (f != null) {
            for (StandbyData.DayInfo d : f.days) {
                if (n >= MAX_DAYS) break;
                if (!d.date.isAfter(today)) continue;   // a stale forecast after midnight: skip what is now today
                String name = d.date.equals(today.plusDays(1)) ? ctx.getString(R.string.home_day_tomorrow) : d.date.format(dayFormat);
                days.addView(dayCard(name, d, n++ > 0));
            }
        }
        days.setVisibility(n > 0 ? VISIBLE : GONE);
    }

    private View hourColumn(String label, StandbyData.HourInfo h) {
        Context ctx = getContext();
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView when = new TextView(ctx);
        when.setText(label);
        when.setTextColor(0x8CFFFFFF);
        when.setTextSize(11f);
        when.setSingleLine(true);
        col.addView(when);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(StatusCells.icon(h.kind));
        LayoutParams ip = new LayoutParams((int) (22 * density), (int) (22 * density));
        ip.topMargin = (int) (7 * density);
        col.addView(icon, ip);

        TextView temp = new TextView(ctx);
        temp.setText(degrees(h.tempC));
        temp.setTextColor(0xFFFFFFFF);
        temp.setTextSize(15f);
        temp.setTypeface(digits);
        temp.setSingleLine(true);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.topMargin = (int) (6 * density);
        col.addView(temp, tp);
        return col;
    }

    private View dayCard(String name, StandbyData.DayInfo d, boolean gapBefore) {
        Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.standby_bar);
        int pad = (int) (18 * density);
        card.setPadding(pad, 0, pad, 0);
        LayoutParams cp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        if (gapBefore) cp.setMarginStart((int) (16 * density));
        card.setLayoutParams(cp);

        TextView when = new TextView(ctx);
        when.setText(name);
        when.setTextColor(0xE6FFFFFF);
        when.setTextSize(14f);
        when.setSingleLine(true);
        card.addView(when);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(StatusCells.icon(d.kind));
        LayoutParams ip = new LayoutParams((int) (22 * density), (int) (22 * density));
        ip.setMarginStart((int) (12 * density));
        card.addView(icon, ip);

        TextView cond = new TextView(ctx);
        cond.setText(StatusCells.weatherText(ctx, d.kind));
        cond.setTextColor(0x8CFFFFFF);
        cond.setTextSize(14f);
        cond.setSingleLine(true);
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart((int) (11 * density));
        card.addView(cond, lp);

        if (!Double.isNaN(d.minC) && !Double.isNaN(d.maxC)) {
            TextView range = new TextView(ctx);
            String low = degrees(d.minC), high = degrees(d.maxC);
            SpannableString text = new SpannableString(low + " / " + high);
            text.setSpan(new ForegroundColorSpan(0x66FFFFFF), 0, low.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            range.setText(text);
            range.setTextColor(0xFFFFFFFF);
            range.setTextSize(15f);
            range.setTypeface(digits);
            range.setSingleLine(true);
            card.addView(range);
        }
        return card;
    }

    private static String degrees(double c) {
        return String.format(Locale.getDefault(), "%d°", Math.round(c));
    }
}
