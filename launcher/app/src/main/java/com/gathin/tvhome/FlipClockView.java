package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.Calendar;

/**
 * A split-flap clock for the standby screen: four digit cards and a colon, each card split in a top
 * and a bottom half. When a digit changes, the top half folds down over the new digit and the new
 * bottom half unfolds after it, like a flip clock. Follows the TV's 12/24-hour setting and ticks on
 * the system's minute broadcast between {@link #start()} and {@link #stop()}.
 */
public class FlipClockView extends LinearLayout {
    private static final int CARD_W_DP = 150, CARD_H_DP = 220, GAP_DP = 14, RADIUS_DP = 14, SPLIT_DP = 3;
    private static final int CARD_COLOR = 0xFF1C1C1F, DIGIT_COLOR = 0xFFF2F2F2, COLON_COLOR = 0xFFD9D9D9;
    private static final long HALF_FLIP_MS = 280;

    private final Digit[] digits = new Digit[4];
    private boolean animated = true;
    private boolean running;
    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setTime(System.currentTimeMillis(), animated);
        }
    };

    public FlipClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        float d = getResources().getDisplayMetrics().density;
        Typeface face = getResources().getFont(R.font.outfit_light);
        for (int i = 0; i < 4; i++) {
            digits[i] = new Digit(context, d, face);
            LayoutParams lp = new LayoutParams((int) (CARD_W_DP * d), (int) (CARD_H_DP * d));
            if (i > 0) lp.setMarginStart((int) ((i == 2 ? 0 : GAP_DP) * d));
            addView(digits[i], lp);
            if (i == 1) {
                Colon colon = new Colon(context, d);
                LayoutParams cp = new LayoutParams((int) (GAP_DP * 4 * d), (int) (CARD_H_DP * d));
                addView(colon, cp);
            }
        }
        setTime(System.currentTimeMillis(), false);
    }

    /** False sets new digits without the flip (the launcher's animations or reduced_motion setting). */
    void setAnimated(boolean animated) {
        this.animated = animated;
    }

    /** Shows the current time at once and flips on every minute from now on. */
    void start() {
        setTime(System.currentTimeMillis(), false);
        if (running) return;
        running = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(tick, f);
    }

    void stop() {
        if (!running) return;
        running = false;
        getContext().unregisterReceiver(tick);
        for (Digit digit : digits) digit.settle();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void setTime(long millis, boolean flip) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        char[] now = digits(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(getContext()));
        for (int i = 0; i < 4; i++) digits[i].show(now[i], flip);
    }

    /** "HH" and "MM" as four characters; 12-hour mode shows 12 for midnight and noon, zero-padded. */
    static char[] digits(int hourOfDay, int minute, boolean is24h) {
        int h = hourOfDay;
        if (!is24h) {
            h = hourOfDay % 12;
            if (h == 0) h = 12;
        }
        return new char[]{(char) ('0' + h / 10), (char) ('0' + h % 10), (char) ('0' + minute / 10), (char) ('0' + minute % 10)};
    }

    /** One card: the halves that stay still, the top half that folds down and the bottom half that unfolds. */
    private static final class Digit extends FrameLayout {
        private final Half topStill, bottomStill, topFold, bottomUnfold;
        private char current = ' ';
        private boolean flipping;
        private char pending;

        Digit(Context ctx, float d, Typeface face) {
            super(ctx);
            setClipChildren(false);
            topStill = add(new Half(ctx, d, face, true), Gravity.TOP);
            bottomStill = add(new Half(ctx, d, face, false), Gravity.BOTTOM);
            topFold = add(new Half(ctx, d, face, true), Gravity.TOP);
            bottomUnfold = add(new Half(ctx, d, face, false), Gravity.BOTTOM);
            bottomUnfold.setVisibility(INVISIBLE);
            float camera = 6000 * d;
            topFold.setCameraDistance(camera);
            bottomUnfold.setCameraDistance(camera);
        }

        private Half add(Half h, int gravity) {
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, 0, gravity);
            addView(h, lp);
            return h;
        }

        @Override
        protected void onMeasure(int w, int h) {
            int half = MeasureSpec.getSize(h) / 2;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                c.getLayoutParams().height = half;
            }
            super.onMeasure(w, h);
            topFold.setPivotY(half);
            bottomUnfold.setPivotY(0);
        }

        void show(char digit, boolean flip) {
            if (flipping) {
                pending = digit;
                return;
            }
            if (digit == current) return;
            if (!flip || current == ' ') {
                set(digit);
                return;
            }
            flipping = true;
            topStill.setDigit(digit);
            bottomUnfold.setDigit(digit);
            topFold.setRotationX(0);
            topFold.animate().rotationX(-90).setDuration(HALF_FLIP_MS).setInterpolator(new AccelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator a) {
                            topFold.animate().setListener(null);
                            topFold.setDigit(digit);
                            topFold.setRotationX(0);
                            bottomUnfold.setRotationX(90);
                            bottomUnfold.setVisibility(VISIBLE);
                            bottomUnfold.animate().rotationX(0).setDuration(HALF_FLIP_MS)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator b) {
                                            bottomUnfold.animate().setListener(null);
                                            bottomStill.setDigit(digit);
                                            bottomUnfold.setVisibility(INVISIBLE);
                                            current = digit;
                                            flipping = false;
                                            if (pending != 0 && pending != current) {
                                                char next = pending;
                                                pending = 0;
                                                show(next, true);
                                            }
                                        }
                                    }).start();
                        }
                    }).start();
        }

        /** Stops any flip in progress and shows the latest digit at rest. */
        void settle() {
            if (!flipping) return;
            topFold.animate().cancel();
            bottomUnfold.animate().cancel();
            flipping = false;
            char latest = pending != 0 ? pending : topStill.digit;
            pending = 0;
            set(latest);
        }

        private void set(char digit) {
            current = digit;
            topStill.setDigit(digit);
            bottomStill.setDigit(digit);
            topFold.setDigit(digit);
            topFold.setRotationX(0);
            bottomUnfold.setDigit(digit);
            bottomUnfold.setVisibility(INVISIBLE);
        }
    }

    /** The top or bottom half of a card: the card's rounded rectangle and the digit, clipped to this half. */
    private static final class Half extends View {
        private final boolean top;
        private final float radius, split;
        private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Rect bounds = new Rect();
        private static final String DIGITS = "0123456789";
        char digit = ' ';

        Half(Context ctx, float d, Typeface face, boolean top) {
            super(ctx);
            this.top = top;
            radius = RADIUS_DP * d;
            split = SPLIT_DP * d / 2;
            card.setColor(CARD_COLOR);
            text.setColor(DIGIT_COLOR);
            text.setTypeface(face);
            text.setTextAlign(Paint.Align.CENTER);
        }

        void setDigit(char c) {
            if (c == digit) return;
            digit = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            // The whole card in this half's coordinates: the other half lies above or below the view.
            float cardTop = top ? 0 : -h, cardBottom = top ? 2 * h : h;
            canvas.save();
            if (top) canvas.clipRect(0, 0, w, h - split);
            else canvas.clipRect(0, split, w, h);
            rect.set(0, cardTop, w, cardBottom);
            canvas.drawRoundRect(rect, radius, radius, card);
            text.setTextSize(cardBottom - cardTop - 2 * radius);
            // Centre the digit glyphs themselves (not the font's line box) on the card.
            text.getTextBounds(DIGITS, 0, DIGITS.length(), bounds);
            float centre = (cardTop + cardBottom) / 2;
            float baseline = centre - (bounds.top + bounds.bottom) / 2f;
            canvas.drawText(String.valueOf(digit), w / 2f, baseline, text);
            canvas.restore();
        }
    }

    /** Two dots between the hour and minute cards. */
    private static final class Colon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float r;

        Colon(Context ctx, float d) {
            super(ctx);
            paint.setColor(COLON_COLOR);
            r = 7 * d;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy - getHeight() / 7f, r, paint);
            canvas.drawCircle(cx, cy + getHeight() / 7f, r, paint);
        }
    }
}
