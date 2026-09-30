/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Newer-platform behaviour, reached by reflection so the app still builds against the API 23 SDK and runs on
 * Android 7 and later. Signatures were checked against the Android 16 (API 36) platform.
 */
final class Compat {

    /** Draws behind the status and navigation bars on every version, matching what Android 15+ enforces. */
    static void edgeToEdge(Activity a) {
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? Color.TRANSPARENT : 0x66000000);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 30) {
            try { Window.class.getMethod("setDecorFitsSystemWindows", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
        }
    }

    /** Space taken by system bars, display cutouts, and the keyboard: {left, top, right, bottom}. */
    static int[] insets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Class<?> type = Class.forName("android.view.WindowInsets$Type");
                int mask = (Integer) type.getMethod("systemBars").invoke(null)
                        | (Integer) type.getMethod("displayCutout").invoke(null)
                        | (Integer) type.getMethod("ime").invoke(null);
                Object ins = WindowInsets.class.getMethod("getInsets", int.class).invoke(in, mask);
                Class<?> ic = ins.getClass();
                return new int[]{ic.getField("left").getInt(ins), ic.getField("top").getInt(ins), ic.getField("right").getInt(ins), ic.getField("bottom").getInt(ins)};
            } catch (Throwable ignored) { }
        }
        int[] r = {in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom()};
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                Object cut = WindowInsets.class.getMethod("getDisplayCutout").invoke(in);
                if (cut != null) {
                    Class<?> cc = cut.getClass();
                    r[0] = Math.max(r[0], (Integer) cc.getMethod("getSafeInsetLeft").invoke(cut));
                    r[1] = Math.max(r[1], (Integer) cc.getMethod("getSafeInsetTop").invoke(cut));
                    r[2] = Math.max(r[2], (Integer) cc.getMethod("getSafeInsetRight").invoke(cut));
                    r[3] = Math.max(r[3], (Integer) cc.getMethod("getSafeInsetBottom").invoke(cut));
                }
            } catch (Throwable ignored) { }
        }
        return r;
    }

    /**
     * Android 16+ no longer calls onBackPressed for apps targeting API 36; it uses OnBackInvokedCallback instead.
     * Registers one that runs the given action. On Android 13–15 the platform keeps using onBackPressed, so the
     * callback is only registered on API 36+ to avoid handling Back twice. Returns the callback, or null.
     */
    static Object registerBack(Activity a, final Runnable onBack) {
        if (Build.VERSION.SDK_INT < 36) return null;
        try {
            final Class<?> cb = Class.forName("android.window.OnBackInvokedCallback");
            Object proxy = Proxy.newProxyInstance(cb.getClassLoader(), new Class<?>[]{cb}, new InvocationHandler() {
                public Object invoke(Object self, Method m, Object[] args) {
                    String n = m.getName();
                    if (n.equals("onBackInvoked")) { onBack.run(); return null; }
                    if (n.equals("hashCode")) return System.identityHashCode(self);
                    if (n.equals("equals")) return args != null && args.length == 1 && self == args[0];
                    if (n.equals("toString")) return "InsulaBackCallback";
                    return null;
                }
            });
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(a);
            Class<?> dc = Class.forName("android.window.OnBackInvokedDispatcher");
            int priority = dc.getField("PRIORITY_DEFAULT").getInt(null);
            dc.getMethod("registerOnBackInvokedCallback", int.class, cb).invoke(dispatcher, priority, proxy);
            return proxy;
        } catch (Throwable t) {
            return null;
        }
    }
}
