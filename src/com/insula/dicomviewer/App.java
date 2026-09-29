/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

public class App extends Application implements Application.ActivityLifecycleCallbacks {
    static boolean unlocked, prompting, external;
    static long bgSince;
    int started;

    @Override public void onCreate() {
        super.onCreate();
        Library.init(this);
        registerActivityLifecycleCallbacks(this);
    }

    public void onActivityStarted(Activity a) { started++; }
    public void onActivityStopped(Activity a) {
        started--;
        if (started <= 0) { started = 0; bgSince = SystemClock.elapsedRealtime(); }
    }
    public void onActivityCreated(Activity a, Bundle b) { }
    public void onActivityResumed(Activity a) { }
    public void onActivityPaused(Activity a) { }
    public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    public void onActivityDestroyed(Activity a) { }
}
