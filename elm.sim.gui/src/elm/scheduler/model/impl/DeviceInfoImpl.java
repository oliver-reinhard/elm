package elm.scheduler.model.impl;

import elm.hs.api.model.Device;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.HomeServer;

public class DeviceInfoImpl implements DeviceInfo {

	private final String id;
	private final HomeServer homeServer;
	private State state = State.NOT_CONNECTED;
	
	private long consumptionStartTime = 0L;
	private int demandPowerWatt = 0;
	private int actualPowerWatt = 0;

	public DeviceInfoImpl(String id, HomeServer server) {
		assert id != null && ! id.isEmpty();
		assert server != null;
		this.id = id;
		this.homeServer = server;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public HomeServer getHomeServer() {
		return homeServer;
	}

	@Override
	public UpdateResult update(Device device) {
		assert device != null;
		UpdateResult result = UpdateResult.NO_UPDATES;
		// TODO
		return result;
	}

	@Override
	public State getState() {
		return state;
	}
	
	protected void setState(State state) {
		this.state = state;
	}

	@Override
	public synchronized void waterConsumptionStarted(int demandPower) {
		demandPowerWatt = demandPower;
		consumptionStartTime = System.currentTimeMillis();
		setState(State.CONSUMING);
	}

	@Override
	public synchronized void waterConsumptionEnded() {
		demandPowerWatt = 0;
		actualPowerWatt = 0;
		consumptionStartTime = 0L;
		setState(State.READY);
	}

	@Override
	public synchronized void powerConsumptionApproved(int actualPower) {
		actualPowerWatt = actualPower;
		setState(State.CONSUMING);
	}

	@Override
	public synchronized void powerConsumptionDenied() {
		actualPowerWatt = 0;
		setState(State.WAITING);
	}

	@Override
	public long getConsumptionStartTime() {
		return consumptionStartTime;
	}

	@Override
	public int getDemandPowerWatt() {
		return demandPowerWatt;
	}

	@Override
	public int getActualPowerWatt() {
		return actualPowerWatt;
	}

	@Override
	public void setActualPowerWatt(int actualPower) {
		this.actualPowerWatt = actualPower;
		// TODO physically reduce power limit
	}

}
