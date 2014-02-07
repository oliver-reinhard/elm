package elm.scheduler.model.impl;

import java.text.SimpleDateFormat;
import java.util.Date;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.model.DeviceInfo;
import static elm.scheduler.model.DeviceInfo.State.*;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.UnsupportedModelException;

public class DeviceInfoImpl implements DeviceInfo {

	private final String id;
	private final HomeServer homeServer;
	private final String name;
	private State state;
	private DeviceModel model;
	
	/** Model-dependent. */
	private short powerMaxUnits;
	
	private long consumptionStartTime = 0L;
	
	/** The power to consume demanded by the user. */ 
	private int demandPowerWatt = 0;
	
	/** The power to consume allowed by the scheduler. */
	private int approvedPowerWatt = 0;

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
		state = NOT_CONNECTED;
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
		
		if (device.connected && state == NOT_CONNECTED) {
			setState(READY);
		} else if (!device.connected && state != ERROR) {
			setState(NOT_CONNECTED);
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
		setState(CONSUMING);
	}

	/**
	 * Invoked by the actual physical device.
	 */
	public synchronized void waterConsumptionEnded() {
		demandPowerWatt = 0;
		approvedPowerWatt = 0;
		consumptionStartTime = 0L;
		setState(READY);
	}

	@Override
	public synchronized void powerConsumptionApproved(int approvedPowerWatt) {
		this.approvedPowerWatt = (approvedPowerWatt == UNLIMITED_POWER) ? model.getPowerMaxWatt() : approvedPowerWatt;
		if (consumptionStartTime != 0) {
			setState(CONSUMING);
		} else {
			setState(READY);
		}
	}

	@Override
	public synchronized void powerConsumptionDenied() {
		approvedPowerWatt = 0;
		setState(WAITING);
	}

	@Override
	public long getConsumptionStartTime() {
		return consumptionStartTime;
	}

	@Override
	public int getDemandPowerWatt() {
		return demandPowerWatt;
	}

	public void setDemandPowerWatt(int value) {
		this.demandPowerWatt = value;

	}

	@Override
	public int getApprovedPowerWatt() {
		return approvedPowerWatt;
	}
	
	@Override
	public int getScaldTemperature() {
		assert approvedPowerWatt >= 0;
		// TODO enhance -- this is a simplified result
		return model.getScaldTemperatureMin() + (model.getScaldTemperatureMax() - model.getScaldTemperatureMin()) * approvedPowerWatt / model.getPowerMaxWatt(); 
	}

	int toPowerWatt(short powerUnits) {
		assert powerMaxUnits != 0;
		return model.getPowerMaxWatt() * powerUnits / powerMaxUnits;
	}

	short toPowerUnits(int powerWatt) {
		assert powerMaxUnits != 0;
		return (short) (powerWatt * powerMaxUnits / model.getPowerMaxWatt());
	}
	
	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder(getName());
		b.append("(");
		b.append(getState());
		if (getState() == CONSUMING) {
			b.append(", start: ");
			b.append(new SimpleDateFormat().format(new Date(consumptionStartTime)));
		}
		b.append(")");
		return b.toString();
	}
}
