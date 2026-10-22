package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The row of home pages (docs/superpowers/specs/2026-10-22-home-pager-design.md). The page views are
 * its children in the layout; {@link #setPages} says which of them take part and in what order.
 * One page is on screen; the others are INVISIBLE (so they take no focus) at translation 0. A move
 * slides the leaving page out and the arriving one in from the other side over {@link #SLIDE_MS},
 * or switches at once without animations.
 */
public class PagerView extends FrameLayout {
    static final long SLIDE_MS = 350;

    interface Listener {
        /** The page at index is on screen (after the slide, or at once). */
        void onPage(int index);
    }

    private final List<View> pages = new ArrayList<>();
    private int current = -1;
    private boolean animations;
    private boolean sliding;
    private Listener listener;

    public PagerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param pageViews children of this view, in Left-to-Right order; the other children are hidden
     * @param start     the page shown now, without animation
     */
    void setPages(List<View> pageViews, int start, boolean animations) {
        this.animations = animations;
        pages.clear();
        pages.addAll(pageViews);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.animate().cancel();
            child.setTranslationX(0f);
            child.setVisibility(pages.contains(child) ? INVISIBLE : GONE);
        }
        current = -1;
        show(start, false);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    int current() {
        return current;
    }

    int count() {
        return pages.size();
    }

    View page(int index) {
        return index >= 0 && index < pages.size() ? pages.get(index) : null;
    }

    /** True while the view at index is the page on screen (or arriving). */
    boolean showing(View page) {
        return current >= 0 && pages.get(current) == page;
    }

    /** One page to the left (delta -1) or right (+1); false at the ends. */
    boolean move(int delta) {
        int target = current + delta;
        if (target < 0 || target >= pages.size()) return false;
        show(target, true);
        return true;
    }

    /** Brings the page at index on screen; false when it already is, or the index is out of range. */
    boolean show(int index, boolean animate) {
        if (index < 0 || index >= pages.size()) return false;
        if (sliding) finishSlide();
        if (index == current) return false;
        View from = current >= 0 ? pages.get(current) : null;
        View to = pages.get(index);
        int dir = index > current ? 1 : -1;
        int width = getWidth();
        current = index;
        if (!animate || !animations || width == 0 || from == null) {
            if (from != null) from.setVisibility(INVISIBLE);
            to.setTranslationX(0f);
            to.setVisibility(VISIBLE);
            if (listener != null) listener.onPage(index);
            return true;
        }
        sliding = true;
        to.setTranslationX(dir * width);
        to.setVisibility(VISIBLE);
        to.animate().translationX(0f).setDuration(SLIDE_MS).setInterpolator(new DecelerateInterpolator(2f))
                .withEndAction(() -> {
                    if (!sliding) return;
                    sliding = false;
                    if (listener != null) listener.onPage(current);
                }).start();
        from.animate().translationX(-dir * width).setDuration(SLIDE_MS).setInterpolator(new DecelerateInterpolator(2f))
                .withEndAction(() -> {
                    from.setVisibility(INVISIBLE);
                    from.setTranslationX(0f);
                }).start();
        return true;
    }

    /** Jumps both pages to where the running slide was heading. */
    private void finishSlide() {
        sliding = false;
        for (View page : pages) {
            page.animate().cancel();
            page.setTranslationX(0f);
            page.setVisibility(page == pages.get(current) ? VISIBLE : INVISIBLE);
        }
        if (listener != null) listener.onPage(current);
    }
}
