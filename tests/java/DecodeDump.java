/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
import com.insula.dicomviewer.*;
import java.io.File;
import java.nio.*;
import java.nio.file.*;

/** Decodes one frame of each DICOM file with the app's decoders and dumps raw pixels for comparison. */
public class DecodeDump {
    public static void main(String[] args) throws Exception {
        File outDir = new File(System.getProperty("out", "tests/.work/pixels"));
        outDir.mkdirs();
        for (String path : args) {
            try {
                byte[] d = Files.readAllBytes(Paths.get(path));
                Dicom.DataSet ds = Dicom.parse(d);
                int frames = Math.max(1, ds.getInt(0x00280008, 1));
                int f = frames > 1 ? 1 : 0;
                RawImage r = PixelDecoder.decode(ds, f);
                ByteBuffer bb = ByteBuffer.allocate(r.pix.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int v : r.pix) bb.putInt(v);
                String name = new File(path).getName();
                Files.write(new File(outDir, name + ".bin").toPath(), bb.array());
                System.out.println("OK|" + path + "|" + r.w + "x" + r.h + "|rgb=" + r.rgb + "|frame=" + f + "|" + Dicom.tsName(ds.transferSyntax));
            } catch (Throwable t) {
                System.out.println("ERR|" + path + "|" + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }
}
