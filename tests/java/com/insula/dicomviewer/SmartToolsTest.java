/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.io.File;
import java.util.*;

/** Study-type recognition and quick-tool profiles, window presets, Study Quality, and point-to-line geometry. */
public class SmartToolsTest {
    static int fails = 0;
    static void check(String n, boolean ok, String d) { System.out.println((ok ? "PASS " : "FAIL ") + n + "  " + d); if (!ok) fails++; }

    static List<String> labels(StudyType.Profile p) {
        List<String> l = new ArrayList<>();
        for (StudyType.Group g : p.groups) for (StudyType.Tool t : g.tools) l.add(g.title + ":" + t.label);
        return l;
    }

    public static void main(String[] a) throws Exception {
        // Recognition: modality, study description, series description, body part, protocol, contrast
        Object[][] cases = {
                {"CT", "CT C-SPINE WO CONTRAST", "Bone 1.0", "", "", "", StudyType.C_SPINE},
                {"CT", "CT CERVICAL SPINE", "", "CSPINE", "", "", StudyType.C_SPINE},
                {"CT", "CT CVJ", "Sag bone", "", "", "", StudyType.CVJ},
                {"CT", "CT LUMBAR SPINE", "", "LSPINE", "", "", StudyType.SPINE},
                {"CT", "NCCT HEAD", "Brain 5mm", "HEAD", "", "", StudyType.BRAIN},
                {"CT", "CT BRAIN PLAIN", "", "", "", "", StudyType.BRAIN},
                {"CT", "CT CORONARY ANGIOGRAPHY", "Series 3: Coronary 75-113 BPM", "HEART", "", "OMNIPAQUE", StudyType.CARDIAC_CTA},
                {"CT", "CT ANGIO HEAD AND NECK", "", "", "", "", StudyType.CTA},
                {"CT", "CTPA", "", "CHEST", "PE PROTOCOL", "", StudyType.CTPA},
                {"CT", "CT CHEST HRCT", "", "CHEST", "", "", StudyType.CHEST},
                {"CT", "CT ABDOMEN AND PELVIS WITH CONTRAST", "Portal venous", "ABDOMEN", "", "IV", StudyType.ABDOMEN},
                {"CT", "TRAUMA PANSCAN", "", "", "", "", StudyType.TRAUMA},
                {"CT", "CT NECK SOFT TISSUE", "", "NECK", "", "", StudyType.NECK},
                {"CT", "", "", "", "", "", StudyType.GENERAL},
                {"MR", "MRI BRAIN", "T2 AX", "BRAIN", "", "", StudyType.BRAIN},
        };
        int ok = 0;
        for (Object[] c : cases) {
            String got = StudyType.detect((String) c[0], (String) c[1], (String) c[2], (String) c[3], (String) c[4], (String) c[5]);
            if (got.equals(c[6])) ok++;
            else System.out.println("     \"" + c[1] + " / " + c[2] + "\": expected " + c[6] + ", got " + got);
        }
        check("recognizes the study type from its descriptions", ok == cases.length, ok + " of " + cases.length);

        // Profiles contain the tools asked for
        List<String> cs = labels(StudyType.profile(StudyType.C_SPINE, "CT"));
        check("cervical spine: Bone, Soft tissue, Spine windows", cs.containsAll(Arrays.asList("Windows:Bone", "Windows:Soft tissue", "Windows:Spine")), "");
        check("cervical spine: Sagittal, Coronal, Axial MPR", cs.containsAll(Arrays.asList("MPR:Sagittal", "MPR:Coronal", "MPR:Axial")), "");
        check("cervical spine: Distance, Angle, Cobb, and 3D Bone", cs.containsAll(Arrays.asList("Measurements:Distance", "Measurements:Angle", "Measurements:Cobb", "3D (Beta):Bone")), "");
        check("cervical spine: CVJ shortcuts", cs.containsAll(Arrays.asList("CVJ:ADI", "CVJ:BDI", "CVJ:Chamberlain", "CVJ:McGregor")), "");
        List<String> br = labels(StudyType.profile(StudyType.BRAIN, "CT"));
        check("brain: Brain, Subdural, Bone, Stroke windows", br.containsAll(Arrays.asList("Windows:Brain", "Windows:Subdural", "Windows:Bone", "Windows:Stroke")), "");
        check("brain: Midline shift, Hematoma, Distance", br.containsAll(Arrays.asList("Quick:Midline shift", "Quick:Hematoma (ABC/2)", "Quick:Distance")), "");
        List<String> mr = labels(StudyType.profile(StudyType.BRAIN, "MR"));
        boolean noCtTools = true;
        for (String l : mr) if (l.startsWith("Windows:") || l.startsWith("3D")) noCtTools = false;
        check("MR brain: no CT windows or 3D skull, but MPR and measurements", noCtTools && mr.contains("MPR:Axial") && mr.contains("Quick:Midline shift") && mr.contains("Quick:Signal (ROI)") && !mr.toString().contains("(HU)"), mr.toString());
        List<String> cta = labels(StudyType.profile(StudyType.CTA, "CT"));
        check("CTA: MIP, MinIP, Thin MIP, 3D", cta.containsAll(Arrays.asList("CTA:MIP", "CTA:MinIP", "CTA:Thin MIP", "CTA:3D (Beta)")), "");
        boolean presetsExist = true, shortcutsExist = true;
        for (String t : new String[]{StudyType.CARDIAC_CTA, StudyType.CTPA, StudyType.CTA, StudyType.TRAUMA, StudyType.CVJ, StudyType.C_SPINE, StudyType.SPINE,
                StudyType.BRAIN, StudyType.NECK, StudyType.CHEST, StudyType.ABDOMEN, StudyType.GENERAL}) {
            for (String n : Windows.forType(t)) if (Windows.get(n) == null) { presetsExist = false; System.out.println("     missing preset " + n); }
            for (StudyType.Group g : StudyType.profile(t, "CT").groups) for (StudyType.Tool tl : g.tools) {
                if (tl.kind.equals("win") && Windows.get(tl.arg) == null) presetsExist = false;
                if (tl.kind.equals("short") && StudyType.shortcut(tl.arg) == null) { shortcutsExist = false; System.out.println("     missing shortcut " + tl.arg); }
            }
        }
        check("every preset and shortcut a profile offers exists", presetsExist && shortcutsExist, "");
        Windows.Preset lung = Windows.get("Lung"), bone = Windows.get("Bone"), brain = Windows.get("Brain");
        check("standard windows: lung W1500/L-600, bone W1800/L400, brain W80/L40", lung.width == 1500 && lung.level == -600 && bone.width == 1800 && bone.level == 400 && brain.width == 80 && brain.level == 40, "");

        // Point-to-line geometry: perpendicular foot
        float[] f = DicomView.foot(0, 0, 10, 0, 4, 3);
        check("point-to-line foot of perpendicular", Math.abs(f[0] - 4) < 1e-5 && Math.abs(f[1]) < 1e-5, f[0] + ", " + f[1]);
        f = DicomView.foot(0, 0, 10, 10, 0, 10);
        check("point-to-line on a diagonal", Math.abs(f[0] - 5) < 1e-5 && Math.abs(f[1] - 5) < 1e-5, f[0] + ", " + f[1]);

        // Study Quality on the synthetic demo (48 slices, 3 mm apart, 0.9 mm pixels, noise sigma 5 HU)
        Library.dir = new File(System.getProperty("work", "tests/.work") + "/quality-lib");
        Library.dir.mkdirs();
        DemoStudy.create(null);
        Library.Series se = Library.study(DemoStudy.studyUid(1)).series.get(0);
        StudyQuality.Report q = StudyQuality.analyze(se);
        Map<String, StudyQuality.Item> m = new HashMap<>();
        for (StudyQuality.Item it : q.items) m.put(it.name, it);
        check("quality: 48 images", m.containsKey("Images") && m.get("Images").value.equals("48"), "");
        check("quality: 3 mm spacing flagged for reformats", m.containsKey("Slice spacing") && m.get("Slice spacing").value.startsWith("3.00") && m.get("Slice spacing").rating == StudyQuality.POOR, m.containsKey("Slice spacing") ? m.get("Slice spacing").value : "missing");
        check("quality: MPR limited, 3D limited", q.mpr.startsWith("Limited") && q.threeD.startsWith("Limited"), "MPR " + q.mpr + ", 3D " + q.threeD);
        double noise = StudyQuality.noiseHU(se.slices().get(24));
        check("quality: noise estimate matches the phantom's 5 HU", noise > 3.5 && noise < 7, String.format("%.1f HU", noise));

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
