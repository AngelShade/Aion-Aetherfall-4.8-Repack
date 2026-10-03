import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Build lossless PNG render copies; retain the original season illustrations. */
class OptimizeArtwork {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("source-media output-directory");
        Path source = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        String[] names = {"aether-frame.png", "aether-crest.png", "ascendant-crest.png", "pass-panel.png"};
        int[] widths = {640, 256, 256, 512};
        for (int i = 0; i < names.length; i++) {
            BufferedImage original = ImageIO.read(source.resolve(names[i]).toFile());
            if (original == null) throw new IllegalArgumentException(names[i]);
            BufferedImage image = original;
            int width = widths[i], height = Math.round((float)original.getHeight() * width / original.getWidth());
            // Halving before the final bicubic pass preserves fine gold edges
            // and alpha when reducing the high-resolution source crests.
            while (image.getWidth() != width || image.getHeight() != height) {
                int nextWidth = Math.max(width, image.getWidth() / 2);
                int nextHeight = Math.max(height, image.getHeight() / 2);
                BufferedImage next = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g = next.createGraphics();
                try {
                    g.setComposite(AlphaComposite.Src);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(image, 0, 0, nextWidth, nextHeight, null);
                } finally { g.dispose(); }
                image = next;
            }
            if (!ImageIO.write(image, "png", output.resolve(names[i]).toFile())) throw new IllegalStateException("PNG encoder");
            System.out.println(names[i] + ": " + original.getWidth() + "x" + original.getHeight() + " -> " + width + "x" + height);
        }
    }
}
