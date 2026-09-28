package com.insula.dicomviewer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.Inflater;

/** Minimal but robust DICOM Part 10 parser (implicit/explicit VR, LE/BE, deflated, sequences, encapsulated pixel data). */
public final class Dicom {
    public static final int PIXEL_DATA = 0x7FE00010;
    public static final String TS_IMPLICIT = "1.2.840.10008.1.2";
    public static final String TS_EXPLICIT_LE = "1.2.840.10008.1.2.1";
    public static final String TS_EXPLICIT_BE = "1.2.840.10008.1.2.2";
    public static final String TS_DEFLATED = "1.2.840.10008.1.2.1.99";
    public static final String TS_RLE = "1.2.840.10008.1.2.5";
    static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    static final Charset UTF8 = Charset.forName("UTF-8");

    public static final class Element {
        public int tag;
        public String vr = "UN";
        public int headerOffset, offset, length;
        public List<DataSet> items;
        public List<int[]> fragments;
    }

    public static final class DataSet {
        public final LinkedHashMap<Integer, Element> map = new LinkedHashMap<>();
        public byte[] buf;
        public boolean bigEndian, explicit = true, deflated;
        public DataSet root;
        Charset cs = LATIN1;
        public String transferSyntax = TS_EXPLICIT_LE;

        Charset charset() { return root != null ? root.cs : cs; }
        public Element get(int tag) { return map.get(tag); }
        public boolean has(int tag) { return map.containsKey(tag); }

        public int u16(int o) {
            return bigEndian ? ((buf[o] & 255) << 8) | (buf[o + 1] & 255) : (buf[o] & 255) | ((buf[o + 1] & 255) << 8);
        }
        public int s32(int o) {
            return bigEndian ? ((buf[o] & 255) << 24) | ((buf[o + 1] & 255) << 16) | ((buf[o + 2] & 255) << 8) | (buf[o + 3] & 255)
                    : (buf[o] & 255) | ((buf[o + 1] & 255) << 8) | ((buf[o + 2] & 255) << 16) | ((buf[o + 3] & 255) << 24);
        }
        long s64(int o) {
            long a = s32(o) & 0xFFFFFFFFL, b = s32(o + 4) & 0xFFFFFFFFL;
            return bigEndian ? (a << 32) | b : (b << 32) | a;
        }

        public String getString(int tag) {
            Element e = map.get(tag);
            if (e == null || e.length <= 0 || e.items != null || e.fragments != null) return null;
            if (isBinary(e.vr)) return valueString(e, 256);
            return trim(new String(buf, e.offset, e.length, charset()));
        }
        public String str(int tag) { String s = getString(tag); return s == null ? "" : s; }

        public String[] getStrings(int tag) {
            String s = getString(tag);
            return s == null ? null : s.split("\\\\");
        }

        public int getInt(int tag, int def) {
            Element e = map.get(tag);
            if (e == null || e.length <= 0 || e.items != null || e.fragments != null) return def;
            try {
                switch (e.vr) {
                    case "US": return u16(e.offset);
                    case "SS": return (short) u16(e.offset);
                    case "UL": case "SL": return s32(e.offset);
                    case "IS": case "DS": { String s = getString(tag); if (s == null) return def; return (int) Math.round(Double.parseDouble(s.split("\\\\")[0].trim())); }
                    default:
                        if (e.length == 2) return u16(e.offset);
                        if (e.length == 4) return s32(e.offset);
                        String s = trim(new String(buf, e.offset, e.length, LATIN1));
                        return (int) Math.round(Double.parseDouble(s.split("\\\\")[0].trim()));
                }
            } catch (Exception ex) { return def; }
        }

        public int[] getIntArray(int tag) {
            Element e = map.get(tag);
            if (e == null || e.length < 2 || e.items != null) return null;
            if (e.vr.equals("US") || e.vr.equals("SS") || e.vr.equals("OW") || e.vr.equals("UN")) {
                int n = e.length / 2; int[] r = new int[n];
                for (int i = 0; i < n; i++) r[i] = u16(e.offset + 2 * i);
                return r;
            }
            double[] d = getDoubles(tag);
            if (d == null) return null;
            int[] r = new int[d.length];
            for (int i = 0; i < d.length; i++) r[i] = (int) Math.round(d[i]);
            return r;
        }

        public double getDouble(int tag, double def) {
            double[] d = getDoubles(tag);
            return d == null || d.length == 0 ? def : d[0];
        }

        public double[] getDoubles(int tag) {
            Element e = map.get(tag);
            if (e == null || e.length <= 0 || e.items != null || e.fragments != null) return null;
            try {
                switch (e.vr) {
                    case "FD": { int n = e.length / 8; double[] r = new double[n]; for (int i = 0; i < n; i++) r[i] = Double.longBitsToDouble(s64(e.offset + 8 * i)); return r; }
                    case "FL": { int n = e.length / 4; double[] r = new double[n]; for (int i = 0; i < n; i++) r[i] = Float.intBitsToFloat(s32(e.offset + 4 * i)); return r; }
                    case "US": case "SS": case "UL": case "SL": return new double[]{getInt(tag, 0)};
                    default: {
                        String s = trim(new String(buf, e.offset, e.length, LATIN1));
                        if (s.isEmpty()) return null;
                        String[] p = s.split("\\\\");
                        double[] r = new double[p.length];
                        for (int i = 0; i < p.length; i++) r[i] = Double.parseDouble(p[i].trim());
                        return r;
                    }
                }
            } catch (Exception ex) { return null; }
        }

        public DataSet item(int seqTag, int idx) {
            Element e = map.get(seqTag);
            if (e == null || e.items == null || idx >= e.items.size()) return null;
            return e.items.get(idx);
        }

        public boolean looksValid() {
            String a = getString(0x00080018), b = getString(0x0020000D), c = getString(0x00080016);
            return uidLike(a) || uidLike(b) || uidLike(c);
        }

        /** Human-readable value for the tag browser. */
        public String valueString(Element e, int max) {
            if (e.items != null) return "(sequence, " + e.items.size() + " item" + (e.items.size() == 1 ? "" : "s") + ")";
            if (e.fragments != null) return "(compressed pixel data, " + Math.max(0, e.fragments.size() - 1) + " fragment(s))";
            if (e.length <= 0) return "";
            StringBuilder sb = new StringBuilder();
            switch (e.vr) {
                case "US": case "SS": { int n = Math.min(e.length / 2, 16); for (int i = 0; i < n; i++) { if (i > 0) sb.append('\\'); int v = u16(e.offset + 2 * i); sb.append(e.vr.equals("SS") ? (short) v : v); } if (e.length / 2 > 16) sb.append("…"); return sb.toString(); }
                case "UL": case "SL": { int n = Math.min(e.length / 4, 16); for (int i = 0; i < n; i++) { if (i > 0) sb.append('\\'); int v = s32(e.offset + 4 * i); sb.append(e.vr.equals("UL") ? (v & 0xFFFFFFFFL) : v); } return sb.toString(); }
                case "FL": case "FD": { double[] d = getDoubles(e.tag); if (d == null) return ""; for (int i = 0; i < Math.min(d.length, 16); i++) { if (i > 0) sb.append('\\'); sb.append(d[i]); } return sb.toString(); }
                case "AT": { int n = Math.min(e.length / 4, 8); for (int i = 0; i < n; i++) { if (i > 0) sb.append('\\'); sb.append(String.format("(%04X,%04X)", u16(e.offset + 4 * i), u16(e.offset + 4 * i + 2))); } return sb.toString(); }
                case "OB": case "OW": case "OF": case "OD": case "OL": case "OV": case "UN":
                    if (e.vr.equals("UN") && e.length <= 128 && printable(e)) return trim(new String(buf, e.offset, e.length, charset()));
                    return "(binary, " + e.length + " bytes)";
                default:
                    String s = trim(new String(buf, e.offset, Math.min(e.length, max * 4), charset()));
                    return s.length() > max ? s.substring(0, max) + "…" : s;
            }
        }

        boolean printable(Element e) {
            for (int i = 0; i < e.length; i++) { int c = buf[e.offset + i] & 255; if ((c < 32 && c != 0) || c > 126) return false; }
            return true;
        }
    }

    static boolean uidLike(String s) {
        if (s == null || s.length() < 3) return false;
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (!(c == '.' || (c >= '0' && c <= '9'))) return false; }
        return true;
    }

    static boolean isBinary(String vr) {
        switch (vr) { case "US": case "SS": case "UL": case "SL": case "FL": case "FD": case "AT": case "OB": case "OW": case "OF": case "OD": case "OL": case "OV": return true; default: return false; }
    }

    static String trim(String s) {
        int a = 0, b = s.length();
        while (b > a && (s.charAt(b - 1) == ' ' || s.charAt(b - 1) == 0)) b--;
        while (a < b && s.charAt(a) == ' ') a++;
        return s.substring(a, b);
    }

    public static DataSet parse(byte[] data) throws Exception {
        DataSet ds = new DataSet();
        int pos = 0;
        if (data.length >= 132 && data[128] == 'D' && data[129] == 'I' && data[130] == 'C' && data[131] == 'M') pos = 132;
        String ts = null;
        Parser meta = new Parser(data, false, true);
        ds.buf = data;
        if (pos + 8 <= data.length && meta.u16(pos) == 0x0002) {
            pos = meta.parse(ds, pos, data.length, true, 0);
            ts = ds.getString(0x00020010);
        }
        if (ts == null || ts.isEmpty()) {
            boolean expl = pos + 6 <= data.length && Parser.isVR(data[pos + 4], data[pos + 5]);
            ts = expl ? TS_EXPLICIT_LE : TS_IMPLICIT;
        }
        ds.transferSyntax = ts;
        boolean big = ts.equals(TS_EXPLICIT_BE);
        boolean explicit = !ts.equals(TS_IMPLICIT);
        byte[] buf = data;
        if (ts.equals(TS_DEFLATED)) {
            Inflater inf = new Inflater(true);
            inf.setInput(data, pos, data.length - pos);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(data.length * 3);
            bo.write(data, 0, pos);
            byte[] tmp = new byte[65536];
            while (!inf.finished()) {
                int n = inf.inflate(tmp);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                bo.write(tmp, 0, n);
            }
            inf.end();
            buf = bo.toByteArray();
            ds.deflated = true;
        }
        ds.buf = buf;
        ds.bigEndian = big;
        ds.explicit = explicit;
        new Parser(buf, big, explicit).parse(ds, pos, buf.length, false, 0);
        String scs = ds.getString(0x00080005);
        if (scs != null) {
            if (scs.contains("192")) ds.cs = UTF8;
            else if (scs.contains("GB18030") || scs.contains("GBK")) { try { ds.cs = Charset.forName("GB18030"); } catch (Exception ignored) { } }
            else if (scs.contains("144")) { try { ds.cs = Charset.forName("ISO-8859-5"); } catch (Exception ignored) { } }
            else if (scs.contains("127")) { try { ds.cs = Charset.forName("ISO-8859-6"); } catch (Exception ignored) { } }
            else if (scs.contains("126")) { try { ds.cs = Charset.forName("ISO-8859-7"); } catch (Exception ignored) { } }
            else if (scs.contains("138")) { try { ds.cs = Charset.forName("ISO-8859-8"); } catch (Exception ignored) { } }
        }
        return ds;
    }

    static final class Parser {
        final byte[] b; final boolean big, explicit;
        Parser(byte[] b, boolean big, boolean explicit) { this.b = b; this.big = big; this.explicit = explicit; }
        int u16(int o) { return big ? ((b[o] & 255) << 8) | (b[o + 1] & 255) : (b[o] & 255) | ((b[o + 1] & 255) << 8); }
        int s32(int o) {
            return big ? ((b[o] & 255) << 24) | ((b[o + 1] & 255) << 16) | ((b[o + 2] & 255) << 8) | (b[o + 3] & 255)
                    : (b[o] & 255) | ((b[o + 1] & 255) << 8) | ((b[o + 2] & 255) << 16) | ((b[o + 3] & 255) << 24);
        }
        static boolean isVR(byte x, byte y) { return x >= 'A' && x <= 'Z' && y >= 'A' && y <= 'Z'; }
        static boolean longVR(String v) {
            switch (v) { case "OB": case "OD": case "OF": case "OL": case "OV": case "OW": case "SQ": case "SV": case "UC": case "UN": case "UR": case "UT": case "UV": return true; default: return false; }
        }

        int parse(DataSet ds, int pos, int end, boolean metaOnly, int depth) {
            while (pos + 8 <= end) {
                int group = u16(pos), el = u16(pos + 2);
                int tag = (group << 16) | el;
                if (metaOnly && group != 2) return pos;
                if (group == 0xFFFE) {
                    if (el == 0xE00D || el == 0xE0DD) return pos + 8;
                    int l = s32(pos + 4);
                    pos += 8 + (l > 0 ? l : 0);
                    continue;
                }
                Element e = new Element();
                e.tag = tag;
                e.headerOffset = pos;
                int len;
                String vr;
                if (explicit && isVR(b[pos + 4], b[pos + 5])) {
                    vr = new String(new char[]{(char) b[pos + 4], (char) b[pos + 5]});
                    if (longVR(vr)) { if (pos + 12 > end) break; len = s32(pos + 8); pos += 12; }
                    else { len = u16(pos + 6); pos += 8; }
                } else {
                    vr = Dict.vr(tag);
                    len = s32(pos + 4);
                    pos += 8;
                }
                e.vr = vr;
                if (len == -1) {
                    e.offset = pos;
                    if (tag == PIXEL_DATA) {
                        e.fragments = new ArrayList<>();
                        pos = parseFragments(e, pos, end);
                    } else {
                        e.items = new ArrayList<>();
                        Parser p = vr.equals("UN") ? new Parser(b, false, false) : this;
                        pos = p.parseItems(e, ds, pos, end, depth);
                    }
                    e.length = pos - e.offset;
                } else {
                    if (len < 0 || pos + len > end) len = Math.max(0, end - pos);
                    e.offset = pos;
                    e.length = len;
                    if (vr.equals("SQ") && depth < 32) {
                        e.items = new ArrayList<>();
                        parseItems(e, ds, pos, pos + len, depth);
                    }
                    pos += len;
                }
                ds.map.put(tag, e);
            }
            return pos;
        }

        int parseItems(Element e, DataSet parent, int pos, int end, int depth) {
            while (pos + 8 <= end) {
                int g = u16(pos), el = u16(pos + 2), len = s32(pos + 4);
                if (g == 0xFFFE && el == 0xE0DD) return pos + 8;
                if (g != 0xFFFE || el != 0xE000) return end;
                pos += 8;
                DataSet item = new DataSet();
                item.buf = b; item.bigEndian = big; item.explicit = explicit;
                item.root = parent.root != null ? parent.root : parent;
                if (len == -1) pos = parse(item, pos, end, false, depth + 1);
                else { int ie = Math.min(end, pos + Math.max(0, len)); parse(item, pos, ie, false, depth + 1); pos = ie; }
                e.items.add(item);
            }
            return pos;
        }

        int parseFragments(Element e, int pos, int end) {
            while (pos + 8 <= end) {
                int g = u16(pos), el = u16(pos + 2), len = s32(pos + 4);
                pos += 8;
                if (g == 0xFFFE && el == 0xE0DD) break;
                if (g != 0xFFFE || el != 0xE000) break;
                if (len < 0) len = 0;
                if (pos + len > end) len = end - pos;
                e.fragments.add(new int[]{pos, len});
                pos += len;
            }
            return pos;
        }
    }

    public static String tsName(String ts) {
        if (ts == null) return "unknown";
        switch (ts) {
            case TS_IMPLICIT: return "Implicit VR Little Endian";
            case TS_EXPLICIT_LE: return "Explicit VR Little Endian";
            case TS_EXPLICIT_BE: return "Explicit VR Big Endian";
            case TS_DEFLATED: return "Deflated Explicit VR";
            case TS_RLE: return "RLE Lossless";
            case "1.2.840.10008.1.2.4.50": return "JPEG Baseline";
            case "1.2.840.10008.1.2.4.51": return "JPEG Extended (12-bit)";
            case "1.2.840.10008.1.2.4.57": return "JPEG Lossless";
            case "1.2.840.10008.1.2.4.70": return "JPEG Lossless SV1";
            case "1.2.840.10008.1.2.4.80": return "JPEG-LS Lossless";
            case "1.2.840.10008.1.2.4.81": return "JPEG-LS Near-lossless";
            case "1.2.840.10008.1.2.4.90": return "JPEG 2000 Lossless";
            case "1.2.840.10008.1.2.4.91": return "JPEG 2000";
            case "1.2.840.10008.1.2.4.201": case "1.2.840.10008.1.2.4.202": case "1.2.840.10008.1.2.4.203": return "HTJ2K";
            case "1.2.840.10008.1.2.4.100": case "1.2.840.10008.1.2.4.101": case "1.2.840.10008.1.2.4.102": case "1.2.840.10008.1.2.4.103": return "MPEG video";
            default: return ts;
        }
    }

    public static final class Dict {
        static final HashMap<Integer, String[]> M = new HashMap<>();
        static final String DATA =
            "00020000ULFile Meta Information Group Length;00020001OBFile Meta Information Version;00020002UIMedia Storage SOP Class UID;" +
            "00020003UIMedia Storage SOP Instance UID;00020010UITransfer Syntax UID;00020012UIImplementation Class UID;00020013SHImplementation Version Name;" +
            "00020016AESource Application Entity Title;00080005CSSpecific Character Set;00080008CSImage Type;00080012DAInstance Creation Date;" +
            "00080013TMInstance Creation Time;00080016UISOP Class UID;00080018UISOP Instance UID;00080020DAStudy Date;00080021DASeries Date;" +
            "00080022DAAcquisition Date;00080023DAContent Date;00080030TMStudy Time;00080031TMSeries Time;00080032TMAcquisition Time;00080033TMContent Time;" +
            "00080050SHAccession Number;00080060CSModality;00080061CSModalities in Study;00080064CSConversion Type;00080070LOManufacturer;00080080LOInstitution Name;" +
            "00080081STInstitution Address;00080090PNReferring Physician's Name;00080100SHCode Value;00080102SHCoding Scheme Designator;00080104LOCode Meaning;" +
            "00081010SHStation Name;00081030LOStudy Description;00081032SQProcedure Code Sequence;0008103ELOSeries Description;00081040LOInstitutional Department Name;" +
            "00081048PNPhysician(s) of Record;00081050PNPerforming Physician's Name;00081060PNName of Physician(s) Reading Study;00081070PNOperators' Name;" +
            "00081090LOManufacturer's Model Name;00081110SQReferenced Study Sequence;00081111SQReferenced Performed Procedure Step Sequence;" +
            "00081115SQReferenced Series Sequence;00081120SQReferenced Patient Sequence;00081140SQReferenced Image Sequence;00081150UIReferenced SOP Class UID;" +
            "00081155UIReferenced SOP Instance UID;00081160ISReferenced Frame Number;00082111STDerivation Description;00082112SQSource Image Sequence;" +
            "00082218SQAnatomic Region Sequence;00089215SQDerivation Code Sequence;00100010PNPatient's Name;00100020LOPatient ID;00100021LOIssuer of Patient ID;" +
            "00100030DAPatient's Birth Date;00100032TMPatient's Birth Time;00100040CSPatient's Sex;00101000LOOther Patient IDs;00101001PNOther Patient Names;" +
            "00101010ASPatient's Age;00101020DSPatient's Size;00101030DSPatient's Weight;00101040LOPatient's Address;00101060PNPatient's Mother's Birth Name;" +
            "00102154SHPatient's Telephone Numbers;00102160SHEthnic Group;001021B0LTAdditional Patient History;00104000LTPatient Comments;" +
            "00120062CSPatient Identity Removed;00120063LODe-identification Method;00180010LOContrast/Bolus Agent;00180015CSBody Part Examined;" +
            "00180020CSScanning Sequence;00180021CSSequence Variant;00180022CSScan Options;00180023CSMR Acquisition Type;00180050DSSlice Thickness;" +
            "00180060DSKVP;00180080DSRepetition Time;00180081DSEcho Time;00180082DSInversion Time;00180083DSNumber of Averages;00180084DSImaging Frequency;" +
            "00180087DSMagnetic Field Strength;00180088DSSpacing Between Slices;00180091ISEcho Train Length;00181000LODevice Serial Number;" +
            "00181020LOSoftware Versions;00181030LOProtocol Name;00181063DSFrame Time;00181088ISHeart Rate;00181100DSReconstruction Diameter;" +
            "00181110DSDistance Source to Detector;00181111DSDistance Source to Patient;00181120DSGantry/Detector Tilt;00181130DSTable Height;" +
            "00181150ISExposure Time;00181151ISX-Ray Tube Current;00181152ISExposure;00181160SHFilter Type;00181164DSImager Pixel Spacing;" +
            "00181210SHConvolution Kernel;00181314DSFlip Angle;00185100CSPatient Position;00185101CSView Position;0020000DUIStudy Instance UID;" +
            "0020000EUISeries Instance UID;00200010SHStudy ID;00200011ISSeries Number;00200012ISAcquisition Number;00200013ISInstance Number;" +
            "00200020CSPatient Orientation;00200032DSImage Position (Patient);00200037DSImage Orientation (Patient);00200052UIFrame of Reference UID;" +
            "00201040LOPosition Reference Indicator;00201041DSSlice Location;00201206ISNumber of Study Related Series;00201208ISNumber of Study Related Instances;" +
            "00204000LTImage Comments;00209113SQPlane Position Sequence;00209116SQPlane Orientation Sequence;00280002USSamples per Pixel;" +
            "00280004CSPhotometric Interpretation;00280006USPlanar Configuration;00280008ISNumber of Frames;00280009ATFrame Increment Pointer;" +
            "00280010USRows;00280011USColumns;00280030DSPixel Spacing;00280034ISPixel Aspect Ratio;00280100USBits Allocated;00280101USBits Stored;" +
            "00280102USHigh Bit;00280103USPixel Representation;00280106USSmallest Image Pixel Value;00280107USLargest Image Pixel Value;" +
            "00280120USPixel Padding Value;00280301CSBurned In Annotation;00281050DSWindow Center;00281051DSWindow Width;00281052DSRescale Intercept;" +
            "00281053DSRescale Slope;00281054LORescale Type;00281055LOWindow Center & Width Explanation;00281101USRed Palette Color LUT Descriptor;" +
            "00281102USGreen Palette Color LUT Descriptor;00281103USBlue Palette Color LUT Descriptor;00281201OWRed Palette Color LUT Data;" +
            "00281202OWGreen Palette Color LUT Data;00281203OWBlue Palette Color LUT Data;00282110CSLossy Image Compression;00282112DSLossy Image Compression Ratio;" +
            "00283000SQModality LUT Sequence;00283010SQVOI LUT Sequence;00289110SQPixel Measures Sequence;00289132SQFrame VOI LUT Sequence;" +
            "00289145SQPixel Value Transformation Sequence;00321032PNRequesting Physician;00321060LORequested Procedure Description;" +
            "00400009SHScheduled Procedure Step ID;00400244DAPerformed Procedure Step Start Date;00400253SHPerformed Procedure Step ID;" +
            "00400254LOPerformed Procedure Step Description;00400260SQPerformed Protocol Code Sequence;00400275SQRequest Attributes Sequence;" +
            "0040A040CSValue Type;0040A043SQConcept Name Code Sequence;0040A124UIUID;0040A160UTText Value;0040A168SQConcept Code Sequence;" +
            "0040A730SQContent Sequence;00540016SQRadiopharmaceutical Information Sequence;00541001CSUnits;52009229SQShared Functional Groups Sequence;" +
            "52009230SQPer-frame Functional Groups Sequence;7FE00010OWPixel Data";
        static {
            for (String l : DATA.split(";")) {
                if (l.length() < 11) continue;
                M.put((int) Long.parseLong(l.substring(0, 8), 16), new String[]{l.substring(8, 10), l.substring(10)});
            }
        }
        public static String vr(int tag) {
            String[] e = M.get(tag);
            if (e != null) return e[0];
            if ((tag & 0xFFFF) == 0) return "UL";
            int g = tag >>> 16, el = tag & 0xFFFF;
            if ((g & 0xFF00) == 0x6000) {
                if (el == 0x3000) return "OW";
                if (el == 0x0010 || el == 0x0011 || el == 0x0100 || el == 0x0102) return "US";
            }
            return "UN";
        }
        public static String name(int tag) {
            String[] e = M.get(tag);
            if (e != null) return e[1];
            if ((tag & 0xFFFF) == 0) return "Group Length";
            if (((tag >>> 16) & 1) == 1) return "Private tag";
            return "Unknown tag";
        }
    }
}
