/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.PopupWindow;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

final class Ui {
    // Neutral dark chrome with a single clinical blue, so images stay the brightest thing on screen.
    static final int CANVAS = 0xFF000000, BG = 0xFF19191A, BAR = 0xFF28282A, CARD = 0xFF303033, LINE = 0xFF444448,
            CHROME = BAR, PANEL = BG, BTN = 0xFF3A3A3E, BTN_ON = 0xFF1976D2, ACCENT = 0xFF2196F3, BLUE = 0xFF1976D2,
            VALUE = 0xFF42A5F5, TEXT = 0xFFF1F1F2, SUB = 0xFFB4B4B8, WARN = 0xFFFFC266;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("insula", Context.MODE_PRIVATE); }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static RippleDrawable ripple(GradientDrawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null);
    }

    static Button btn(Context c, String t, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        b.setStateListAnimator(null);
        setOn(b, false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        b.setLayoutParams(lp);
        return b;
    }

    static void setOn(Button b, boolean on) {
        b.setBackground(ripple(rounded(on ? BTN_ON : BTN, dp(b.getContext(), 18))));
    }

    /** Full-width blue pill, e.g. "Reset". */
    static Button pill(Context c, String t, String icon, View.OnClickListener l) {
        Button b = btn(c, t, l);
        b.setBackground(ripple(rounded(BLUE, dp(c, 28))));
        b.setTextSize(16);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        if (icon != null) {
            Icons d = new Icons(icon, TEXT);
            d.setBounds(0, 0, dp(c, 24), dp(c, 24));
            b.setCompoundDrawables(d, null, null, null);
            b.setCompoundDrawablePadding(dp(c, 12));
        }
        b.setGravity(Gravity.CENTER);
        return b;
    }

    static final java.util.Map<String, String> NAMES = new java.util.HashMap<>();
    static {
        String[] kv = {"back", "Back", "share", "Share or export", "report", "Study details", "menu", "Tools menu", "search", "Search", "close", "Close",
                "more", "More options", "settings", "Settings", "pencil", "Measure and annotate", "plus", "Import studies"};
        for (int i = 0; i < kv.length; i += 2) NAMES.put(kv[i], kv[i + 1]);
    }

    /** Long-pressing the view shows its name in a small bubble (and reads it to screen readers). */
    static void tooltip(View anchor, final String text) {
        if (text == null) return;
        anchor.setContentDescription(text);
        anchor.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { showTip(v, text); return true; } });
    }

    static void showTip(final View anchor, String text) {
        Context c = anchor.getContext();
        TextView t = text(c, text, 14, TEXT);
        t.setBackground(rounded(0xF2111113, dp(c, 6)));
        t.setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7));
        t.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        final PopupWindow pw = new PopupWindow(t, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pw.setTouchable(false);
        pw.setElevation(dp(c, 8));
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int sw = c.getResources().getDisplayMetrics().widthPixels;
        int x = Math.max(dp(c, 6), Math.min(sw - t.getMeasuredWidth() - dp(c, 6), loc[0] + anchor.getWidth() / 2 - t.getMeasuredWidth() / 2));
        int y = loc[1] - t.getMeasuredHeight() - dp(c, 8);
        if (y < dp(c, 30)) y = loc[1] + anchor.getHeight() + dp(c, 8);
        try { pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y); } catch (Exception e) { return; }
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        anchor.postDelayed(new Runnable() { public void run() { try { pw.dismiss(); } catch (Exception ignored) { } } }, 1800);
    }

    /** Round icon tool button; long-press shows its name. */
    static ImageButton toolBtn(Context c, String icon, String label, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageDrawable(new Icons(icon, TEXT));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, 11);
        b.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 46), dp(c, 46));
        lp.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        b.setLayoutParams(lp);
        setToolOn(b, false);
        b.setOnClickListener(l);
        tooltip(b, label);
        return b;
    }

    static void setToolOn(View b, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? BLUE : BTN);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), g, null));
    }

    static View vsep(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, dp(c, 1)), dp(c, 28));
        lp.setMargins(dp(c, 6), 0, dp(c, 6), 0);
        v.setLayoutParams(lp);
        return v;
    }

    /** Icon above a short label, for grids of actions in menus. */
    static LinearLayout tile(Context c, String icon, String label, View.OnClickListener l) {
        LinearLayout t = col(c);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 8));
        t.setBackground(ripple(rounded(BTN, dp(c, 10))));
        ImageView iv = iconView(c, icon, 26, TEXT);
        t.addView(iv);
        TextView tv = text(c, label, 12, SUB);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(0, dp(c, 5), 0, 0);
        t.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 76), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        tooltip(t, label);
        return t;
    }

    static ImageButton icon(Context c, String name, View.OnClickListener l) { return icon(c, name, NAMES.get(name), l); }

    static ImageButton icon(Context c, String name, String label, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        tooltip(b, label);
        b.setImageDrawable(new Icons(name, TEXT));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, 12);
        b.setPadding(p, p, p, p);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 50), dp(c, 50)));
        b.setOnClickListener(l);
        return b;
    }

    static ImageView iconView(Context c, String name, int sizeDp, int color) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new Icons(name, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static LinearLayout topBar(Context c) {
        LinearLayout l = row(c);
        l.setBackgroundColor(BAR);
        l.setPadding(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 6));
        l.setMinimumHeight(dp(c, 62));
        return l;
    }

    static TextView text(Context c, String t, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    static TextView title(Context c, String t) {
        TextView v = text(c, t, 19, TEXT);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    static TextView heading(Context c, String t) {
        TextView v = text(c, t, 17, TEXT);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setPadding(0, dp(c, 18), 0, dp(c, 10));
        return v;
    }

    /** Horizontal filler for rows. A bare View with WRAP_CONTENT would claim all the space it's offered. */
    static View spacer(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
        return v;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static HorizontalScrollView hscroll(Context c, View child) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(child);
        return h;
    }

    static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(SUB);
        e.setTextSize(16);
        return e;
    }

    static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w); }
    static LinearLayout.LayoutParams wrapWeight(float w) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w); }
    static LinearLayout.LayoutParams vweight(float w) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, w); }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); }

    /** Icon + label + switch, as in a settings list. */
    static Switch switchRow(Context c, LinearLayout parent, String icon, String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = row(c);
        r.setPadding(dp(c, 4), dp(c, 12), 0, dp(c, 12));
        r.addView(iconView(c, icon, 26, TEXT));
        TextView t = text(c, label, 17, TEXT);
        t.setPadding(dp(c, 18), 0, 0, 0);
        r.addView(t, wrapWeight(1));
        Switch s = new Switch(c);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFFEEEEEE}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0xFF1E5A8C, 0xFF5A5A5E}));
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        r.addView(s);
        parent.addView(r);
        parent.addView(divider(c));
        return s;
    }

    /** Icon + label row that acts like a button. Returns the label so callers can update it. */
    static TextView actionRow(Context c, LinearLayout parent, String icon, String label, View.OnClickListener l) {
        LinearLayout r = row(c);
        r.setPadding(dp(c, 4), dp(c, 14), 0, dp(c, 14));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, new ColorDrawable(0xFFFFFFFF)));
        r.addView(iconView(c, icon, 26, TEXT));
        TextView t = text(c, label, 17, TEXT);
        t.setPadding(dp(c, 18), 0, 0, 0);
        r.addView(t, wrapWeight(1));
        r.setOnClickListener(l);
        parent.addView(r);
        parent.addView(divider(c));
        return t;
    }

    /** Row of equal icon cells with one highlighted, like a segmented control. */
    static final class Segmented {
        final LinearLayout view;
        final View[] cells;
        int selected = -1;
        interface OnPick { void pick(int i); }

        Segmented(Context c, String[] icons, final OnPick cb) {
            view = row(c);
            view.setBackgroundColor(LINE);
            view.setPadding(1, 1, 1, 1);
            cells = new View[icons.length];
            for (int i = 0; i < icons.length; i++) {
                final int k = i;
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(new Icons(icons[i], TEXT));
                int p = dp(c, 13);
                iv.setPadding(p, p, p, p);
                iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { select(k); cb.pick(k); } });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(c, 52), 1);
                if (i > 0) lp.leftMargin = 1;
                view.addView(iv, lp);
                cells[i] = iv;
            }
            select(0);
        }

        void select(int i) {
            selected = i;
            for (int k = 0; k < cells.length; k++)
                cells[k].setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), new ColorDrawable(k == i ? BLUE : BAR), null));
        }
    }

    /** Slide-in panel over a FrameLayout, from the left or right edge, with a dimming scrim. */
    static final class Sheet {
        final View scrim;
        final ScrollView panel;
        final LinearLayout content;
        final boolean right;
        final int width;
        boolean open;

        Sheet(Context c, FrameLayout root, boolean right, int widthPx) {
            this.right = right;
            this.width = widthPx;
            scrim = new View(c);
            scrim.setBackgroundColor(0x99000000);
            scrim.setVisibility(View.GONE);
            scrim.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { close(); } });
            root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            panel = new ScrollView(c);
            panel.setBackgroundColor(BAR);
            panel.setElevation(dp(c, 16));
            content = col(c);
            content.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 24));
            panel.addView(content);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = right ? Gravity.END : Gravity.START;
            root.addView(panel, lp);
            panel.setTranslationX(right ? widthPx : -widthPx);
            panel.setVisibility(View.INVISIBLE);
        }

        void open() {
            if (open) return;
            open = true;
            scrim.setAlpha(0);
            scrim.setVisibility(View.VISIBLE);
            scrim.animate().alpha(1).setDuration(180).setListener(null).start();
            panel.setVisibility(View.VISIBLE);
            panel.animate().translationX(0).setDuration(220).setListener(null).start();
        }

        void close() {
            if (!open) return;
            open = false;
            scrim.animate().alpha(0).setDuration(180).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { scrim.setVisibility(View.GONE); }
            }).start();
            panel.animate().translationX(right ? width : -width).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { if (!open) panel.setVisibility(View.INVISIBLE); }
            }).start();
        }
    }

    interface OnChoice { void choose(int i); }

    /** Dialog with a titled, icon-led list of choices (Import studies, Share, and similar). */
    static AlertDialog choices(Context c, String titleText, String subtitle, String[] icons, String[] labels, final OnChoice cb) {
        LinearLayout l = col(c);
        l.setPadding(dp(c, 28), dp(c, 24), dp(c, 28), dp(c, 8));
        TextView t = text(c, titleText, 24, TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        l.addView(t);
        View bar = new View(c);
        bar.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(c, 64), dp(c, 3));
        bl.topMargin = dp(c, 12);
        l.addView(bar, bl);
        if (subtitle != null) l.addView(heading(c, subtitle));
        else l.addView(new View(c), new LinearLayout.LayoutParams(1, dp(c, 12)));
        final AlertDialog[] d = new AlertDialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            actionRow(c, l, icons[i], labels[i], new View.OnClickListener() { public void onClick(View v) { d[0].dismiss(); cb.choose(k); } });
        }
        l.removeViewAt(l.getChildCount() - 1);
        ScrollView sv = new ScrollView(c);
        sv.addView(l);
        d[0] = new AlertDialog.Builder(c).setView(sv).setNegativeButton("Cancel", null).create();
        return d[0];
    }

    static void confirm(Context c, String msg, String action, final Runnable r) {
        new AlertDialog.Builder(c).setMessage(msg)
                .setPositiveButton(action, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { r.run(); } })
                .setNegativeButton("Cancel", null).show();
    }

    // ---------------- grouped settings cards ----------------
    static final int C_BLUE = 0xFF42A5F5, C_GREEN = 0xFF66BB6A, C_TEAL = 0xFF26A69A, C_AMBER = 0xFFFFB300, C_PURPLE = 0xFFAB47BC, C_RED = 0xFFEF5350;

    /** A titled rounded card; add rows to the returned container. */
    static LinearLayout group(Context c, LinearLayout parent, String title) {
        TextView t = text(c, title.toUpperCase(java.util.Locale.getDefault()), 12.5f, SUB);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 6), dp(c, 22), 0, dp(c, 8));
        parent.addView(t);
        LinearLayout card = col(c);
        card.setBackground(rounded(CARD, dp(c, 16)));
        card.setClipToOutline(true);
        parent.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    static View badge(Context c, String icon, int color) {
        LinearLayout b = row(c);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor((color & 0x00FFFFFF) | 0x33000000);
        b.setBackground(g);
        b.addView(iconView(c, icon, 20, color));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 38), dp(c, 38)));
        return b;
    }

    static void cardDivider(Context c, LinearLayout card) {
        if (card.getChildCount() == 0) return;
        View v = new View(c);
        v.setBackgroundColor(0xFF3E3E42);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.leftMargin = dp(c, 68);
        card.addView(v, lp);
    }

    /** Icon badge, title, optional subtitle, and an optional trailing view. Returns {row, subtitle}. */
    static View[] setting(Context c, LinearLayout card, String icon, int color, String title, String subtitle, View trailing, View.OnClickListener l) {
        cardDivider(c, card);
        LinearLayout r = row(c);
        r.setPadding(dp(c, 16), dp(c, 12), dp(c, 14), dp(c, 12));
        r.setMinimumHeight(dp(c, 64));
        r.addView(badge(c, icon, color));
        LinearLayout tc = col(c);
        tc.setPadding(dp(c, 14), 0, dp(c, 8), 0);
        TextView t = text(c, title, 16, color == C_RED ? C_RED : TEXT);
        tc.addView(t);
        TextView st = text(c, subtitle == null ? "" : subtitle, 13, SUB);
        st.setVisibility(subtitle == null ? View.GONE : View.VISIBLE);
        tc.addView(st);
        r.addView(tc, wrapWeight(1));
        if (trailing != null) r.addView(trailing);
        else if (l != null) r.addView(iconView(c, "chevronr", 20, SUB));
        if (l != null) {
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, new ColorDrawable(0xFFFFFFFF)));
            r.setOnClickListener(l);
        }
        card.addView(r);
        return new View[]{r, st};
    }

    static Switch settingSwitch(Context c, LinearLayout card, String icon, int color, String title, String subtitle, boolean on, CompoundButton.OnCheckedChangeListener l) {
        final Switch s = new Switch(c);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFFEEEEEE}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0xFF1E5A8C, 0xFF5A5A5E}));
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        View[] r = setting(c, card, icon, color, title, subtitle, s, null);
        r[0].setOnClickListener(new View.OnClickListener() { public void onClick(View v) { s.toggle(); } });
        r[0].setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, new ColorDrawable(0xFFFFFFFF)));
        return s;
    }

    interface OnValue { void value(int v); }

    /** Titled slider showing its value; min..max inclusive. */
    static android.widget.SeekBar slider(Context c, LinearLayout parent, String icon, int color, String title, final int min, int max, int value, final String unit, final OnValue cb) {
        LinearLayout box = col(c);
        box.setPadding(dp(c, 16), dp(c, 12), dp(c, 14), dp(c, 6));
        LinearLayout top = row(c);
        if (color != 0) top.addView(badge(c, icon, color)); else top.addView(iconView(c, icon, 26, TEXT));
        TextView t = text(c, title, 16, TEXT);
        t.setPadding(dp(c, 14), 0, 0, 0);
        top.addView(t, wrapWeight(1));
        final TextView val = text(c, value + " " + unit, 15, VALUE);
        val.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        top.addView(val);
        box.addView(top);
        android.widget.SeekBar sb = new android.widget.SeekBar(c);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(ACCENT));
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) { val.setText((p + min) + " " + unit); if (user) cb.value(p + min); }
            public void onStartTrackingTouch(android.widget.SeekBar s) { }
            public void onStopTrackingTouch(android.widget.SeekBar s) { }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 6);
        box.addView(sb, lp);
        LinearLayout ticks = row(c);
        ticks.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        ticks.addView(text(c, String.valueOf(min), 12, SUB), wrapWeight(1));
        ticks.addView(text(c, String.valueOf(max), 12, SUB));
        box.addView(ticks);
        if (parent.getChildCount() > 0 && parent.getBackground() != null) cardDivider(c, parent);
        parent.addView(box);
        return sb;
    }
}
