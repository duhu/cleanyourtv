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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The weather page of Home (res/layout/weather_page.xml): the current conditions with today's range
 * and the date, the next hours and cards for the next days, all from the feed's snapshot. Nothing
 * here takes focus. Shows a waiting line until the first weather arrives.
 */
public class WeatherPageView extends LinearLayout {
    private static final int MAX_HOURS = 12, MAX_DAYS = 3;

    private final View waiting, top;
    private final ImageView nowIcon;
    private final TextView nowTemp, nowCond, nowHiLo, date;
    private final LinearLayout hours, days;
    private final float density;
    private final Typeface digits;
    private StandbyData.StandbySnapshot snapshot = StandbyData.StandbySnapshot.EMPTY;

    public WeatherPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.weather_page, this, true);
        density = getResources().getDisplayMetrics().density;
        digits = getResources().getFont(R.font.outfit_light);
        waiting = findViewById(R.id.wxWaiting);
        top = findViewById(R.id.wxTop);
        nowIcon = findViewById(R.id.wxNowIcon);
        nowTemp = findViewById(R.id.wxNowTemp);
        nowCond = findViewById(R.id.wxNowCond);
        nowHiLo = findViewById(R.id.wxNowHiLo);
        date = findViewById(R.id.wxDate);
        hours = findViewById(R.id.wxHours);
        days = findViewById(R.id.wxDays);
    }

    void render(StandbyData.StandbySnapshot s) {
        snapshot = s == null ? StandbyData.StandbySnapshot.EMPTY : s;
        redraw();
    }

    /** Once a minute, and when the page comes on screen: the "now" column, the date and the day names follow the clock. */
    void tick() {
        redraw();
    }

    private void redraw() {
        StandbyData.WeatherInfo w = snapshot.weather;
        waiting.setVisibility(w == null ? VISIBLE : GONE);
        top.setVisibility(w == null ? GONE : VISIBLE);
        if (w == null) {
            hours.setVisibility(GONE);
            days.setVisibility(GONE);
            return;
        }
        Context ctx = getContext();
        Locale locale = Locale.getDefault();
        nowIcon.setImageResource(StatusCells.icon(w.kind));
        nowTemp.setText(degrees(w.tempC));
        String label = w.text != null ? w.text : StatusCells.weatherText(ctx, w.kind);
        nowCond.setText(label);

        StandbyData.Forecast f = snapshot.forecast;
        boolean range = f != null && !Double.isNaN(f.todayMaxC) && !Double.isNaN(f.todayMinC);
        nowHiLo.setVisibility(range ? VISIBLE : GONE);
        if (range) nowHiLo.setText(ctx.getString(R.string.home_hilo, (int) Math.round(f.todayMaxC), (int) Math.round(f.todayMinC)));

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        date.setText(today.format(DateTimeFormatter.ofPattern(ctx.getString(R.string.standby_date_pattern), locale)) + " "
                + today.format(DateTimeFormatter.ofPattern(ctx.getString(R.string.standby_weekday_pattern), locale)));

        hours.removeAllViews();
        days.removeAllViews();
        List<StandbyData.HourInfo> next = f == null ? Collections.emptyList() : f.hoursFrom(now, MAX_HOURS);
        DateTimeFormatter hourFormat = DateTimeFormatter.ofPattern(ctx.getString(R.string.home_hour_pattern), locale);
        for (int i = 0; i < next.size(); i++) {
            StandbyData.HourInfo h = next.get(i);
            hours.addView(hourColumn(i == 0 ? ctx.getString(R.string.home_hour_now) : h.time.format(hourFormat), h));
        }
        hours.setVisibility(next.isEmpty() ? GONE : VISIBLE);

        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern(ctx.getString(R.string.home_day_pattern), locale);
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
        when.setTextSize(13f);
        when.setSingleLine(true);
        col.addView(when);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(StatusCells.icon(h.kind));
        LayoutParams ip = new LayoutParams((int) (32 * density), (int) (32 * density));
        ip.topMargin = (int) (12 * density);
        col.addView(icon, ip);

        TextView temp = new TextView(ctx);
        temp.setText(degrees(h.tempC));
        temp.setTextColor(0xFFFFFFFF);
        temp.setTextSize(19f);
        temp.setTypeface(digits);
        temp.setSingleLine(true);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.topMargin = (int) (10 * density);
        col.addView(temp, tp);
        return col;
    }

    private View dayCard(String name, StandbyData.DayInfo d, boolean gapBefore) {
        Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.standby_bar);
        int pad = (int) (24 * density);
        card.setPadding(pad, 0, pad, 0);
        LayoutParams cp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        if (gapBefore) cp.setMarginStart((int) (20 * density));
        card.setLayoutParams(cp);

        TextView when = new TextView(ctx);
        when.setText(name);
        when.setTextColor(0xE6FFFFFF);
        when.setTextSize(16f);
        when.setSingleLine(true);
        card.addView(when, new LayoutParams((int) (55 * density), LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(StatusCells.icon(d.kind));
        LayoutParams ip = new LayoutParams((int) (28 * density), (int) (28 * density));
        ip.setMarginStart((int) (12 * density));
        card.addView(icon, ip);

        TextView cond = new TextView(ctx);
        cond.setText(StatusCells.weatherText(ctx, d.kind));
        cond.setTextColor(0x8CFFFFFF);
        cond.setTextSize(16f);
        cond.setSingleLine(true);
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart((int) (12 * density));
        card.addView(cond, lp);

        if (!Double.isNaN(d.minC) && !Double.isNaN(d.maxC)) {
            TextView range = new TextView(ctx);
            String low = degrees(d.minC), high = degrees(d.maxC);
            SpannableString text = new SpannableString(low + " / " + high);
            text.setSpan(new ForegroundColorSpan(0x66FFFFFF), 0, low.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            range.setText(text);
            range.setTextColor(0xFFFFFFFF);
            range.setTextSize(18f);
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
