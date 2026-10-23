package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The git page of Home (res/layout/git_page.xml): the 30-day commit total, the daily chart of all
 * shown repositories and up to eight repository cards, from the feed's GitData snapshot. Nothing
 * here takes focus. Shows a waiting line until the first snapshot arrives.
 */
public class GitPageView extends FrameLayout {
    private static final int PER_ROW = 4;
    private static final int WEEKDAY = 0x6BFFFFFF, WEEKEND = 0x33FFFFFF, TODAY = 0xFFFFFFFF;

    private final TextView waiting, total, sub, updated;
    private final View header, trend;
    private final BarChartView chart;
    private final LinearLayout axis, rows;
    private final float density;
    private final Typeface light, medium;
    private GitData.Snapshot snapshot;

    public GitPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.git_page, this, true);
        density = getResources().getDisplayMetrics().density;
        light = getResources().getFont(R.font.outfit_light);
        medium = getResources().getFont(R.font.outfit_medium);
        waiting = findViewById(R.id.gitWaiting);
        header = findViewById(R.id.gitHeader);
        total = findViewById(R.id.gitTotal);
        sub = findViewById(R.id.gitSub);
        trend = findViewById(R.id.gitTrend);
        updated = findViewById(R.id.gitUpdated);
        chart = findViewById(R.id.gitChart);
        chart.setBarColors(WEEKDAY, WEEKEND, TODAY);
        axis = findViewById(R.id.gitAxis);
        rows = findViewById(R.id.gitRows);
    }

    /** A new snapshot from the feed; null keeps the waiting line. */
    void render(GitData.Snapshot s) {
        snapshot = s;
        redraw();
    }

    /** Once a minute, and when the page comes on screen: the ages follow the clock. */
    void tick() {
        redraw();
    }

    private void redraw() {
        GitData.Snapshot s = snapshot;
        boolean none = s == null || s.repos.isEmpty();
        waiting.setVisibility(none ? VISIBLE : GONE);
        header.setVisibility(none ? GONE : VISIBLE);
        trend.setVisibility(none ? GONE : VISIBLE);
        rows.setVisibility(none ? GONE : VISIBLE);
        if (s == null) {
            waiting.setText(R.string.git_waiting);
            return;
        }
        if (none) {
            waiting.setText(R.string.git_none);
            return;
        }
        Context ctx = getContext();
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        total.setText(String.valueOf(s.total()));
        sub.setText(ctx.getString(R.string.git_sub, s.activeRepos(), s.thisWeek(), s.lastWeek()));
        updated.setText(ctx.getString(R.string.git_updated, StatusCells.ageText(ctx, StandbyData.age(s.asOfMillis, now))));
        boolean[] weekend = weekends(s.asOfMillis, zone);
        chart.setValues(s.dailyTotals(), weekend);
        drawAxis(s.asOfMillis, zone);

        rows.removeAllViews();
        List<GitData.Repo> repos = s.repos;
        for (int start = 0; start < repos.size(); start += PER_ROW) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(124));
            if (start > 0) rp.topMargin = dp(12);
            rows.addView(row, rp);
            for (int i = start; i < Math.min(repos.size(), start + PER_ROW); i++) {
                row.addView(card(repos.get(i), weekend, now), cardParams(i > start));
            }
            // Keep the cards of a short row the same width as a full one.
            for (int i = Math.min(repos.size(), start + PER_ROW); i < start + PER_ROW; i++) {
                row.addView(new View(ctx), cardParams(true));
            }
        }
    }

    private LinearLayout.LayoutParams cardParams(boolean gapBefore) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        if (gapBefore) p.setMarginStart(dp(12));
        return p;
    }

    private static boolean[] weekends(long asOf, ZoneId zone) {
        boolean[] out = new boolean[GitData.DAYS];
        for (int i = 0; i < GitData.DAYS; i++) out[i] = GitData.isWeekend(asOf, i, zone);
        return out;
    }

    /** Five labels: the first day, then every seven days, and "today". */
    private void drawAxis(long asOf, ZoneId zone) {
        Context ctx = getContext();
        axis.removeAllViews();
        LocalDate today = Instant.ofEpochMilli(asOf).atZone(zone).toLocalDate();
        DateTimeFormatter f = DateTimeFormatter.ofPattern(ctx.getString(R.string.standby_date_pattern), Locale.getDefault());
        int[] offsets = {GitData.DAYS - 1, GitData.DAYS - 8, GitData.DAYS - 15, GitData.DAYS - 22, 0};
        for (int i = 0; i < offsets.length; i++) {
            TextView t = new TextView(ctx);
            t.setText(offsets[i] == 0 ? ctx.getString(R.string.git_today) : today.minusDays(offsets[i]).format(f));
            t.setTextColor(0x66FFFFFF);
            t.setTextSize(11f);
            t.setSingleLine(true);
            t.setGravity(i == 0 ? Gravity.START : i == offsets.length - 1 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
            axis.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private View card(GitData.Repo r, boolean[] weekend, long now) {
        Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.glass_panel);
        card.setPadding(dp(15), dp(12), dp(15), dp(11));
        GitData.Tier tier = r.tier();
        if (tier == GitData.Tier.COLD) card.setAlpha(0.55f);

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(tier == GitData.Tier.HOT ? 0xFF4CD964 : tier == GitData.Tier.WARM ? 0xFFFFB340 : 0x66FFFFFF);
        dot.setBackground(d);
        head.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView name = new TextView(ctx);
        name.setText(r.name);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(16f);
        name.setTypeface(medium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        np.setMarginStart(dp(8));
        head.addView(name, np);
        card.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // The namespace on the left and the 30-day count on the right, so a long name keeps the whole first line.
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView ns = new TextView(ctx);
        ns.setText(r.namespace);
        ns.setTextColor(0x66FFFFFF);
        ns.setTextSize(10f);
        ns.setTypeface(light);
        ns.setSingleLine(true);
        ns.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(ns, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = new TextView(ctx);
        String n = String.valueOf(r.total());
        String unit = ctx.getString(R.string.git_commits_unit);
        SpannableString text = new SpannableString(n + " " + unit);
        text.setSpan(new RelativeSizeSpan(0.6f), n.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(0x66FFFFFF), n.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        count.setText(text);
        count.setTextColor(0xE6FFFFFF);
        count.setTextSize(17f);
        count.setTypeface(light);
        count.setSingleLine(true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMarginStart(dp(8));
        line.addView(count, cp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(1);
        card.addView(line, lp);

        BarChartView spark = new BarChartView(ctx);
        spark.setBarColors(WEEKDAY, WEEKEND, TODAY);
        spark.setValues(r.daily, weekend);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(26));
        sp.topMargin = dp(6);
        card.addView(spark, sp);

        card.addView(new View(ctx), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = new LinearLayout(ctx);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        if (r.lastAtMillis > 0) {
            TextView who = new TextView(ctx);
            who.setText(r.lastAuthor + " · " + StatusCells.ageText(ctx, StandbyData.age(r.lastAtMillis, now)));
            who.setTextColor(0xB3FFFFFF);
            who.setTextSize(11.5f);
            who.setSingleLine(true);
            foot.addView(who, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView title = new TextView(ctx);
            title.setText(r.lastTitle);
            title.setTextColor(0x8CFFFFFF);
            title.setTextSize(11.5f);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tp.setMarginStart(dp(8));
            foot.addView(title, tp);
        } else {
            foot.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        if (r.ci != StandbyData.CiStatus.UNKNOWN) {
            TextView ci = new TextView(ctx);
            ci.setText(r.ci == StandbyData.CiStatus.SUCCESS ? R.string.git_ci_ok : r.ci == StandbyData.CiStatus.FAILED ? R.string.git_ci_failed : R.string.git_ci_running);
            ci.setTextColor(StatusCells.statusColor(r.ci));
            ci.setTextSize(10f);
            ci.setTypeface(light);
            ci.setSingleLine(true);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ip.setMarginStart(dp(8));
            foot.addView(ci, ip);
        }
        card.addView(foot, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }
}
