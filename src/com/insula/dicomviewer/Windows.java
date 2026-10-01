/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

/** Standard CT window presets (level and width in HU), shared by the viewer, MPR, and the study-type quick tools. */
final class Windows {
    static final class Preset {
        final String name;
        final double level, width;
        Preset(String n, double l, double w) { name = n; level = l; width = w; }
        String label() { return name + "   W " + fmt(width) + " / L " + fmt(level); }
    }

    static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }

    static final Preset[] ALL = {
            new Preset("Brain", 40, 80),
            new Preset("Subdural", 75, 215),
            new Preset("Stroke", 40, 40),
            new Preset("Posterior fossa", 45, 120),
            new Preset("Temporal bone", 600, 2800),
            new Preset("Sinus", 200, 2000),
            new Preset("Soft tissue", 50, 400),
            new Preset("Neck", 60, 350),
            new Preset("Spine", 50, 250),
            new Preset("Bone", 400, 1800),
            new Preset("Lung", -600, 1500),
            new Preset("Mediastinum", 50, 350),
            new Preset("Abdomen", 40, 400),
            new Preset("Liver", 60, 150),
            new Preset("Angio (CTA)", 300, 600),
            new Preset("Pulmonary embolism", 100, 700),
            new Preset("Blood / hematoma", 60, 150),
    };

    static Preset get(String name) {
        for (Preset p : ALL) if (p.name.equals(name)) return p;
        return null;
    }

    /** The presets most useful for a study type, in the order shown in the brightness strip. */
    static String[] forType(String type) {
        switch (type == null ? "" : type) {
            case StudyType.BRAIN: return new String[]{"Brain", "Subdural", "Stroke", "Bone", "Posterior fossa", "Temporal bone", "Sinus"};
            case StudyType.C_SPINE: case StudyType.CVJ: case StudyType.SPINE: return new String[]{"Bone", "Soft tissue", "Spine", "Neck"};
            case StudyType.CTA: case StudyType.CARDIAC_CTA: return new String[]{"Angio (CTA)", "Soft tissue", "Mediastinum", "Lung", "Bone"};
            case StudyType.CTPA: return new String[]{"Pulmonary embolism", "Angio (CTA)", "Mediastinum", "Lung", "Bone"};
            case StudyType.TRAUMA: return new String[]{"Bone", "Soft tissue", "Lung", "Brain", "Subdural", "Blood / hematoma", "Abdomen"};
            case StudyType.CHEST: return new String[]{"Lung", "Mediastinum", "Bone", "Soft tissue"};
            case StudyType.ABDOMEN: return new String[]{"Abdomen", "Liver", "Soft tissue", "Bone", "Lung"};
            case StudyType.NECK: return new String[]{"Neck", "Soft tissue", "Bone", "Lung"};
            default: return new String[]{"Soft tissue", "Lung", "Bone", "Brain", "Abdomen", "Mediastinum", "Liver", "Angio (CTA)"};
        }
    }
}
