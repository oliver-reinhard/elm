package elm.scheduler.model;

import static elm.scheduler.model.DeviceInfo.NO_POWER;
import static elm.scheduler.model.DeviceInfo.UNDEFINED_TEMPERATURE;
import static elm.scheduler.model.DeviceInfo.UNLIMITED_POWER;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_LIMITED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.INITIALIZING;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.READY;
import static elm.scheduler.model.ModelTestUtil.createDeviceWithInfo;
import static elm.scheduler.model.ModelTestUtil.createDeviceWithStatus;
import static elm.scheduler.model.ModelTestUtil.round;
import static elm.scheduler.model.ModelTestUtil.toPowerUnits;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import elm.hs.api.model.Device;
import elm.hs.api.model.DeviceCharacteristics.DeviceModel;
import elm.scheduler.model.DeviceInfo.UpdateResult;
import elm.scheduler.model.impl.DeviceInfoImpl;
import elm.ui.api.ElmStatus;

public class DeviceInfoTest {

	static final String ID = "d1";
	static final int EXPECTED_WAITING_TIME = 5_000;

	HomeServer hs1;
	DeviceInfoImpl di1;

	@Before
	public void setup() {
		hs1 = mock(HomeServer.class);
		try {
			di1 = new DeviceInfoImpl(hs1, createDeviceWithInfo(1, 1), ID);
		} catch (UnsupportedModelException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Test
	public void updateNeedStatus() {
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
		assertEquals(UpdateResult.MINOR_UPDATES, result);
		assertEquals(READY, di1.getStatus());
		//
	}

	@Test
	public void updateDevicePower() {
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());

		final Device d = createDeviceWithStatus(1, 1, 10_000);
		UpdateResult result = di1.update(d);
		assertEquals(round(10_000), di1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());

		result = di1.update(createDeviceWithStatus(1, 1, 0));
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UpdateResult.MINOR_UPDATES, result);
		assertEquals(READY, di1.getStatus());
	}

	@Test
	public void updateIntakeTemperature() {
		final Device d = createDeviceWithStatus(1, 1, 0);
		assert d.status.tIn == 100; // 10°C
		// initialize DeviceInfo:
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
	public void updatePowerUnlimited() {
		di1.update(createDeviceWithStatus(1, 1, 10_000));
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());
		//
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.ON, 0);
		assertEquals(CONSUMPTION_APPROVED, di1.getStatus());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getScaldProtectionTemperature());
		verify(hs1).putDeviceUpdate(Mockito.<AsynchronousPhysicalDeviceUpdate> any());
	}

	@Test
	public void updatePowerLimited() {
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
		verify(hs1).putDeviceUpdate(Mockito.<AsynchronousPhysicalDeviceUpdate> any());
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
		verify(hs1, times(2)).putDeviceUpdate(Mockito.<AsynchronousPhysicalDeviceUpdate> any());
		// next poll returns restored setpoint temperature:
		d.status.setpoint = referenceTemperature;
		di1.update(d);
		assertEquals(referenceTemperature, di1.getActualDemandTemperature());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getUserDemandTemperature());
	}

	@Test
	public void updatePowerDenied() {
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
		verify(hs1).putDeviceUpdate(Mockito.<AsynchronousPhysicalDeviceUpdate> any());
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
		verify(hs1, times(2)).putDeviceUpdate(Mockito.<AsynchronousPhysicalDeviceUpdate> any());
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
		verify(hs1, times(3)).putDeviceUpdate(Mockito.<AsynchronousPhysicalDeviceUpdate> any());
		d.status.setpoint = 380;
		di1.update(d);
		assertEquals(380, di1.getActualDemandTemperature());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getUserDemandTemperature());
	}
}
