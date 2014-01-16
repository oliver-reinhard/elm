package elm.sim.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class SimulatorUI extends JFrame {

	private static final long	serialVersionUID	= 1L;
	private OutletUI		outlet_1;

	public SimulatorUI() {
		setTitle("Dusche");
		setSize(200, 300);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		outlet_1 = new OutletUI("Dusche");
		getContentPane().add(outlet_1);
		outlet_1.setModel(new OutletModel(null, Flow.HIGH, Temperature.TEMP_1, false));
		// outlet_1.setModel(new SchedulerModel(70));
		outlet_1.setModel(new SchedulerModel(Status.SATURATION));
		outlet_1.addPropertyChangeListener(new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				System.out.println("PCL: source = " + evt.getSource());
				System.out.println("PCL: value = " + evt.getPropertyName() + ": " + evt.getOldValue() + " --> " + evt.getNewValue());
			}
		});
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				SimulatorUI ex = new SimulatorUI();
				ex.setVisible(true);
			}
		});
	}

}
