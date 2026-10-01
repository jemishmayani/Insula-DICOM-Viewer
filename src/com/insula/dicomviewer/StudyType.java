/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recognizes the kind of study from its DICOM descriptions (study, series, body part, protocol, contrast) and offers
 * tools suited to it: window presets, MPR planes, slab MIPs, measurement shortcuts, and 3D presets.
 */
final class StudyType {
    static final String CARDIAC_CTA = "cardiac-cta", CTPA = "ctpa", CTA = "cta", TRAUMA = "trauma", CVJ = "cvj", C_SPINE = "c-spine",
            SPINE = "spine", BRAIN = "brain", NECK = "neck", CHEST = "chest", ABDOMEN = "abdomen", GENERAL = "general";

    /** One quick tool. kind: win, mpr, slab, tool, short, 3d. */
    static final class Tool {
        final String kind, arg, label, icon;
        Tool(String kind, String arg, String label, String icon) { this.kind = kind; this.arg = arg; this.label = label; this.icon = icon; }
    }

    static final class Group {
        final String title;
        final List<Tool> tools = new ArrayList<>();
        Group(String t) { title = t; }
        Group add(String kind, String arg, String label, String icon) { tools.add(new Tool(kind, arg, label, icon)); return this; }
    }

    static final class Profile {
        String type = GENERAL, title = "", reason = "";
        boolean ct;
        final List<Group> groups = new ArrayList<>();
    }

    /** A named measurement with instructions and a commonly cited reference, for guidance only. */
    static final class Shortcut {
        final String id, label, how, reference;
        final int tool;
        Shortcut(String id, int tool, String label, String how, String reference) { this.id = id; this.tool = tool; this.label = label; this.how = how; this.reference = reference; }
    }

    static final Shortcut[] SHORTCUTS = {
            new Shortcut("adi", DicomView.T_LENGTH, "ADI", "Atlanto-dens interval, on a midline sagittal image: from the back of the anterior arch of C1 to the front of the dens.",
                    "Commonly cited: up to 3 mm in adults, up to 5 mm in children."),
            new Shortcut("bdi", DicomView.T_LENGTH, "BDI", "Basion-dens interval, on a midline sagittal image: from the tip of the basion to the tip of the dens.",
                    "Commonly cited on CT: up to 8.5 mm."),
            new Shortcut("chamberlain", DicomView.T_PTLINE, "Chamberlain", "Midline sagittal image. Draw Chamberlain's line from the back of the hard palate to the opisthion, then tap the tip of the dens.",
                    "Commonly cited: a dens tip more than 3 mm above the line suggests basilar invagination."),
            new Shortcut("mcgregor", DicomView.T_PTLINE, "McGregor", "Midline sagittal image. Draw McGregor's line from the back of the hard palate to the lowest point of the occiput, then tap the tip of the dens.",
                    "Commonly cited: a dens tip more than 4.5 mm above the line is abnormal."),
            new Shortcut("canal-c", DicomView.T_LENGTH, "Canal AP", "Midline sagittal or axial image: front-to-back diameter of the spinal canal, from the back of the vertebral body to the spinolaminar line.",
                    "Commonly cited for the cervical canal: under 10 mm absolute stenosis, 10 to 13 mm relative stenosis."),
            new Shortcut("canal-l", DicomView.T_LENGTH, "Canal AP", "Midline sagittal or axial image: front-to-back diameter of the spinal canal.",
                    "Commonly cited for the lumbar canal: under 10 mm absolute stenosis, 10 to 12 mm relative stenosis."),
            new Shortcut("height", DicomView.T_LENGTH, "Body height", "Sagittal image: vertebral body height (anterior, middle, or posterior). Compare with the vertebra above or below.",
                    "Genant grading of height loss: mild 20 to 25%, moderate 25 to 40%, severe over 40%."),
            new Shortcut("midline", DicomView.T_PTLINE, "Midline shift", "Axial image at the foramen of Monro. Draw the ideal midline from the front to the back attachment of the falx, then tap the septum pellucidum.",
                    "Commonly cited: a shift over 5 mm is significant."),
            new Shortcut("abc", DicomView.T_ABC, "Hematoma", "On the slice where the hematoma is largest: draw its longest diameter (A), then the widest diameter at right angles (B). You'll then enter how many slices show it (C).",
                    "Volume = A x B x C / 2. A volume of 30 mL or more is part of the ICH score."),
            new Shortcut("cobb", DicomView.T_COBB, "Cobb", "Coronal image: draw along the endplate of the most tilted vertebra above, then along the most tilted one below.",
                    "Scoliosis is usually defined as a Cobb angle of 10 degrees or more."),
    };

    static Shortcut shortcut(String id) { for (Shortcut s : SHORTCUTS) if (s.id.equals(id)) return s; return null; }

    static String norm(String s) { return " " + (s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim()) + " "; }

    static boolean any(String text, String... words) { for (String w : words) if (text.contains(" " + w + " ")) return true; return false; }

    /** Decides the study type from its text fields. */
    static String detect(String modality, String studyDesc, String seriesDesc, String bodyPart, String protocol, String contrast) {
        String t = norm(studyDesc + " " + seriesDesc + " " + bodyPart + " " + protocol);
        String joined = t.replace(" ", "");
        boolean contrastGiven = contrast != null && !contrast.trim().isEmpty();
        if (!"CT".equals(modality)) {
            if (any(t, "BRAIN", "HEAD")) return BRAIN;
            if (any(t, "SPINE", "CSPINE", "LSPINE", "TSPINE", "CERVICAL", "LUMBAR")) return any(t, "CERVICAL", "CSPINE") || joined.contains("CSPINE") ? C_SPINE : SPINE;
            return GENERAL;
        }
        if (any(t, "CORONARY", "CCTA", "CARDIAC", "HEART", "CORONARIES", "TAVI", "TAVR") || joined.contains("CTCA")) return CARDIAC_CTA;
        if (any(t, "CTPA", "PE") || t.contains(" PULMONARY ANGIO") || t.contains(" PULMONARY EMBOL")) return CTPA;
        if (any(t, "CTA", "ANGIO", "ANGIOGRAPHY", "ANGIOGRAM", "RUNOFF", "CAROTID", "CAROTIDS", "COW", "VASCULAR", "AORTOGRAM") || t.contains(" CIRCLE OF WILLIS")
                || (contrastGiven && any(t, "ARTERIAL", "AORTA"))) return CTA;
        if (any(t, "TRAUMA", "POLYTRAUMA", "PANSCAN") || t.contains(" WHOLE BODY")) return TRAUMA;
        if (any(t, "CVJ", "CRANIOVERTEBRAL", "ODONTOID", "ATLANTOAXIAL", "ATLANTO") || t.contains(" CRANIO VERTEBRAL")) return CVJ;
        if (any(t, "CSPINE") || t.contains(" C SPINE") || t.contains(" CERVICAL SPINE") || joined.contains("CSPINE") || (any(t, "CERVICAL") && any(t, "SPINE", "VERTEBRA", "VERTEBRAE"))) return C_SPINE;
        if (any(t, "SPINE", "LSPINE", "TSPINE", "LUMBAR", "SACRUM", "LUMBOSACRAL", "DORSAL", "VERTEBRA", "VERTEBRAE") || joined.contains("LSSPINE")) return SPINE;
        if (any(t, "BRAIN", "HEAD", "CRANIUM", "SKULL", "NCCT", "NCHCT", "STROKE", "CEREBRAL")) return BRAIN;
        if (any(t, "NECK", "THYROID", "LARYNX", "PAROTID")) return NECK;
        if (any(t, "CHEST", "THORAX", "LUNG", "LUNGS", "HRCT", "THORACIC")) return CHEST;
        if (any(t, "ABDOMEN", "ABD", "PELVIS", "KUB", "LIVER", "PANCREAS", "RENAL", "UROGRAPHY", "ENTEROGRAPHY", "TRIPHASIC")) return ABDOMEN;
        return GENERAL;
    }

    static String title(String type, String modality) {
        String m = modality == null || modality.isEmpty() ? "" : modality + " ";
        switch (type) {
            case CARDIAC_CTA: return "CT CORONARY / CARDIAC ANGIOGRAPHY";
            case CTPA: return "CT PULMONARY ANGIOGRAPHY";
            case CTA: return "CT ANGIOGRAPHY";
            case TRAUMA: return "CT TRAUMA";
            case CVJ: return m + "CRANIOVERTEBRAL JUNCTION";
            case C_SPINE: return m + "CERVICAL SPINE";
            case SPINE: return m + "SPINE";
            case BRAIN: return m + "BRAIN";
            case NECK: return m + "NECK";
            case CHEST: return m + "CHEST";
            case ABDOMEN: return m + "ABDOMEN AND PELVIS";
            default: return (m + "STUDY").trim();
        }
    }

    static Group windows(String type) {
        Group g = new Group("Windows");
        for (String n : Windows.forType(type)) {
            if (g.tools.size() >= 5) break;
            g.add("win", n, n, "brightness");
        }
        return g;
    }

    static Group mpr(String... order) {
        Group g = new Group("MPR");
        for (String p : order) g.add("mpr", p.toLowerCase(Locale.ROOT), p, "plane-" + p.toLowerCase(Locale.ROOT));
        return g;
    }

    static Profile profile(String type, String modality) {
        Profile p = new Profile();
        p.type = type;
        p.ct = "CT".equals(modality);
        p.title = title(type, modality);
        switch (type) {
            case CVJ:
                p.groups.add(windows(type));
                p.groups.add(mpr("Sagittal", "Coronal", "Axial"));
                p.groups.add(new Group("CVJ measurements").add("short", "adi", "ADI", "length").add("short", "bdi", "BDI", "length")
                        .add("short", "chamberlain", "Chamberlain", "ptline").add("short", "mcgregor", "McGregor", "ptline"));
                p.groups.add(new Group("Measurements").add("tool", "length", "Distance", "length").add("tool", "angle", "Angle", "angle").add("short", "canal-c", "Canal AP", "length"));
                p.groups.add(new Group("3D (Beta)").add("3d", "bone", "Bone", "cube"));
                break;
            case C_SPINE:
                p.groups.add(windows(type));
                p.groups.add(mpr("Sagittal", "Coronal", "Axial"));
                p.groups.add(new Group("Measurements").add("tool", "length", "Distance", "length").add("tool", "angle", "Angle", "angle").add("short", "cobb", "Cobb", "cobb")
                        .add("short", "canal-c", "Canal AP", "length").add("short", "height", "Body height", "length"));
                p.groups.add(new Group("CVJ").add("short", "adi", "ADI", "length").add("short", "bdi", "BDI", "length")
                        .add("short", "chamberlain", "Chamberlain", "ptline").add("short", "mcgregor", "McGregor", "ptline"));
                p.groups.add(new Group("3D (Beta)").add("3d", "bone", "Bone", "cube"));
                break;
            case SPINE:
                p.groups.add(windows(type));
                p.groups.add(mpr("Sagittal", "Coronal", "Axial"));
                p.groups.add(new Group("Measurements").add("tool", "length", "Distance", "length").add("tool", "angle", "Angle", "angle").add("short", "cobb", "Cobb", "cobb")
                        .add("short", "canal-l", "Canal AP", "length").add("short", "height", "Body height", "length"));
                p.groups.add(new Group("3D (Beta)").add("3d", "bone", "Bone", "cube"));
                break;
            case BRAIN:
                p.groups.add(windows(type));
                p.groups.add(mpr("Axial", "Coronal", "Sagittal"));
                p.groups.add(new Group("Quick").add("short", "midline", "Midline shift", "ptline").add("short", "abc", "Hematoma (ABC/2)", "abc").add("tool", "length", "Distance", "length")
                        .add("tool", "ellipse", "Density (HU)", "ellipse"));
                p.groups.add(new Group("3D (Beta)").add("3d", "bone", "Skull", "cube"));
                break;
            case CARDIAC_CTA: case CTA: case CTPA:
                p.groups.add(new Group(CTPA.equals(type) ? "CTPA" : "CTA").add("slab", "mip:20", "MIP", "mip").add("slab", "minip:10", "MinIP", "minip")
                        .add("slab", "mip:5", "Thin MIP", "mip").add("3d", CARDIAC_CTA.equals(type) ? "coronary" : "vessels", "3D (Beta)", "cube"));
                p.groups.add(windows(type));
                p.groups.add(mpr("Axial", "Coronal", "Sagittal"));
                p.groups.add(new Group("Measurements").add("tool", "length", "Diameter", "length").add("tool", "ellipse", "Density (HU)", "ellipse").add("tool", "angle", "Angle", "angle"));
                break;
            case TRAUMA:
                p.groups.add(windows(type));
                p.groups.add(mpr("Axial", "Coronal", "Sagittal"));
                p.groups.add(new Group("Measurements").add("tool", "length", "Distance", "length").add("tool", "angle", "Angle", "angle").add("tool", "ellipse", "Density (HU)", "ellipse"));
                p.groups.add(new Group("3D (Beta)").add("3d", "bone", "Bone", "cube"));
                break;
            case CHEST:
                p.groups.add(windows(type));
                p.groups.add(new Group("Slabs").add("slab", "mip:10", "MIP (nodules)", "mip").add("slab", "minip:10", "MinIP (airways)", "minip"));
                p.groups.add(mpr("Axial", "Coronal", "Sagittal"));
                p.groups.add(new Group("Measurements").add("tool", "length", "Distance", "length").add("tool", "ellipse", "Density (HU)", "ellipse"));
                break;
            default:
                if (p.ct) p.groups.add(windows(type));
                p.groups.add(mpr("Axial", "Coronal", "Sagittal"));
                p.groups.add(new Group("Measurements").add("tool", "length", "Distance", "length").add("tool", "angle", "Angle", "angle").add("tool", "ellipse", "Density", "ellipse"));
                if (p.ct) p.groups.add(new Group("3D (Beta)").add("3d", "bone", "Bone", "cube").add("3d", "all", "All tissues", "cube"));
        }
        if (!p.ct) {
            // CT windows are in Hounsfield units and 3D presets assume CT densities, so neither applies to MR.
            java.util.Iterator<Group> it = p.groups.iterator();
            while (it.hasNext()) { String t = it.next().title; if (t.equals("Windows") || t.startsWith("3D")) it.remove(); }
            // MR has no Hounsfield units: the region tool measures signal instead.
            for (Group g : p.groups)
                for (int i = 0; i < g.tools.size(); i++) {
                    Tool t = g.tools.get(i);
                    if (t.label.contains("(HU)") || t.label.equals("Density")) g.tools.set(i, new Tool(t.kind, t.arg, "Signal (ROI)", t.icon));
                }
        }
        return p;
    }
}
