import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the mod icon.
 *
 * <p>Here rather than in a paint program so that the icon can be regenerated: it is a page with a
 * quill over it, and the page is the same parchment the book is, so it reads as a book icon at 32
 * pixels where an ornate one would read as a smudge.
 *
 * <p>Run with: java tools/MakeIcon.java src/main/resources/assets/roleplayersquill/icon.png
 */
public final class MakeIcon {
	private static final int SIZE = 128;

	private static final Color BACKGROUND = new Color(0x2B2118);
	private static final Color PAGE = new Color(0xE8DBBB);
	private static final Color PAGE_SHADE = new Color(0xCFBE96);
	private static final Color INK = new Color(0x3A2E22);
	private static final Color FEATHER = new Color(0xF2EDE3);
	private static final Color FEATHER_SHADE = new Color(0xC9BFAE);
	private static final Color NIB = new Color(0x6B5A3E);

	public static void main(String[] args) throws Exception {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		g.setColor(BACKGROUND);
		g.fillRoundRect(0, 0, SIZE, SIZE, 22, 22);

		// The page, tilted a little so it is a sheet rather than a rectangle.
		g.rotate(Math.toRadians(-6), SIZE / 2.0, SIZE / 2.0);
		g.setColor(PAGE_SHADE);
		g.fillRoundRect(20, 16, 86, 100, 6, 6);
		g.setColor(PAGE);
		g.fillRoundRect(18, 14, 86, 100, 6, 6);

		// Lines of writing: one centred heading and a justified block, which is what the mod does.
		g.setColor(INK);
		g.fillRect(44, 28, 34, 4);
		int[] widths = { 70, 70, 70, 70, 46 };
		for (int i = 0; i < widths.length; i++) {
			g.fillRect(26, 44 + i * 11, widths[i], 3);
		}
		g.rotate(Math.toRadians(6), SIZE / 2.0, SIZE / 2.0);

		// The quill, from the bottom left to the top right. Narrow on purpose: a broad feather at
		// this size covers the writing underneath and the icon stops saying "book".
		GeneralPath feather = new GeneralPath(Path2D.WIND_NON_ZERO);
		feather.moveTo(40, 110);
		feather.curveTo(66, 88, 92, 58, 110, 20);
		feather.curveTo(94, 30, 66, 58, 48, 94);
		feather.closePath();
		g.setColor(FEATHER);
		g.fill(feather);

		g.setColor(INK);
		g.setStroke(new BasicStroke(2.5f));
		g.draw(feather);

		g.setColor(FEATHER_SHADE);
		g.setStroke(new BasicStroke(1.6f));
		for (int i = 1; i <= 6; i++) {
			double t = i / 7.0;
			int x = (int) (46 + t * 58);
			int y = (int) (100 - t * 74);
			g.drawLine(x, y, x + 7, y - 5);
		}

		g.setColor(NIB);
		g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine(40, 110, 30, 120);

		g.dispose();

		File target = new File(args.length > 0 ? args[0] : "icon.png");
		File parent = target.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		ImageIO.write(image, "PNG", target);
		System.out.println("Wrote " + target.getAbsolutePath());
	}
}
