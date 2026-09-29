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
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Describes every tool, grouped by where it appears. */
public class GuideActivity extends BaseActivity {
    // {section} rows start with "#"; tool rows are {icon, name, description}
    static final String[][] G = {
            {"#", "Home screen", "Your library of studies, albums, and transfers."},
            {"plus", "Import studies", "The blue + button. Choose File(s) for DICOM files or ZIPs (including study sets), Folder for a copied patient CD or USB drive, Download link for a direct link to a DICOM file or ZIP, or DICOM query to search a PACS."},
            {"file", "Study card", "Shows the patient, age/sex, study description, source institution, date, size, and modality. Tap to open the viewer. Long-press for details, albums, study set export, anonymized export, or delete."},
            {"search", "Search", "Filters studies by patient name, ID, description, accession number, modality, date, or series description."},
            {"chevron", "Sort", "Orders studies by newest, oldest, patient name, modality, or size."},
            {"album", "Albums tab", "Collections you make, such as teaching files or one patient's follow-ups. A study can be in several albums. Long-press a study to add it; long-press an album to rename, delete, or export it as a study set."},
            {"download", "Transfers tab", "A log of imports, PACS downloads, and exports, with sizes, download speeds, and the reason for any failure. Useful for checking that a study arrived completely."},
            {"server", "PACS", "The server icon at the top. Search your hospitals' PACS and download studies (see PACS profiles below)."},
            {"settings", "Settings", "The gear icon at the top. Privacy, loop speed, backup and transfer, this Guide, and About."},

            {"#", "Viewer", "Reading one study."},
            {"back", "Back", "Returns to the home screen."},
            {"share", "Share or export", "Share the current image, save it as PNG or JPEG, export key images as a PDF, or export the series as an anonymized ZIP."},
            {"report", "Study details", "Patient, study, and series information, and where the study was downloaded from."},
            {"menu", "Tools menu", "Opens the tools panel on the right (see Tools menu below)."},
            {"chevron", "Series name", "Tap the name at the top left of a viewport to show another series of this study, or one from another study to compare."},
            {"pencil", "Measure and annotate", "The pencil at the top right of a viewport opens the measurement tools as a rail right below it. The pencil turns into a check mark; tap it to close the tools."},
            {"layers", "Thumbnail strip", "Series of this study with their image counts. Tap to show a series in the selected viewport; long-press for MPR, DICOM tags, or anonymized export."},
            {"info", "Overlay", "WL and WW (window level and width), SE (series number), and IM (image number of total) appear in the corners. The letters on the edges show patient orientation: A anterior, P posterior, R right, L left, H head, F feet."},
            {"move", "Gestures", "Pinch to zoom, drag with two fingers to pan, and double-tap to fit the image. One finger follows the chosen tool."},

            {"#", "Tools menu (viewer)", "Opened with the menu icon at the top right."},
            {"layers", "Scroll", "Swipe up or down to move through the images of a series."},
            {"brightness", "Brightness (window)", "Swipe left or right to change contrast (window width) and up or down to change brightness (window level)."},
            {"ruler", "Measure", "Opens the measurement toolbar."},
            {"link", "Link", "Keeps viewports on the same patient position while you scroll, when their series share a frame of reference."},
            {"loop", "Loop", "Plays the series as a movie (cine), useful for ultrasound, cardiac, or perfusion series."},
            {"speed", "Loop speed", "Slider from 1 to 60 frames per second. The default is set in Settings; series that record their own frame rate start at that rate."},
            {"lay4", "Layouts", "One viewport, two stacked, two side by side, three stacked, three in a row, or a 2 × 2 grid. Empty viewports fill with the next series."},
            {"sliders", "Presets", "Standard CT windows such as brain, subdural, stroke, lung, mediastinum, abdomen, liver, bone, and angio, plus the file's default and full range."},
            {"contrast", "Invert", "Swaps black and white."},
            {"rotate", "Rotate", "Turns the image 90° clockwise."},
            {"fliph", "Flip H", "Mirrors the image left to right. Orientation letters update to match."},
            {"flipv", "Flip V", "Mirrors the image top to bottom."},
            {"annotations", "Annotations", "Shows or hides arrows and labels."},
            {"ruler", "Measures", "Shows or hides measurements."},
            {"crossref", "Cross-references", "Draws dashed lines showing where the other viewports' current slices cut through this image."},
            {"teacher", "Teacher mode", "Hides the patient's name and IDs on screen and in exports, for teaching or presenting."},
            {"cube", "MPR (3 planes)", "Opens multiplanar reconstruction for the selected series."},
            {"tags", "DICOM tags", "Lists every tag in the current image, with search."},
            {"reset", "Reset", "Resets zoom, pan, rotation, flips, inversion, and window in all viewports."},

            {"#", "Measurement tools", "A rail under the viewport's pencil (in MPR, a bar above the tools). Long-press any icon to see its name. Measurements on stored series are saved automatically and travel with study sets."},
            {"select", "Select and edit", "Tap a measurement to select it. Drag a white handle to reshape it, or drag the measurement itself to move it."},
            {"length", "Length", "Drag between two points. Shows millimetres or centimetres using the image's pixel spacing, or pixels if the image isn't calibrated."},
            {"angle", "Angle", "Drag the first arm from its end to the vertex, then drag or tap to place the second arm."},
            {"cobb", "Cobb angle", "Drag a line along one endplate, then a second along the other. Shows the angle between them, as used for scoliosis."},
            {"ellipse", "Ellipse ROI", "Drag to draw an ellipse. Shows area, mean, standard deviation, minimum, maximum, and pixel count. For CT the values are in HU."},
            {"rect", "Rectangle ROI", "Same as the ellipse, with a rectangle."},
            {"probe", "Pixel value", "Tap or drag to read the value under the point (HU for CT, RGB for color images)."},
            {"arrow", "Arrow with label", "Drag from the point of interest outward, then type an optional label."},
            {"eraser", "Eraser", "Tap a measurement to remove it."},
            {"undo", "Undo", "Reverses the last change on this image: drawing, editing, deleting, or clearing."},
            {"star", "Key image", "Marks or unmarks the current image as key. Export key images as a PDF from Share."},
            {"clearimg", "Clear marks on this image", "Removes all measurements and annotations on the current image. Undo brings them back."},
            {"check", "Done", "Closes the tools and returns to scrolling or windowing. Tapping the check mark where the pencil was does the same."},
            {"label", "Edit label", "Appears when an arrow is selected. Changes its text."},
            {"copy", "Duplicate", "Appears when a measurement is selected. Adds a copy nearby."},
            {"trash", "Delete", "Appears when a measurement is selected. Removes it."},

            {"#", "MPR", "Multiplanar reconstruction, opened from the viewer."},
            {"target", "Crosshair", "Drag near the center to move through the volume. Drag a colored line near its dot to tilt the other two planes (oblique); do it in two views for double-oblique. Red is axial, green coronal, and yellow sagittal."},
            {"layers", "Scroll", "Swipe to page through a plane."},
            {"brightness", "Window", "Adjusts brightness and contrast, linked across planes unless you turn that off. In the 3D view it changes which densities are visible."},
            {"hand", "Pan", "Moves the image with one finger."},
            {"curve", "Draw a curve", "Tap points along a vessel, the spinal canal, or a dental arch. The fourth view shows the curved reformat straightened out; swipe it to shift the curve sideways."},
            {"ruler", "Measure", "The same measurement toolbar as the viewer. Measurements on reconstructed planes last until the planes change."},
            {"cube", "3D view", "Drag to rotate. In the menu choose MIP, Bone, Soft tissue, or Vessels."},
            {"maximize", "Maximize", "Shows the selected view alone. Tap again or use Back to return."},
            {"lay4", "Layout", "Four views, three planes, or the selected view only."},
            {"layers", "Slab", "Thin, MIP (maximum intensity, for vessels and nodules), MinIP (minimum, for airways), or Average (smooths noise), with thickness from 2 to 80 mm. Dashed lines show the slab edges."},
            {"download", "Save planes as a new series", "Reformats the whole volume along a plane at the spacing you choose and stores it as a DICOM series in the same study."},
            {"reset", "Reset orientation", "Returns to straight axial, coronal, and sagittal planes and resets the 3D view."},

            {"#", "PACS profiles", "Opened with the server icon on the home screen, or with + then DICOM query."},
            {"server", "Profile card", "Shows the current PACS. Tap to switch profiles, search all profiles at once, add a profile, or manage profiles."},
            {"settings", "Manage profiles", "Add, edit, or delete a profile: name, institution, DICOMweb address, and sign-in (username and password, access token, or none). Test connection checks the details before saving."},
            {"search", "Search", "Finds studies by patient name, ID, date or date range, and modality. The date shortcuts fill in today, yesterday, the last 7 days, or the last 30 days."},
            {"download", "Download", "Tap a result to download the study. Whole series are fetched in single requests, several at once, and progress shows images, size, and speed. The study remembers which institution it came from."},

            {"#", "Settings", "Opened with the gear icon on the home screen."},
            {"lock", "App lock", "Asks for your device PIN, pattern, or biometrics when you open the app or return to it."},
            {"shield", "Block screenshots", "Prevents screenshots and screen recording of the app."},
            {"eye", "Hide patient details in exports", "Leaves names and IDs out of exported images and PDFs."},
            {"export", "Export settings", "Saves preferences, albums, and PACS profiles to a file. PACS passwords are left out unless you protect them with a passphrase."},
            {"import", "Import settings", "Merges a settings file into this phone."},
            {"export", "Export study set", "Packs all studies, an album, or chosen studies into a ZIP with their measurements, key images, and albums, optionally anonymized."},
            {"import", "Import study set", "Adds a study set's studies and restores its measurements, key images, and albums. The set also appears as an album."},
            {"trash", "Delete all studies", "Removes every study and its measurements from this phone."},
            {"info", "About Insula", "Version, Check for updates, the disclaimer, links to the source code, releases, user guide, and privacy policy, the licence texts, and how to contact the developer. Also opens when you tap the card at the top of Settings."},
            {"download", "Check for updates", "Asks GitHub for the newest release, only when you tap it. If there is one, the download opens in your browser; install it over this version and your studies and settings are kept."},
    };

    final List<View[]> rows = new ArrayList<>();   // {rowView, sectionHeader}
    final List<String> hay = new ArrayList<>();
    TextView empty;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout screen = Ui.col(this);
        screen.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.title(this, "Guide");
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(t, Ui.wrapWeight(1));
        screen.addView(top);
        EditText q = Ui.field(this, "Search tools, e.g. Cobb, MIP, anonymize");
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, -2);
        ql.setMargins(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), 0);
        screen.addView(q, ql);

        ScrollView sv = new ScrollView(this);
        LinearLayout c = Ui.col(this);
        c.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(c);
        screen.addView(sv, Ui.vweight(1));

        View section = null;
        for (String[] g : G) {
            if (g[0].equals("#")) {
                LinearLayout h = Ui.col(this);
                TextView ht = Ui.text(this, g[1], 20, Ui.TEXT);
                ht.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                ht.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 2));
                h.addView(ht);
                h.addView(Ui.text(this, g[2], 13.5f, Ui.SUB));
                View bar = new View(this);
                bar.setBackgroundColor(Ui.ACCENT);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 3));
                bl.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
                h.addView(bar, bl);
                c.addView(h);
                section = h;
                continue;
            }
            LinearLayout r = Ui.row(this);
            r.setGravity(Gravity.TOP);
            r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            LinearLayout ic = Ui.row(this);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 22)));
            ic.addView(Ui.iconView(this, g[0], 24, Ui.TEXT));
            r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
            LinearLayout col = Ui.col(this);
            col.setPadding(Ui.dp(this, 14), 0, 0, 0);
            TextView name = Ui.text(this, g[1], 16, Ui.TEXT);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            col.addView(name);
            TextView d = Ui.text(this, g[2], 14, Ui.SUB);
            d.setLineSpacing(0, 1.15f);
            d.setPadding(0, Ui.dp(this, 3), 0, 0);
            col.addView(d);
            r.addView(col, Ui.wrapWeight(1));
            c.addView(r);
            rows.add(new View[]{r, section});
            hay.add((g[1] + " " + g[2]).toLowerCase(Locale.ROOT));
        }
        empty = Ui.text(this, "No tools match. Try a shorter word.", 15, Ui.SUB);
        empty.setPadding(0, Ui.dp(this, 30), 0, 0);
        empty.setVisibility(View.GONE);
        c.addView(empty);

        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int cc) { }
            public void onTextChanged(CharSequence s, int a, int b2, int cc) { filter(s.toString()); }
            public void afterTextChanged(Editable s) { }
        });
        setContentView(screen);
    }

    void filter(String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        java.util.Set<View> sections = new java.util.HashSet<>();
        int shown = 0;
        for (int i = 0; i < rows.size(); i++) {
            boolean on = q.isEmpty() || hay.get(i).contains(q);
            rows.get(i)[0].setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) { shown++; sections.add(rows.get(i)[1]); }
        }
        for (View[] r : rows) if (r[1] != null) r[1].setVisibility(q.isEmpty() || sections.contains(r[1]) ? View.VISIBLE : View.GONE);
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }
}
