package elm.sim.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.WeakHashMap;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class SimulationUtil {
	
	private SimulationUtil() {
		// prevent instantiation
	}

	/** Cache for status icons. */
	private static final WeakHashMap<Status, ImageIcon>	statusIcons	= new WeakHashMap<Status, ImageIcon>();

	public static ImageIcon getIcon(Status status) {
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

}
