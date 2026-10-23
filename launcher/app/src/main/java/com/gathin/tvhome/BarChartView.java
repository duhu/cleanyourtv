package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * A row of bars for the git page: one per day, heights relative to the largest value, weekends
 * dimmer and the last bar (today) bright. Nothing animates and nothing is written; the big chart and
 * the card sparklines are the same view at different sizes.
 */
public class BarChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float density;
    private int[] values = new int[0];
    private boolean[] weekend = new boolean[0];
    private int weekdayColor = 0x6BFFFFFF, weekendColor = 0x33FFFFFF, todayColor = 0xFFFFFFFF;

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    /** @param weekend one flag per value, or null for none; a shorter array counts as false beyond its end */
    void setValues(int[] values, boolean[] weekend) {
        this.values = values == null ? new int[0] : values.clone();
        this.weekend = weekend == null ? new boolean[0] : weekend.clone();
        invalidate();
    }

    void setBarColors(int weekday, int weekend, int today) {
        weekdayColor = weekday;
        weekendColor = weekend;
        todayColor = today;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (n == 0 || w <= 0 || h <= 0) return;
        int max = 0;
        for (int v : values) max = Math.max(max, v);
        float slot = w / n;
        float gap = Math.max(density, slot / 4f);
        float width = Math.max(1f, slot - gap);
        float radius = width / 2f;
        float min = 2 * density;
        float bottom = getPaddingTop() + h;
        for (int i = 0; i < n; i++) {
            float height = max == 0 ? min : Math.max(min, values[i] / (float) max * h);
            float left = getPaddingLeft() + i * slot + gap / 2f;
            bar.set(left, bottom - height, left + width, bottom);
            boolean we = i < weekend.length && weekend[i];
            paint.setColor(i == n - 1 ? todayColor : we ? weekendColor : weekdayColor);
            // Round the top corners only: draw the rounded shape, then square off the bottom half.
            canvas.drawRoundRect(bar, radius, radius, paint);
            if (height > radius) {
                bar.top = bottom - height + radius;
                canvas.drawRect(bar, paint);
            }
        }
    }
}
