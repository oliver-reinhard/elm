package elm.sim.metamodel;

public interface SimChangeNotifier {

	void addModelListener(SimModelListener listener);

	void removeModelListener(SimModelListener listener);

}