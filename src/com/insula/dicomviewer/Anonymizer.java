package com.insula.dicomviewer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Blanks identifying attributes in place (same lengths, so any transfer syntax except deflated works without re-encoding).
 * Also scrubs nested sequences and all private tags. UIDs and study dates are kept so series still load together.
 */
public final class Anonymizer {
    static final Map<Integer, String> PHI = new HashMap<>();
    static {
        PHI.put(0x00100010, "ANONYMOUS");  // Patient's Name
        PHI.put(0x00100020, "ANON");       // Patient ID
        PHI.put(0x00100021, "");
        PHI.put(0x00100030, "");           // Birth Date
        PHI.put(0x00100032, "");
        PHI.put(0x00101000, "");
        PHI.put(0x00101001, "");
        PHI.put(0x00101040, "");
        PHI.put(0x00101060, "");
        PHI.put(0x00102154, "");
        PHI.put(0x00102160, "");
        PHI.put(0x001021B0, "");
        PHI.put(0x00104000, "");
        PHI.put(0x00080050, "");           // Accession
        PHI.put(0x00080080, "");           // Institution
        PHI.put(0x00080081, "");
        PHI.put(0x00080090, "");           // Referring physician
        PHI.put(0x00081010, "");
        PHI.put(0x00081040, "");
        PHI.put(0x00081048, "");
        PHI.put(0x00081050, "");
        PHI.put(0x00081060, "");
        PHI.put(0x00081070, "");
        PHI.put(0x00181000, "");           // Device serial
        PHI.put(0x00200010, "");           // Study ID
        PHI.put(0x00321032, "");
        PHI.put(0x00400009, "");
        PHI.put(0x00400253, "");
        PHI.put(0x00204000, "");           // Image comments
    }

    /** @return number of files written; warn[0] set true if any image declares burned-in annotation. */
    public static int writeZip(List<File> files, File out, boolean[] warn, Library.Progress p) throws Exception {
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
        int n = 0, i = 0;
        try {
            for (File f : files) {
                i++;
                byte[] data = Library.readFile(f);
                Dicom.DataSet ds;
                try { ds = Dicom.parse(data); } catch (Exception e) { continue; }
                if (ds.deflated) continue;
                scrub(ds);
                if ("YES".equalsIgnoreCase(ds.getString(0x00280301))) warn[0] = true;
                z.putNextEntry(new ZipEntry(String.format("IMG%05d.dcm", ++n)));
                z.write(data);
                z.closeEntry();
                if (p != null) p.update(i, files.size(), "Anonymizing");
            }
        } finally { z.close(); }
        return n;
    }

    static void scrub(Dicom.DataSet ds) {
        for (Dicom.Element e : ds.map.values()) {
            if (e.items != null) { for (Dicom.DataSet it : e.items) scrub(it); continue; }
            if (e.fragments != null || e.length <= 0) continue;
            int g = e.tag >>> 16;
            if (g == 0x0002) continue;
            if ((g & 1) == 1) { fill(ds.buf, e, "", (byte) 0); continue; }
            String r = PHI.get(e.tag);
            if (r != null) fill(ds.buf, e, r, (byte) ' ');
        }
    }

    static void fill(byte[] b, Dicom.Element e, String val, byte pad) {
        byte[] v = val.getBytes(Dicom.LATIN1);
        for (int k = 0; k < e.length; k++) b[e.offset + k] = k < v.length ? v[k] : pad;
    }
}
