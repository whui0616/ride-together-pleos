import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class Parts {
    static final int ALPHA_MIN = 10;

    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "sheet" -> sheet(Path.of(a[1]), Path.of(a[2]));
            case "grid" -> grid(Path.of(a[1]), Path.of(a[2]), a.length > 3 ? Integer.parseInt(a[3]) : 100);
            case "bbox" -> { for (int i = 1; i < a.length; i++) { Rectangle r = bbox(ImageIO.read(new File(a[i]))); System.out.println(a[i] + " " + r.x + " " + r.y + " " + r.width + " " + r.height); } }
            case "compose" -> compose(Path.of(a[1]), a[2], a[3], Path.of(a[4]));
            case "export" -> export(Path.of(a[1]), Path.of(a[2]), Integer.parseInt(a[3]));
            case "webp" -> { BufferedImage im = ImageIO.read(new File(a[1])); int maxW = a.length > 4 ? Integer.parseInt(a[4]) : im.getWidth(); if (im.getWidth() > maxW) im = scale(im, maxW / (double) im.getWidth()); writeWebp(im, Path.of(a[2]), Float.parseFloat(a[3])); }
            default -> throw new IllegalArgumentException(a[0]);
        }
    }

    static Rectangle bbox(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        int[] px = im.getRGB(0, 0, w, h, null, 0, w);
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (((px[y * w + x] >>> 24) & 0xff) >= ALPHA_MIN) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
        }
        if (x1 < 0) return new Rectangle(0, 0, w, h);
        return new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    static BufferedImage argb(BufferedImage im) {
        if (im.getType() == BufferedImage.TYPE_INT_ARGB) return im;
        BufferedImage o = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics(); g.drawImage(im, 0, 0, null); g.dispose(); return o;
    }

    static BufferedImage scale(BufferedImage im, double s) {
        int w = Math.max(1, (int) Math.round(im.getWidth() * s)), h = Math.max(1, (int) Math.round(im.getHeight() * s));
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(im, 0, 0, w, h, null); g.dispose(); return o;
    }

    static void sheet(Path dir, Path out) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var s = Files.walk(dir)) { s.filter(p -> p.toString().toLowerCase().endsWith(".png")).sorted().forEach(files::add); }
        int cell = 220, cols = 6, rows = (files.size() + cols - 1) / cols;
        BufferedImage o = new BufferedImage(cols * cell, rows * (cell + 30), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setColor(new Color(200, 200, 200)); g.fillRect(0, 0, o.getWidth(), o.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        for (int i = 0; i < files.size(); i++) {
            BufferedImage im = argb(ImageIO.read(files.get(i).toFile()));
            Rectangle r = bbox(im);
            BufferedImage c = im.getSubimage(r.x, r.y, r.width, r.height);
            double s = Math.min((cell - 10) / (double) r.width, (cell - 10) / (double) r.height);
            int cx = (i % cols) * cell, cy = (i / cols) * (cell + 30);
            int dw = (int) (r.width * s), dh = (int) (r.height * s);
            g.drawImage(c, cx + (cell - dw) / 2, cy + (cell - dh) / 2, dw, dh, null);
            g.setColor(Color.BLACK);
            g.drawString(files.get(i).getFileName().toString().replace(".png", ""), cx + 4, cy + cell + 12);
            g.drawString(im.getWidth() + "x" + im.getHeight() + " bbox " + r.width + "x" + r.height + " @" + r.x + "," + r.y, cx + 4, cy + cell + 26);
        }
        g.dispose(); ImageIO.write(o, "png", out.toFile());
    }

    static void grid(Path in, Path out, int step) throws IOException {
        BufferedImage im = argb(ImageIO.read(in.toFile()));
        BufferedImage o = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setColor(new Color(230, 230, 230)); g.fillRect(0, 0, o.getWidth(), o.getHeight());
        g.drawImage(im, 0, 0, null);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        for (int x = 0; x < im.getWidth(); x += step) { g.setColor(new Color(255, 0, 0, 110)); g.drawLine(x, 0, x, im.getHeight()); g.setColor(Color.RED); g.drawString("" + x, x + 2, 20); }
        for (int y = 0; y < im.getHeight(); y += step) { g.setColor(new Color(0, 0, 255, 110)); g.drawLine(0, y, im.getWidth(), y); g.setColor(Color.BLUE); g.drawString("" + y, 2, y - 2); }
        Rectangle r = bbox(im); g.setColor(Color.GREEN); g.drawRect(r.x, r.y, r.width, r.height);
        g.dispose(); ImageIO.write(o, "png", out.toFile());
    }

    record Part(String g, String slot, String option, String file, double cx, double cy, double w, int z, double ct, double cb, double cl, double cr) {}
    record Base(String g, String file, int w, int h) {}
    static Path pack; static Map<String, Base> bases = new LinkedHashMap<>(); static List<Part> parts = new ArrayList<>();

    static void readSpec(Path spec) throws IOException {
        for (String line : Files.readAllLines(spec)) {
            line = line.strip(); int hash = line.indexOf('#'); if (hash >= 0) line = line.substring(0, hash).strip(); if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "pack" -> pack = Path.of(t[1]);
                case "base" -> bases.put(t[1], new Base(t[1], t[2], Integer.parseInt(t[3]), Integer.parseInt(t[4])));
                case "part" -> parts.add(new Part(t[1], t[2], t[3], t[4], d(t[5]), d(t[6]), d(t[7]), Integer.parseInt(t[8]),
                        t.length > 9 ? d(t[9]) : 0, t.length > 10 ? d(t[10]) : 0, t.length > 11 ? d(t[11]) : 0, t.length > 12 ? d(t[12]) : 0));
                default -> throw new IOException("bad line: " + line);
            }
        }
    }
    static double d(String s) { return Double.parseDouble(s); }

    static Map<String, BufferedImage> cache = new HashMap<>();
    static BufferedImage load(String f) throws IOException { BufferedImage im = cache.get(f); if (im == null) { im = argb(ImageIO.read(pack.resolve(f).toFile())); cache.put(f, im); } return im; }

    static BufferedImage piece(Part p) throws IOException {
        BufferedImage im = load(p.file); Rectangle r = bbox(im);
        int x0 = r.x + (int) (r.width * p.cl), x1 = r.x + r.width - (int) (r.width * p.cr);
        int y0 = r.y + (int) (r.height * p.ct), y1 = r.y + r.height - (int) (r.height * p.cb);
        return im.getSubimage(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    static void compose(Path spec, String g, String look, Path out) throws IOException {
        readSpec(spec);
        Map<String, String> lk = new HashMap<>();
        for (String kv : look.split(";")) { int i = kv.indexOf('='); if (i > 0) lk.put(kv.substring(0, i), kv.substring(i + 1)); }
        Base b = bases.get(g);
        BufferedImage base = load(b.file);
        BufferedImage o = new BufferedImage(b.w, b.h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr = o.createGraphics();
        gr.setColor(new Color(235, 235, 235)); gr.fillRect(0, 0, b.w, b.h);
        gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        List<Part> sel = new ArrayList<>();
        for (Part p : parts) if (p.g.equals(g) && p.option.equals(lk.getOrDefault(p.slot, "none"))) sel.add(p);
        sel.sort(Comparator.comparingInt(p -> p.z));
        boolean baseDrawn = false;
        for (Part p : sel) {
            if (p.z >= 0 && !baseDrawn) { gr.drawImage(base, 0, 0, null); baseDrawn = true; }
            BufferedImage pc = piece(p);
            double s = p.w / pc.getWidth();
            int dw = (int) Math.round(pc.getWidth() * s), dh = (int) Math.round(pc.getHeight() * s);
            gr.drawImage(pc, (int) Math.round(p.cx - dw / 2.0), (int) Math.round(p.cy - dh / 2.0), dw, dh, null);
        }
        if (!baseDrawn) gr.drawImage(base, 0, 0, null);
        gr.dispose(); ImageIO.write(o, "png", out.toFile());
    }

    static void export(Path spec, Path outDir, int baseH) throws IOException {
        readSpec(spec);
        Files.createDirectories(outDir);
        StringBuilder js = new StringBuilder("{\n  \"version\": 1,\n  \"bases\": {\n");
        int bi = 0;
        for (Base b : bases.values()) {
            double s = baseH / (double) b.h;
            BufferedImage im = scale(load(b.file), s);
            String name = "base_" + b.g + ".webp";
            writeWebp(im, outDir.resolve(name), 88f);
            js.append(bi++ > 0 ? ",\n" : "").append("    \"").append(b.g).append("\": {\"file\": \"").append(name).append("\", \"w\": ").append(im.getWidth()).append(", \"h\": ").append(im.getHeight()).append("}");
        }
        js.append("\n  },\n  \"parts\": [\n");
        int pi = 0;
        for (Part p : parts) {
            Base b = bases.get(p.g);
            double s = baseH / (double) b.h;
            BufferedImage pc = piece(p);
            double ps = p.w * s / pc.getWidth();
            BufferedImage im = scale(pc, ps);
            String name = p.g + "_" + p.slot + "_" + p.option + ".webp";
            writeWebp(im, outDir.resolve(name), 85f);
            js.append(pi++ > 0 ? ",\n" : "").append(String.format(Locale.ROOT,
                    "    {\"g\": \"%s\", \"slot\": \"%s\", \"option\": \"%s\", \"file\": \"%s\", \"x\": %.4f, \"y\": %.4f, \"w\": %.4f, \"h\": %.4f, \"z\": %d}",
                    p.g, p.slot, p.option, name, p.cx / b.w, p.cy / b.h, im.getWidth() / (b.w * s), im.getHeight() / (b.h * s), p.z));
        }
        js.append("\n  ]\n}\n");
        Files.writeString(outDir.resolve("static_rig.json"), js.toString());
        System.out.println("exported " + parts.size() + " parts → " + outDir);
    }

    static boolean webpLoaded = false;
    static void writeWebp(BufferedImage im, Path out, float q) throws IOException {
        if (!webpLoaded) { System.load("C:\\Program Files\\Android\\Android Studio\\plugins\\webp\\lib\\libwebp\\win\\webp_jni.dll"); webpLoaded = true; }
        im = argb(im);
        int w = im.getWidth(), h = im.getHeight();
        int[] px = im.getRGB(0, 0, w, h, null, 0, w);
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < px.length; i++) { int c = px[i]; rgba[i * 4] = (byte) (c >> 16); rgba[i * 4 + 1] = (byte) (c >> 8); rgba[i * 4 + 2] = (byte) c; rgba[i * 4 + 3] = (byte) (c >>> 24); }
        byte[] enc = com.google.webp.libwebp.WebPEncodeRGBA(rgba, w, h, w * 4, q);
        Files.write(out, enc);
        System.out.println(out.getFileName() + " " + w + "x" + h + " " + enc.length / 1024 + "KB");
    }
}
