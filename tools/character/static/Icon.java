import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class Icon {
    public static void main(String[] a) throws Exception {
        BufferedImage fg = ImageIO.read(new File(a[0])), bg = ImageIO.read(new File(a[1]));
        int S = 432;
        int w = fg.getWidth(), h = fg.getHeight(); int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (((fg.getRGB(x, y) >>> 24) & 0xff) > 10) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
        BufferedImage crop = fg.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        double s = S * 0.62 / Math.max(crop.getWidth(), crop.getHeight());
        BufferedImage of = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = of.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int dw = (int) (crop.getWidth() * s), dh = (int) (crop.getHeight() * s);
        g.drawImage(crop, (S - dw) / 2, (S - dh) / 2, dw, dh, null); g.dispose();
        ImageIO.write(of, "png", new File(a[2]));
        BufferedImage ob = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        g = ob.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(bg, 0, 0, S, S, null); g.dispose();
        ImageIO.write(ob, "png", new File(a[3]));
        System.out.println("icon ok");
    }
}
