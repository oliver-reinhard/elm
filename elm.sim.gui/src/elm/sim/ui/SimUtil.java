package elm.sim.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.WeakHashMap;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import elm.sim.model.SimStatus;

public class SimUtil {

	private SimUtil() {
		// prevent instantiation
	}

	/** Cache for status icons. */
	private static final WeakHashMap<SimStatus, ImageIcon> statusIcons = new WeakHashMap<SimStatus, ImageIcon>();

	/** Cache for icons specified by filename. */
	private static final WeakHashMap<String, ImageIcon> otherIcons = new WeakHashMap<String, ImageIcon>();

	/**
	 * Gets the icon from the classpath via the class loader.
	 * 
	 * @param status
	 *            cannot be {@code null}
	 * @return {@code null} on {@link IOException}s
	 */
	public static ImageIcon getIcon(SimStatus status) {
		assert status != null;
		if (statusIcons.containsKey(status)) {
			return statusIcons.get(status); // can be null !
		}
		try {
			String filename = "icons/status_" + status.name().toLowerCase() + ".png";
			InputStream stream = SimUtil.class.getClassLoader().getResourceAsStream(filename);
			BufferedImage img = ImageIO.read(stream);
			ImageIcon icon = new ImageIcon(img);
			statusIcons.put(status, icon);
			return icon;
		} catch (IOException ex) {
			statusIcons.put(status, null);
			return null;
		}
	}

	/**
	 * Gets the icon from the classpath via the class loader.
	 * 
	 * @param name
	 *            cannot be {@code null} or empty
	 * @return {@code null} on {@link IOException}s
	 */
	public static ImageIcon getIcon(String name) {
		assert name != null && !name.isEmpty();

		if (otherIcons.containsKey(name)) {
			return otherIcons.get(name); // can be null !
		}
		try {
			String filename = "icons/" + name + ".png";
			InputStream stream = SimUtil.class.getClassLoader().getResourceAsStream(filename);
			BufferedImage img = ImageIO.read(stream);
			ImageIcon icon = new ImageIcon(img);
			otherIcons.put(name, icon);
			return icon;
		} catch (IOException ex) {
			otherIcons.put(name, null);
			return null;
		}
	}

}
