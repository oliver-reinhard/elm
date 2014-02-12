package elm.scheduler;

import java.io.IOException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class HomeServerLocator {
	
	public static void main(String[] args) {
		System.out.println("Starting service listener.");
		try {
			JmDNS jmDNS = JmDNS.create();
			jmDNS.addServiceListener("_clage-hs._tcp.local.", new ServiceListener() {
				
				@Override
				public void serviceResolved(ServiceEvent e) {
					print("resolved", e);
				}
				
				@Override
				public void serviceRemoved(ServiceEvent e) {
					print("removed", e);
				}
				
				@Override
				public void serviceAdded(ServiceEvent e) {
					print("added", e);
				}
				
				void print(String desc, ServiceEvent e) {
					System.out.println("Service " + desc + ": " + e.getType() + ", " + e.getName());
				}
			});
		} catch (IOException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
	}

}
