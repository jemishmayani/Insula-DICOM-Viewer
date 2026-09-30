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
import android.widget.ImageView;
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

            {"#", "Getting started", "First launch and the demo study."},
            {"book", "Welcome screen", "Shown once on first launch. It explains what Insula does and asks you to confirm that it isn't for primary diagnosis."},
            {"cube", "Demo study", "A synthetic CT head phantom with a prior and a current study (no real patient). Use it to try scrolling, windowing, measuring, MPR, and Compare. Load it from the welcome screen or the empty home screen; delete it like any study."},

            {"#", "Viewer", "Reading one study."},
            {"back", "Back", "Returns to the home screen."},
            {"share", "Share or export", "Share the current image, save it as PNG or JPEG, export key images as a PDF, or export the series as an anonymized ZIP."},
            {"report", "Study details", "Tap the patient name at the top to see patient, study, and series information, and where the study was downloaded from."},
            {"menu", "Tools menu", "Opens the tools panel on the right (see Tools menu below)."},
            {"chevron", "Series name", "Tap the name at the top left of a viewport to show another series of this study, or one from another study to compare."},
            {"pencil", "Measure and annotate", "The pencil at the top right of a viewport opens the measurement tools as a rail right below it. The pencil turns into a check mark; tap it to close the tools."},
            {"layers", "Thumbnail strip", "Series of this study with their image counts. Tap to show a series in the selected viewport; long-press for MPR, DICOM tags, or anonymized export."},
            {"info", "Overlay", "WL and WW (window level and width), SE (series number), and IM (image number of total) appear in the corners. The letters on the edges show patient orientation: A anterior, P posterior, R right, L left, H head, F feet."},
            {"move", "Gestures", "Pinch to zoom, drag with two fingers to pan, and double-tap to fit the image. One finger follows the chosen tool."},
            {"layers", "Flick to glide", "With the Scroll tool, a quick flick keeps scrolling through the series and slows down on its own. Touch the image to stop."},
            {"crossref", "Scrub bar", "Drag along the right edge of the image to jump anywhere in the series. The marker shows where you are."},
            {"sliders", "Long-press for presets", "Long-press the image (with the Scroll, Window, or Pan tool) to choose a window preset."},

            {"#", "Quick bar", "The row of buttons above the thumbnails: common tools in one tap."},
            {"layers", "Scroll", "Swipe up or down to move through the series."},
            {"brightness", "Window", "Drag sideways for contrast (width) and up or down for brightness (level). The setting is remembered for each series."},
            {"ruler", "Measure", "Opens the measurement tools beside the pencil."},
            {"sliders", "Presets", "Standard CT windows, the file's default, and full range."},
            {"play", "Play", "Plays the series as a movie; tap again to pause. Speed is in More."},
            {"compare", "Compare", "Shows a prior or later study of the same patient side by side, with the best-matching series (same modality, plane, and description), scrolling together."},
            {"orbit", "3D", "Opens 3D VRT for the selected series: volume rendering with automatic tissue separation, cuts, and saved sessions."},
            {"lay4", "Layout", "One to four viewports."},
            {"link", "Link", "Appears with two or more viewports. Keeps them on the same anatomy while you scroll."},
            {"align", "Align", "Appears with two or more viewports. If two studies don't line up, scroll each to the same anatomy and tap Align; from then on they scroll together from there."},
            {"reset", "Reset", "Resets zoom, pan, rotation, flips, inversion, and window, and forgets the remembered window."},
            {"menu", "More", "Opens the full tools menu."},

            {"#", "Tools menu (viewer)", "Opened with the menu icon at the top right."},
            {"layers", "Scroll", "Swipe up or down to move through the images of a series."},
            {"brightness", "Brightness (window)", "Swipe left or right to change contrast (window width) and up or down to change brightness (window level)."},
            {"ruler", "Measure", "Opens the measurement toolbar."},
            {"link", "Link", "Keeps viewports on the same anatomy while you scroll. Within a study, positions match exactly; across studies, stacks are lined up by their centres until you use Align."},
            {"align", "Align linked viewports here", "Treats the slices now shown as the same anatomy, for comparing studies acquired in different positions."},
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
            {"#", "3D VRT", "Volume rendering with automatic tissue separation. Open with 3D in the quick bar."},
            {"orbit", "Opening 3D", "Tap 3D in the viewer's quick bar, choose 3D VRT from a thumbnail's long-press menu, or use Open in 3D VRT in MPR. The series needs at least 10 slices."},
            {"info", "Quality and limited mode", "Quality is chosen for your phone: High, Standard, or Low on the graphics processor, or Basic on the processor if the phone lacks OpenGL ES 3.0. Lower levels use a coarser volume, so small vessels look softer; Basic mode renders slowly and sharpens after you let go. The label at the top left shows the level; change it in Display."},
            {"tissue", "Automatic tissue separation", "On opening, contrast CT is split into skin and fat, organs and soft tissue, vessels and heart chambers, bone, lungs, and calcium, using thresholds measured from the scan itself. Bone is found from its dense outer layer and what it encloses, so vessels touching bone stay vessels. It separates tissue types, not individual organs, and can be wrong: check it and correct it."},
            {"sliders", "Presets", "Coronary CTA, vessels only, bones, all tissues, lungs and airways, vessel MIP, and skin surface."},
            {"tissue", "Tissues", "For each class: show or hide it, tap its colour to change it, and set opacity and the density range where it appears. You can also rerun the automatic separation with your own vessel and bone thresholds."},
            {"select", "Pick", "Tap a structure to hide it, show only it (hiding the rest of its class), or move it to another class. For example, move the heart chambers from Vessels to Selection and hide them to see the coronary arteries."},
            {"scissors", "Cut", "Draw around a region with one finger. Choose Remove inside, Keep only inside, or Move visible tissue inside to a class. The cut goes through the full depth as seen from the current direction, so rotate first."},
            {"crop", "Clip", "Slide away slabs from the right, left, front, back, feet, or head."},
            {"cube", "Views", "Front, back, left, right, from the head, and from the feet."},
            {"reset", "Spin", "Turns the volume continuously around the head-feet axis. Touch to stop."},
            {"brightness", "Display", "Rendering mode (VRT, MIP, MinIP, Surface), MIP window, lighting, background, and quality."},
            {"undo", "Undo", "Steps back through picks and cuts."},
            {"camera", "Capture", "Save or share the current image, add it to the study as a DICOM image, or add a 36-view rotation series to the study that you can scroll like any series."},
            {"save", "Sessions", "Save everything, including your cuts and moved structures, inside the study as \"Insula 3D VRT states\". Open it later to continue, even after exporting and importing the study set. Delete old sessions from the same menu."},
            {"orbit", "3D gestures", "Drag to rotate, pinch to zoom, drag with two fingers to move, double-tap for the front view."},

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
            {"brightness", "Remember window settings", "On by default. Each series reopens with the brightness and contrast you last used. Reset in the quick bar forgets it."},
            {"info", "About Insula", "Version, Check for updates, the disclaimer, links to the source code, releases, user guide, and privacy policy, the licence texts, and how to contact the developer. Also opens when you tap the card at the top of Settings."},
            {"download", "Check for updates", "Asks GitHub for the newest release, only when you tap it. If there is one, the download opens in your browser; install it over this version and your studies and settings are kept."},
    };

    /** One collapsible section of the guide. */
    static final class Section {
        String title;
        LinearLayout body;
        ImageView chevron;
        TextView count;
        View header, block;
        final List<View> rows = new ArrayList<>();
        final List<String> hay = new ArrayList<>();
        boolean expanded;
    }

    final List<Section> sections = new ArrayList<>();
    TextView empty, toggleAll;
    String query = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout screen = Ui.col(this);
        screen.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.title(this, "Guide");
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(t, Ui.wrapWeight(1));
        toggleAll = Ui.text(this, "Expand all", 14, Ui.VALUE);
        toggleAll.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        toggleAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean open = !allExpanded();
                for (Section sec : sections) sec.expanded = open;
                apply();
            }
        });
        top.addView(toggleAll);
        screen.addView(top);

        EditText q = Ui.field(this, "Search all tools, e.g. Cobb, MIP, compare");
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, -2);
        ql.setMargins(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 4));
        screen.addView(q, ql);

        ScrollView sv = new ScrollView(this);
        LinearLayout c = Ui.col(this);
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 32));
        sv.addView(c);
        screen.addView(sv, Ui.vweight(1));

        Section sec = null;
        for (String[] g : G) {
            if (g[0].equals("#")) {
                sec = newSection(c, g[1], g[2]);
                continue;
            }
            if (sec == null) continue;
            View row = toolRow(g);
            sec.body.addView(row);
            sec.rows.add(row);
            sec.hay.add((g[1] + " " + g[2]).toLowerCase(Locale.ROOT));
        }
        empty = Ui.text(this, "No tools match. Try a shorter word.", 15, Ui.SUB);
        empty.setPadding(Ui.dp(this, 8), Ui.dp(this, 30), 0, 0);
        c.addView(empty);

        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int cc) { }
            public void onTextChanged(CharSequence s, int a, int b2, int cc) { query = s.toString().trim().toLowerCase(Locale.ROOT); apply(); }
            public void afterTextChanged(Editable s) { }
        });
        setContentView(screen);
        apply();
    }

    Section newSection(LinearLayout parent, String title, String desc) {
        final Section sec = new Section();
        sec.title = title;
        LinearLayout block = Ui.col(this);
        block.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 14)));
        block.setClipToOutline(true);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Ui.dp(this, 10);
        parent.addView(block, bl);

        LinearLayout h = Ui.row(this);
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        h.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF), null,
                new android.graphics.drawable.ColorDrawable(0xFFFFFFFF)));
        LinearLayout hc = Ui.col(this);
        TextView ht = Ui.text(this, title, 17, Ui.TEXT);
        ht.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hc.addView(ht);
        TextView hd = Ui.text(this, desc, 13, Ui.SUB);
        hd.setPadding(0, Ui.dp(this, 2), 0, 0);
        hc.addView(hd);
        h.addView(hc, Ui.wrapWeight(1));
        sec.count = Ui.text(this, "", 12.5f, Ui.VALUE);
        sec.count.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 6), 0);
        h.addView(sec.count);
        sec.chevron = Ui.iconView(this, "chevron", 22, Ui.TEXT);
        h.addView(sec.chevron);
        h.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sec.expanded = !sec.expanded; apply(); }
        });
        Ui.tooltip(h, "Show or hide " + title);
        block.addView(h);

        sec.body = Ui.col(this);
        sec.body.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 8));
        block.addView(sec.body);
        sec.header = h;
        sec.block = block;
        sections.add(sec);
        return sec;
    }

    View toolRow(String[] g) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        LinearLayout ic = Ui.row(this);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.rounded(Ui.BG, Ui.dp(this, 20)));
        ic.addView(Ui.iconView(this, g[0], 22, Ui.TEXT));
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(this, 14), 0, 0, 0);
        TextView name = Ui.text(this, g[1], 15.5f, Ui.TEXT);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(name);
        TextView d = Ui.text(this, g[2], 14, Ui.SUB);
        d.setLineSpacing(0, 1.15f);
        d.setPadding(0, Ui.dp(this, 3), 0, 0);
        col.addView(d);
        r.addView(col, Ui.wrapWeight(1));
        return r;
    }

    boolean allExpanded() {
        for (Section sec : sections) if (!sec.expanded) return false;
        return true;
    }

    /** Shows sections and rows for the current search and expansion state. */
    void apply() {
        boolean searching = !query.isEmpty();
        int shown = 0;
        for (Section sec : sections) {
            int matches = 0;
            for (int i = 0; i < sec.rows.size(); i++) {
                boolean on = !searching || sec.hay.get(i).contains(query) || sec.title.toLowerCase(Locale.ROOT).contains(query);
                sec.rows.get(i).setVisibility(on ? View.VISIBLE : View.GONE);
                if (on) matches++;
            }
            shown += matches;
            // While searching, sections with matches open automatically; your own open/closed choice returns after.
            boolean open = searching ? matches > 0 : sec.expanded;
            sec.block.setVisibility(searching && matches == 0 ? View.GONE : View.VISIBLE);
            sec.body.setVisibility(open ? View.VISIBLE : View.GONE);
            sec.chevron.setRotation(open ? 180 : 0);
            sec.count.setText(searching ? matches + (matches == 1 ? " match" : " matches") : sec.rows.size() + (sec.rows.size() == 1 ? " item" : " items"));
        }
        empty.setVisibility(searching && shown == 0 ? View.VISIBLE : View.GONE);
        toggleAll.setVisibility(searching ? View.GONE : View.VISIBLE);
        toggleAll.setText(allExpanded() ? "Collapse all" : "Expand all");
    }
}
