package elm.scheduler.model.impl;

import static elm.scheduler.model.DeviceController.NO_POWER;
import static elm.scheduler.model.DeviceController.UNDEFINED_TEMPERATURE;
import static elm.scheduler.model.DeviceController.UNLIMITED_POWER;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_ENDED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_LIMITED;
import static elm.scheduler.model.DeviceController.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceController.DeviceStatus.ERROR;
import static elm.scheduler.model.DeviceController.DeviceStatus.INITIALIZING;
import static elm.scheduler.model.DeviceController.DeviceStatus.READY;
import static elm.scheduler.model.impl.ModelTestUtil.createDeviceWithInfo;
import static elm.scheduler.model.impl.ModelTestUtil.createDeviceWithStatus;
import static elm.scheduler.model.impl.ModelTestUtil.round;
import static elm.scheduler.model.impl.ModelTestUtil.toPowerUnits;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import elm.hs.api.model.Device;
import elm.hs.api.model.ElmStatus;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.model.AsynchRemoteDeviceUpdate;
import elm.scheduler.model.DeviceController.UpdateResult;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.UnsupportedModelException;

public class DeviceControllerTest {

	static final String ID = "d1";
	static final int EXPECTED_WAITING_TIME = 5_000;

	HomeServer hs1;
	DeviceControllerImpl di1;

	@Before
	public void setup() {
		hs1 = mock(HomeServer.class);
		try {
			di1 = new DeviceControllerImpl(hs1, createDeviceWithInfo(1, 1), ID);
		} catch (UnsupportedModelException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Test
	public void update_NeedsStatus() {
		// update(): Status object required for given Device
		assertEquals(INITIALIZING, di1.getStatus());
		Device d = createDeviceWithInfo(1, 1);
		UpdateResult result = di1.update(d);
		assertEquals(UpdateResult.DEVICE_STATUS_REQUIRED, result);
		assertEquals(INITIALIZING, di1.getStatus());
		//
		d = createDeviceWithStatus(1, 1, 0);
		result = di1.update(d);
		assertEquals(UpdateResult.MINOR_UPDATES, result);
		assertEquals(READY, di1.getStatus());
		//
		d = createDeviceWithStatus(1, 1, 0);
		result = di1.update(d);
		assertEquals(UpdateResult.NO_UPDATES, result);
		assertEquals(READY, di1.getStatus());
		//
		d = createDeviceWithInfo(1, 1);
		d.info.flags = 0; // means heater ON
		result = di1.update(d);
		assertEquals(UpdateResult.DEVICE_STATUS_REQUIRED, result);
		//
		d = createDeviceWithStatus(1, 1, 10_000);
		assert d.status.flags == 0; // means heater ON
		result = di1.update(d);
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		//
		d = createDeviceWithStatus(1, 1, 0);  // heater OFF
		result = di1.update(d);
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_ENDED, di1.getStatus());
		//
	}

	@Test
	public void update_DevicePower() {
		// update(): demand power has changed
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());

		UpdateResult result = di1.update(createDeviceWithStatus(1, 1, 10_000));  // device turned ON
		assertEquals(round(10_000), di1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());

		result = di1.update(createDeviceWithStatus(1, 1, 0));  // device turned OFF
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_ENDED, di1.getStatus());
	}

	@Test
	public void update_IntakeWaterTemperature() {
		// update(): intake water temperature has changed
		final Device d = createDeviceWithStatus(1, 1, 0);
		assert d.status.tIn == 100; // 10°C
		// initialize DeviceManager:
		UpdateResult result = di1.update(d);
		assertEquals(100, di1.getIntakeWaterTemperature());
		assertEquals(UpdateResult.MINOR_UPDATES, result);

		d.status.tIn = 110; // 11°C
		result = di1.update(d);
		// minor difference ignored:
		assertEquals(100, di1.getIntakeWaterTemperature());
		assertEquals(UpdateResult.NO_UPDATES, result);

		d.status.tIn = 125; // 12.5°C
		result = di1.update(d);
		assertEquals(125, di1.getIntakeWaterTemperature());
		// minor update while not consuming:
		assertEquals(UpdateResult.MINOR_UPDATES, result);

		d.status.power = toPowerUnits(10_000);
		di1.update(d);
		d.status.tIn = 100; // 10°C
		result = di1.update(d);
		assertEquals(100, di1.getIntakeWaterTemperature());
		// urgent update while consuming:
		assertEquals(UpdateResult.URGENT_UPDATES, result);
	}

	@Test
	public void updatePowerConsumption_Unlimited() {
		// updateMaximumPowerConsumption():
		di1.update(createDeviceWithStatus(1, 1, 10_000));  // turn ON
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());
		//
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.ON, 0);
		assertEquals(CONSUMPTION_APPROVED, di1.getStatus());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getScaldProtectionTemperature());
		verify(hs1).putDeviceUpdate(Mockito.<AsynchRemoteDeviceUpdate> any());
		//
		di1.update(createDeviceWithStatus(1, 1, 0));  // turn OFF
		assertEquals(CONSUMPTION_ENDED, di1.getStatus());
		//
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.ON, 0);
		assertEquals(READY, di1.getStatus());
	}

	@Test
	public void updatePowerConsumption_Limited() {
		final Device d = createDeviceWithStatus(1, 1, 10_000);
		assert d.status.setpoint > 0;
		final short referenceTemperature = d.status.setpoint;
		di1.update(d);
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());

		// APPROVED = 5_000
		di1.updateMaximumPowerConsumption(5_000, ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
		assertEquals(CONSUMPTION_LIMITED, di1.getStatus());
		assertEquals(5_000, di1.getApprovedPowerWatt());
		assertEquals(242, di1.getScaldProtectionTemperature());
		verify(hs1).putDeviceUpdate(Mockito.<AsynchRemoteDeviceUpdate> any());
		// next poll returns setpoint = scald-protection temperature:
		d.status.setpoint = 242;
		di1.update(d);
		assertEquals(242, di1.getActualDemandTemperature());
		assertEquals(referenceTemperature, di1.getUserDemandTemperature());

		// APPROVED = Unlimited
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.SATURATION, 0);
		assertEquals(CONSUMPTION_APPROVED, di1.getStatus());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getScaldProtectionTemperature());
		verify(hs1, times(2)).putDeviceUpdate(Mockito.<AsynchRemoteDeviceUpdate> any());
		// next poll returns restored setpoint temperature:
		d.status.setpoint = referenceTemperature;
		di1.update(d);
		assertEquals(referenceTemperature, di1.getActualDemandTemperature());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getUserDemandTemperature());
	}

	@Test
	public void updatePowerConsumption_Denied() {
		final Device d = createDeviceWithStatus(1, 1, 10_000);
		d.status.setpoint = 380;
		di1.update(d);
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());
		//
		di1.updateMaximumPowerConsumption(NO_POWER, ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
		assertEquals(CONSUMPTION_DENIED, di1.getStatus());
		assertEquals(0, di1.getApprovedPowerWatt());
		final short minScaldTemp = DeviceModel.SIM.getScaldProtectionTemperatureMin();
		assertEquals(minScaldTemp, di1.getScaldProtectionTemperature());
		verify(hs1).putDeviceUpdate(Mockito.<AsynchRemoteDeviceUpdate> any());
		// next poll returns setpoint = scald-protection temperature:
		d.status.setpoint = minScaldTemp;
		di1.update(d);
		assertEquals(minScaldTemp, di1.getActualDemandTemperature());
		assertEquals(380, di1.getUserDemandTemperature());

		// APPROVED = 5_000
		di1.updateMaximumPowerConsumption(5_000, ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
		assertEquals(CONSUMPTION_LIMITED, di1.getStatus());
		assertEquals(5_000, di1.getApprovedPowerWatt());
		assertEquals(242, di1.getScaldProtectionTemperature());
		verify(hs1, times(2)).putDeviceUpdate(Mockito.<AsynchRemoteDeviceUpdate> any());
		// next poll returns setpoint = scald-protection temperature:
		d.status.setpoint = 242;
		di1.update(d);
		assertEquals(242, di1.getActualDemandTemperature());
		assertEquals(380, di1.getUserDemandTemperature());

		// APPROVED = Unlimited
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.SATURATION, 0);
		assertEquals(CONSUMPTION_APPROVED, di1.getStatus());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getScaldProtectionTemperature());
		verify(hs1, times(3)).putDeviceUpdate(Mockito.<AsynchRemoteDeviceUpdate> any());
		d.status.setpoint = 380;
		di1.update(d);
		assertEquals(380, di1.getActualDemandTemperature());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getUserDemandTemperature());
	}

	@Test
	public void putElmStatus() {
		AsynchRemoteDeviceUpdate deviceUpdate = new AsynchRemoteDeviceUpdate(di1);
		di1.setStatus(READY);
		di1.putElmStatus(deviceUpdate, READY, ElmStatus.ON, 0);
		assertEquals(ElmStatus.ON, deviceUpdate.getFeedback().deviceStatus);
		//
		di1.putElmStatus(deviceUpdate, READY, ElmStatus.SATURATION, 0);
		assertEquals(ElmStatus.SATURATION, deviceUpdate.getFeedback().deviceStatus);
		//
		di1.putElmStatus(deviceUpdate, READY, ElmStatus.OVERLOAD, 0);
		assertEquals(ElmStatus.OVERLOAD, deviceUpdate.getFeedback().deviceStatus);
		

		di1.setStatus(CONSUMPTION_STARTED);
		// elmStatus is irrelevant:
		for (ElmStatus elmStatus : ElmStatus.values()) {
			di1.putElmStatus(deviceUpdate, CONSUMPTION_STARTED, elmStatus, 0);
			assertEquals(ElmStatus.ON, deviceUpdate.getFeedback().deviceStatus);
		}
		
		di1.setStatus(CONSUMPTION_APPROVED);
		// elmStatus is irrelevant:
		for (ElmStatus elmStatus : ElmStatus.values()) {
			di1.putElmStatus(deviceUpdate, CONSUMPTION_APPROVED, elmStatus, 0);
			assertEquals(ElmStatus.ON, deviceUpdate.getFeedback().deviceStatus);
		}
		
		di1.setStatus(CONSUMPTION_LIMITED);
		// elmStatus is irrelevant:
		for (ElmStatus elmStatus : ElmStatus.values()) {
			di1.putElmStatus(deviceUpdate, CONSUMPTION_LIMITED, elmStatus, 0);
			assertEquals(ElmStatus.SATURATION, deviceUpdate.getFeedback().deviceStatus);
		}
		
		di1.setStatus(CONSUMPTION_DENIED);
		// elmStatus is irrelevant:
		for (ElmStatus elmStatus : ElmStatus.values()) {
			di1.putElmStatus(deviceUpdate, CONSUMPTION_DENIED, elmStatus, 0);
			assertEquals(ElmStatus.OVERLOAD, deviceUpdate.getFeedback().deviceStatus);
		}
		
		di1.setStatus(ERROR);
		// elmStatus is irrelevant:
		for (ElmStatus elmStatus : ElmStatus.values()) {
			di1.putElmStatus(deviceUpdate, ERROR, elmStatus, 0);
			assertEquals(ElmStatus.ERROR, deviceUpdate.getFeedback().deviceStatus);
		}
	}
}
