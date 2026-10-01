/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Pages side by side that follow a horizontal swipe, like tabs you can drag between. Only clearly horizontal
 * drags are taken, so vertical scrolling and taps in the pages keep working.
 */
final class SwipePager extends FrameLayout {
    interface Listener { void onPage(int page); }

    Listener listener;
    int current;
    float downX, downY, offset;
    boolean dragging;
    VelocityTracker vt;
    final int slop, minFling;

    SwipePager(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        minFling = Ui.dp(c, 450);
    }

    int pages() { return getChildCount(); }

    /** Shows page i, sliding to it if animate is true. */
    void setPage(int i, boolean animate) {
        i = Math.max(0, Math.min(pages() - 1, i));
        if (animate && getWidth() > 0 && i != current) { settle(i); return; }
        stopAnimations();
        current = i;
        offset = 0;
        place(0);
        showOnly(i, -1);
    }

    /**
     * Pages other than those on screen are INVISIBLE (not GONE), so they stay laid out and appear instantly,
     * without a blank first frame.
     */
    void showOnly(int a, int b) {
        for (int k = 0; k < pages(); k++) getChildAt(k).setVisibility(k == a || k == b ? VISIBLE : INVISIBLE);
    }

    void place(float off) {
        int w = getWidth();
        for (int k = 0; k < pages(); k++) getChildAt(k).setTranslationX((k - current) * w + off);
    }

    int settling = -1;

    /** Stops a slide in progress, keeping the pages where they are, so a new swipe can start smoothly. */
    void stopAnimations() {
        for (int k = 0; k < pages(); k++) getChildAt(k).animate().cancel();
        if (settling >= 0) {
            // Continue from wherever the slide had reached.
            View t = getChildAt(settling);
            current = settling;
            offset = t.getTranslationX();
            settling = -1;
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); dragging = false;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                boolean canMove = (dx < 0 && current < pages() - 1) || (dx > 0 && current > 0);
                if (!dragging && Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.6f && canMove) {
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    stopAnimations();
                    // Start the drag from the current finger position, with the neighbour already placed off-screen
                    // before it is shown (showing it first drew it over the current page for one frame).
                    downX = e.getX() - offset;
                    drag(e.getX());
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    /** Positions the pages for a finger at x, then shows the neighbour being revealed. */
    void drag(float x) {
        float dx = x - downX;
        if ((current == 0 && dx > 0) || (current == pages() - 1 && dx < 0)) dx *= 0.3f;   // resistance at the ends
        offset = dx;
        place(offset);
        int next = dx < 0 ? current + 1 : current - 1;
        showOnly(current, next >= 0 && next < pages() ? next : -1);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return true;
        if (vt != null) vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                drag(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float v = 0;
                if (vt != null) { vt.computeCurrentVelocity(1000); v = vt.getXVelocity(); vt.recycle(); vt = null; }
                int target = current;
                if ((offset < -getWidth() * 0.25f || v < -minFling) && current < pages() - 1) target = current + 1;
                else if ((offset > getWidth() * 0.25f || v > minFling) && current > 0) target = current - 1;
                dragging = false;
                settle(target);
                return true;
            }
        }
        return true;
    }

    /** Slides from where the pages are now to page 'target'; finishes when the animation itself ends. */
    void settle(final int target) {
        final int from = current;
        int w = getWidth();
        // Place everything first, then make sure the target is visible. Pages already on screen stay visible (a
        // half-revealed neighbour slides back out rather than vanishing); hiding happens when the slide ends.
        place(offset);
        getChildAt(target).setVisibility(VISIBLE);
        settling = target;
        long ms = Math.max(120, Math.min(240, (long) (240 * Math.abs((getChildAt(target).getTranslationX())) / Math.max(1, w))));
        for (int k = 0; k < pages(); k++) {
            View p = getChildAt(k);
            android.view.ViewPropertyAnimator an = p.animate().translationX((k - target) * w).setDuration(ms)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
            if (k == target) an.withEndAction(new Runnable() {
                public void run() {
                    if (settling != target) return;   // a new swipe took over
                    settling = -1;
                    current = target;
                    offset = 0;
                    place(0);
                    showOnly(target, -1);
                    if (target != from && listener != null) listener.onPage(target);
                }
            });
            an.start();
        }
    }
}
