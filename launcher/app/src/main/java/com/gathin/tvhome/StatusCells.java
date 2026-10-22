package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The cells of the status bar (res/layout/status_bar.xml) that the standby screen and the home screen
 * share: a coloured dot, the project name and how long ago its pipeline changed, after the fixed HDMI
 * cell the layout carries. Also the words for a weather kind, used wherever weather is shown.
 */
final class StatusCells {
    static final int MAX_CI_CELLS = 4;

    private StatusCells() {
    }

    /** Replaces every cell after the first (the HDMI hint) with one per CI entry, at most MAX_CI_CELLS. */
    static void fill(LinearLayout bar, List<StandbyData.CiInfo> ci, float density) {
        while (bar.getChildCount() > 1) bar.removeViewAt(bar.getChildCount() - 1);
        long now = System.currentTimeMillis();
        int n = 0;
        for (StandbyData.CiInfo c : ci) {
            if (n++ >= MAX_CI_CELLS) break;
            bar.addView(ciCell(bar.getContext(), density, c, now));
        }
    }

    static View ciCell(Context ctx, float density, StandbyData.CiInfo ci, long now) {
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
            age.setText(ageText(ctx, StandbyData.age(ci.updatedAtMillis, now)));
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

    static int statusColor(StandbyData.CiStatus s) {
        switch (s) {
            case SUCCESS: return 0xFF4CD964;
            case FAILED: return 0xFFFF5A5A;
            case RUNNING: return 0xFFFFB340;
            default: return 0x66FFFFFF;
        }
    }

    static String ageText(Context ctx, StandbyData.Age a) {
        switch (a.unit) {
            case StandbyData.Age.MINUTES: return ctx.getString(R.string.standby_age_minutes, a.count);
            case StandbyData.Age.HOURS: return ctx.getString(R.string.standby_age_hours, a.count);
            case StandbyData.Age.DAYS: return ctx.getString(R.string.standby_age_days, a.count);
            default: return ctx.getString(R.string.standby_age_now);
        }
    }

    static String weatherText(Context ctx, StandbyData.WeatherKind k) {
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

    /** The icon for a weather kind (res/drawable/ic_wx_*). */
    static int icon(StandbyData.WeatherKind k) {
        switch (k) {
            case CLEAR: return R.drawable.ic_wx_sun;
            case CLOUDY: return R.drawable.ic_wx_partly;
            case FOG: return R.drawable.ic_wx_fog;
            case RAIN: return R.drawable.ic_wx_rain;
            case SNOW: return R.drawable.ic_wx_snow;
            case THUNDER: return R.drawable.ic_wx_storm;
            default: return R.drawable.ic_wx_cloud;
        }
    }
}
