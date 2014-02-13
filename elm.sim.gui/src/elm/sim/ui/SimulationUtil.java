package elm.sim.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.WeakHashMap;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import elm.sim.model.SimStatus;

public class SimulationUtil {
	
	private SimulationUtil() {
		// prevent instantiation
	}

	/** Cache for status icons. */
	private static final WeakHashMap<SimStatus, ImageIcon>	statusIcons	= new WeakHashMap<SimStatus, ImageIcon>();
	
	/** Cache for icons specified by filename. */
	private static final WeakHashMap<String, ImageIcon>	otherIcons	= new WeakHashMap<String, ImageIcon>();

	public static ImageIcon getIcon(SimStatus status) {
		if (statusIcons.containsKey(status)) {
			return statusIcons.get(status); // can be null !
		}
		try {
			String filename = "icons/status_" + status.name().toLowerCase() + ".png";
			BufferedImage img = ImageIO.read(new File(filename));
			ImageIcon icon = new ImageIcon(img);
			statusIcons.put(status, icon);
			return icon;
		} catch (IOException ex) {
			statusIcons.put(status, null);
			return null;
		}
	}

	public static ImageIcon getIcon(String name) {
		assert name != null && ! name.isEmpty();
		
		if (otherIcons.containsKey(name)) {
			return otherIcons.get(name); // can be null !
		}
		try {
			String filename = "icons/" + name + ".png";
			BufferedImage img = ImageIO.read(new File(filename));
			ImageIcon icon = new ImageIcon(img);
			otherIcons.put(name, icon);
			return icon;
		} catch (IOException ex) {
			otherIcons.put(name, null);
			return null;
		}
	}

}
