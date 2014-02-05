package elm.scheduler.model.impl;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.model.DeviceInfo;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.UnsupportedModelException;

public class DeviceInfoImpl implements DeviceInfo {

	private final String id;
	private final HomeServer homeServer;
	private final String name;
	private DeviceModel model;
	private State state;

	private long consumptionStartTime = 0L;
	private int demandPowerWatt = 0;
	private int actualPowerWatt = 0;
	private short powerMaxUnits;

	public DeviceInfoImpl(HomeServer server, Device device) throws UnsupportedModelException {
		this(server, device, null);
	}

	public DeviceInfoImpl(HomeServer server, Device device, String name) throws UnsupportedModelException {
		assert server != null;
		assert device != null;
		assert device.status != null;
		this.id = device.id;
		this.homeServer = server;
		this.name = (name == null || name.isEmpty()) ? device.id : name;
		
		model = DeviceModel.getModel(device);
		if (!model.getType().isRemoteControllable()) {
			throw new UnsupportedModelException(device.id);
		}
		
		powerMaxUnits = device.status.powerMax;
		state = State.NOT_CONNECTED;
		update(device);
	}

	@Override
	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	@Override
	public HomeServer getHomeServer() {
		return homeServer;
	}

	@Override
	public UpdateResult update(Device device) {
		assert device != null;
		UpdateResult result = UpdateResult.NO_UPDATES;
		
		if (device.connected && state == State.NOT_CONNECTED) {
			setState(State.READY);
		} else if (!device.connected && state != State.ERROR) {
			setState(State.NOT_CONNECTED);
		}
		
		if (device.status != null) {
			final int newDemandPowerWatt = toPowerWatt(device.status.power);
			if (newDemandPowerWatt != demandPowerWatt) {
				if (demandPowerWatt == 0) {
					waterConsumptionStarted(newDemandPowerWatt);
				} else if (newDemandPowerWatt == 0) {
					waterConsumptionEnded();
				} else {
					demandPowerWatt = newDemandPowerWatt;
				}
				result = result.and(newDemandPowerWatt > 0 ? UpdateResult.URGENT_UPDATES : UpdateResult.MINOR_UPDATES);
			}
		}
		return result;
	}

	@Override
	public State getState() {
		return state;
	}

	protected void setState(State state) {
		this.state = state;
	}

	/**
	 * Invoked by the actual physical device.
	 * 
	 * @param demandPowerUnits
	 *            the power needed to satisfy the user demand (temperature, flow).
	 */
	public synchronized void waterConsumptionStarted(int demandPowerWatt) {
		this.demandPowerWatt = demandPowerWatt;
		consumptionStartTime = System.currentTimeMillis();
		setState(State.CONSUMING);
	}

	/**
	 * Invoked by the actual physical device.
	 */
	public synchronized void waterConsumptionEnded() {
		demandPowerWatt = 0;
		actualPowerWatt = 0;
		consumptionStartTime = 0L;
		setState(State.READY);
	}

	@Override
	public synchronized void powerConsumptionApproved(int actualPowerWatt) {
		this.actualPowerWatt = actualPowerWatt;
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

	public void setDemandPowerWatt(int demandPowerWatt) {
		this.demandPowerWatt = demandPowerWatt;

	}

	@Override
	public int getActualPowerWatt() {
		return actualPowerWatt;
	}

	@Override
	public void setActualPowerWatt(int actualPower) {
		this.actualPowerWatt = actualPower;
	}

	int toPowerWatt(short powerUnits) {
		assert powerMaxUnits != 0;
		return model.getPowerMaxWatt() * powerUnits / powerMaxUnits;
	}

	short toPowerUnits(int powerWatt) {
		assert powerMaxUnits != 0;
		return (short) (powerWatt * powerMaxUnits / model.getPowerMaxWatt());
	}

}
