/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TagActivity extends BaseActivity {
    final List<String> all = new ArrayList<>(), shown = new ArrayList<>();
    ArrayAdapter<String> adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.CHROME);
        LinearLayout top = Ui.topBar(this);
        top.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        top.addView(Ui.title(this, "  DICOM tags"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);
        EditText q = Ui.field(this, "Filter by tag, name, or value");
        root.addView(q);
        ListView lv = new ListView(this);
        lv.setBackgroundColor(Ui.PANEL);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, shown) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                t.setTypeface(Typeface.MONOSPACE);
                t.setTextSize(11.5f);
                t.setTextColor(Ui.TEXT);
                t.setMinHeight(0);
                t.setPadding(Ui.dp(TagActivity.this, 8), Ui.dp(TagActivity.this, 3), Ui.dp(TagActivity.this, 8), Ui.dp(TagActivity.this, 3));
                return t;
            }
        };
        lv.setAdapter(adapter);
        root.addView(lv, Ui.vweight(1));
        setContentView(root);

        String path = getIntent().getStringExtra("path");
        try {
            Dicom.DataSet ds = Dicom.parse(Library.readFile(new File(path)));
            all.add("Transfer syntax: " + Dicom.tsName(ds.transferSyntax));
            add(ds, 0);
        } catch (Exception e) {
            all.add("Could not read file: " + e.getMessage());
        }
        shown.addAll(all);
        adapter.notifyDataSetChanged();
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) {
                String f = s.toString().toLowerCase(Locale.ROOT).trim();
                shown.clear();
                for (String l : all) if (f.isEmpty() || l.toLowerCase(Locale.ROOT).contains(f)) shown.add(l);
                adapter.notifyDataSetChanged();
            }
            public void afterTextChanged(Editable s) { }
        });
    }

    void add(Dicom.DataSet ds, int depth) {
        if (all.size() > 20000) return;
        StringBuilder ind = new StringBuilder();
        for (int i = 0; i < depth; i++) ind.append("  ");
        for (Dicom.Element e : ds.map.values()) {
            all.add(String.format("%s(%04X,%04X) %s  %s: %s", ind, e.tag >>> 16, e.tag & 0xFFFF, e.vr, Dicom.Dict.name(e.tag), ds.valueString(e, 200)));
            if (e.items != null) {
                int i = 0;
                for (Dicom.DataSet it : e.items) {
                    all.add(ind + "  › Item " + (++i));
                    add(it, depth + 2);
                    if (all.size() > 20000) { all.add("(truncated)"); return; }
                }
            }
        }
    }
}
