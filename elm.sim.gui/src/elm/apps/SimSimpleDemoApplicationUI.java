package elm.apps;

import elm.scheduler.model.UnsupportedModelException;
import elm.sim.ui.SimServerApplicationUI;
import elm.sim.ui.SimpleSimServerApplicationConfiguration;
import elm.util.ElmLogFormatter;

public class SimSimpleDemoApplicationUI {

	static final int SERVER_PORT = 9090;

	public static void main(String[] args) throws UnsupportedModelException {
		try {
			ElmLogFormatter.init();

			SimpleSimServerApplicationConfiguration configuration = new SimpleSimServerApplicationConfiguration();
			configuration.init(true, SERVER_PORT);
			SimServerApplicationUI ui = new SimServerApplicationUI(configuration);
			ui.setVisible(true);
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
