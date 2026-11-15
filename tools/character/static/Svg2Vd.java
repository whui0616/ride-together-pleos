import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.*;
import java.util.*;

public class Svg2Vd {
    static Map<String, String> gradients = new HashMap<>();
    static StringBuilder out;
    static int skippedText;

    public static void main(String[] a) throws Exception {
        Path outDir = Path.of(a[a.length - 1]);
        Files.createDirectories(outDir);
        int n = 0;
        for (int i = 0; i < a.length - 1; i++) {
            try (var s = Files.list(Path.of(a[i]))) {
                for (Path p : s.filter(x -> x.toString().endsWith(".svg")).sorted().toList()) {
                    String name = p.getFileName().toString().replace(".svg", "").toLowerCase().replaceAll("[^a-z0-9_]", "_");
                    Files.writeString(outDir.resolve(name + ".xml"), convert(p));
                    n++;
                }
            }
        }
        System.out.println("converted " + n + " (text skipped " + skippedText + ")");
    }

    static String convert(Path p) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(p.toFile());
        Element svg = doc.getDocumentElement();
        String[] vb = svg.getAttribute("viewBox").trim().split("[ ,]+");
        double vw = Double.parseDouble(vb[2]), vh = Double.parseDouble(vb[3]);
        gradients.clear();
        collectGradients(svg);
        out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- 이미지팩 v2 ").append(p.getFileName()).append(" (Svg2Vd 변환) -->\n");
        out.append(String.format(Locale.ROOT, "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:width=\"24dp\" android:height=\"24dp\"\n    android:viewportWidth=\"%s\" android:viewportHeight=\"%s\">\n", num(vw), num(vh)));
        Style root = new Style();
        root.fill = "#000000";
        for (Node c = svg.getFirstChild(); c != null; c = c.getNextSibling()) walk(c, root, 1);
        out.append("</vector>\n");
        return out.toString();
    }

    static void collectGradients(Element e) {
        NodeList gs = e.getElementsByTagName("linearGradient");
        for (int i = 0; i < gs.getLength(); i++) {
            Element g = (Element) gs.item(i);
            NodeList stops = g.getElementsByTagName("stop");
            if (stops.getLength() > 0) {
                Element s = (Element) stops.item(0);
                String c = s.getAttribute("stop-color");
                if (c.isEmpty()) { String st = s.getAttribute("style"); int k = st.indexOf("stop-color:"); if (k >= 0) c = st.substring(k + 11).split(";")[0].trim(); }
                gradients.put(g.getAttribute("id"), c);
            }
        }
    }

    static class Style implements Cloneable {
        String fill, stroke; double strokeWidth = 1, opacity = 1, fillOpacity = 1, strokeOpacity = 1; String cap, join;
        Style copy() { try { return (Style) clone(); } catch (CloneNotSupportedException e) { throw new RuntimeException(e); } }
    }

    static Style inherit(Element e, Style parent) {
        Style s = parent.copy();
        if (e.hasAttribute("fill")) s.fill = e.getAttribute("fill");
        if (e.hasAttribute("stroke")) s.stroke = e.getAttribute("stroke");
        if (e.hasAttribute("stroke-width")) s.strokeWidth = Double.parseDouble(e.getAttribute("stroke-width"));
        if (e.hasAttribute("opacity")) s.opacity *= Double.parseDouble(e.getAttribute("opacity"));
        if (e.hasAttribute("fill-opacity")) s.fillOpacity = Double.parseDouble(e.getAttribute("fill-opacity"));
        if (e.hasAttribute("stroke-opacity")) s.strokeOpacity = Double.parseDouble(e.getAttribute("stroke-opacity"));
        if (e.hasAttribute("stroke-linecap")) s.cap = e.getAttribute("stroke-linecap");
        if (e.hasAttribute("stroke-linejoin")) s.join = e.getAttribute("stroke-linejoin");
        return s;
    }

    static void walk(Node n, Style parent, int depth) {
        if (!(n instanceof Element e)) return;
        String tag = e.getTagName();
        if (tag.equals("title") || tag.equals("defs") || tag.equals("linearGradient") || tag.equals("desc")) return;
        if (tag.equals("text")) { skippedText++; return; }
        Style s = inherit(e, parent);
        String ind = "    ".repeat(depth);
        if (tag.equals("g") || tag.equals("svg")) {
            String tr = e.getAttribute("transform");
            boolean group = !tr.isEmpty();
            if (group) {
                double tx = 0, ty = 0, sx = 1, sy = 1;
                var m = java.util.regex.Pattern.compile("(translate|scale)\\(([^)]*)\\)").matcher(tr);
                while (m.find()) {
                    String[] v = m.group(2).trim().split("[ ,]+");
                    if (m.group(1).equals("translate")) { tx = Double.parseDouble(v[0]); ty = v.length > 1 ? Double.parseDouble(v[1]) : 0; }
                    else { sx = Double.parseDouble(v[0]); sy = v.length > 1 ? Double.parseDouble(v[1]) : sx; }
                }
                out.append(String.format(Locale.ROOT, "%s<group android:translateX=\"%s\" android:translateY=\"%s\" android:scaleX=\"%s\" android:scaleY=\"%s\">\n", ind, num(tx), num(ty), num(sx), num(sy)));
            }
            for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) walk(c, s, depth + (group ? 1 : 0));
            if (group) out.append(ind).append("</group>\n");
            return;
        }
        String d = switch (tag) {
            case "path" -> e.getAttribute("d");
            case "circle" -> circle(dbl(e, "cx"), dbl(e, "cy"), dbl(e, "r"), dbl(e, "r"));
            case "ellipse" -> circle(dbl(e, "cx"), dbl(e, "cy"), dbl(e, "rx"), dbl(e, "ry"));
            case "rect" -> rect(dbl(e, "x"), dbl(e, "y"), dbl(e, "width"), dbl(e, "height"), e.hasAttribute("rx") ? dbl(e, "rx") : (e.hasAttribute("ry") ? dbl(e, "ry") : 0));
            case "line" -> String.format(Locale.ROOT, "M%s,%s L%s,%s", num(dbl(e, "x1")), num(dbl(e, "y1")), num(dbl(e, "x2")), num(dbl(e, "y2")));
            case "polyline", "polygon" -> poly(e.getAttribute("points"), tag.equals("polygon"));
            default -> null;
        };
        if (d == null || d.isBlank()) return;
        if (e.hasAttribute("transform")) {
            Style dummy = s; Element g = e.getOwnerDocument().createElement("g"); g.setAttribute("transform", e.getAttribute("transform"));
            Element copy = (Element) e.cloneNode(false); copy.removeAttribute("transform"); g.appendChild(copy);
            walk(g, parent, depth); return;
        }
        out.append(ind).append("<path android:pathData=\"").append(d.replace("\"", "'").trim()).append("\"");
        String fill = color(s.fill);
        if (fill != null) out.append(" android:fillColor=\"").append(fill).append("\"");
        double fa = s.opacity * s.fillOpacity; if (fill != null && fa < 1) out.append(String.format(Locale.ROOT, " android:fillAlpha=\"%s\"", num(fa)));
        String stroke = color(s.stroke);
        if (stroke != null) {
            out.append(" android:strokeColor=\"").append(stroke).append("\"").append(String.format(Locale.ROOT, " android:strokeWidth=\"%s\"", num(s.strokeWidth)));
            double sa = s.opacity * s.strokeOpacity; if (sa < 1) out.append(String.format(Locale.ROOT, " android:strokeAlpha=\"%s\"", num(sa)));
            if (s.cap != null) out.append(" android:strokeLineCap=\"").append(s.cap).append("\"");
            if (s.join != null) out.append(" android:strokeLineJoin=\"").append(s.join).append("\"");
        }
        out.append("/>\n");
    }

    static String color(String c) {
        if (c == null || c.equals("none") || c.isEmpty()) return null;
        if (c.startsWith("url(")) { String id = c.substring(5, c.length() - 1); return color(gradients.getOrDefault(id, "#3D7BFF")); }
        if (c.equals("currentColor")) return "#15181D";
        if (c.startsWith("#") && c.length() == 4) return "#" + c.charAt(1) + c.charAt(1) + c.charAt(2) + c.charAt(2) + c.charAt(3) + c.charAt(3);
        if (c.equals("white")) return "#FFFFFF"; if (c.equals("black")) return "#000000";
        return c;
    }

    static double dbl(Element e, String k) { String v = e.getAttribute(k); return v.isEmpty() ? 0 : Double.parseDouble(v); }
    static String num(double v) { return (v == Math.rint(v)) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", ""); }

    static String circle(double cx, double cy, double rx, double ry) {
        return String.format(Locale.ROOT, "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 -%s,0 Z", num(cx - rx), num(cy), num(rx), num(ry), num(2 * rx), num(rx), num(ry), num(2 * rx));
    }
    static String rect(double x, double y, double w, double h, double r) {
        if (r <= 0) return String.format(Locale.ROOT, "M%s,%s h%s v%s h-%s Z", num(x), num(y), num(w), num(h), num(w));
        r = Math.min(r, Math.min(w, h) / 2);
        return String.format(Locale.ROOT, "M%s,%s h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 -%s,%s h-%s a%s,%s 0 0 1 -%s,-%s v-%s a%s,%s 0 0 1 %s,-%s Z",
            num(x + r), num(y), num(w - 2 * r), num(r), num(r), num(r), num(r), num(h - 2 * r), num(r), num(r), num(r), num(r), num(w - 2 * r), num(r), num(r), num(r), num(r), num(h - 2 * r), num(r), num(r), num(r), num(r));
    }
    static String poly(String pts, boolean close) {
        String[] v = pts.trim().split("[ ,]+"); StringBuilder b = new StringBuilder();
        for (int i = 0; i + 1 < v.length; i += 2) b.append(i == 0 ? "M" : " L").append(v[i]).append(",").append(v[i + 1]);
        return close ? b + " Z" : b.toString();
    }
}
