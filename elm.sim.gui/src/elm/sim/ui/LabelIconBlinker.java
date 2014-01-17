package elm.sim.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

class LabelIconBlinker implements Runnable {
	private final List<JLabel>	labels	= new ArrayList<JLabel>();
	private Thread				running;
	private boolean				shouldStop;

	private final ImageIcon		iconOn;
	private final ImageIcon		iconOff;

	public LabelIconBlinker(ImageIcon iconOn, ImageIcon iconOff) {
		assert iconOn != null;
		assert iconOff != null;
		this.iconOn = iconOn;
		this.iconOff = iconOff;
	}

	public synchronized void start(JLabel label) {
		if (!labels.contains(label)) {
			labels.add(label);
			if (running == null) {
				running = new Thread(this, "Blinker");
				shouldStop = false;
				running.start();
			}
		}
	}

	public synchronized void stop(JLabel label) {
		labels.remove(label);
		if (labels.isEmpty() && running != null) {
			this.notify();
			shouldStop = true;
			running = null;
		}
	}

	@Override
	public synchronized void run() {
		try {
			while (!shouldStop) {
				this.wait(500);
				blink(iconOn);
				if (shouldStop) {
					break;
				}
				this.wait(500);
				blink(iconOff);
			}
		} catch (InterruptedException e) {
			// exited the loop, goal reached
		}
		blink(iconOff);
		running = null;
	}

	private void blink(final ImageIcon icon) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				for (JLabel label : labels) {
					label.setIcon(icon);
				}
			}
		});
	}
}