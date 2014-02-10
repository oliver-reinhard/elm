package elm.scheduler.model;

import static elm.scheduler.model.DeviceInfo.NO_POWER;
import static elm.scheduler.model.DeviceInfo.UNDEFINED_TEMPERATURE;
import static elm.scheduler.model.DeviceInfo.UNLIMITED_POWER;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_APPROVED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_DENIED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_LIMITED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.CONSUMPTION_STARTED;
import static elm.scheduler.model.DeviceInfo.DeviceStatus.READY;
import static elm.scheduler.model.ModelTestUtil.createDevice;
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
import elm.scheduler.ElmStatus;
import elm.scheduler.model.DeviceInfo.UpdateResult;
import elm.scheduler.model.impl.DeviceInfoImpl;

public class DeviceInfoTest {

	static final String ID = "d1";

	HomeServer hs1;
	DeviceInfoImpl di1;

	@Before
	public void setup() {
		hs1 = mock(HomeServer.class);
		try {
			di1 = new DeviceInfoImpl(hs1, createDevice(1, 1, 0), ID);
		} catch (UnsupportedModelException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Test
	public void updateDevicePower() {
		assertEquals(READY, di1.getStatus());
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(DeviceModel.DSX.getPowerMaxWatt(), di1.getApprovedPowerWatt());

		final Device d = createDevice(1, 1, 10_000);
		UpdateResult result = di1.update(d);
		assertEquals(round(10_000), di1.getDemandPowerWatt());
		assertEquals(DeviceModel.DSX.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());

		result = di1.update(createDevice(1, 1, 0));
		assertEquals(0, di1.getDemandPowerWatt());
		assertEquals(DeviceModel.DSX.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UpdateResult.MINOR_UPDATES, result);
		assertEquals(READY, di1.getStatus());
	}

	@Test
	public void updateIntakeTemperature() {
		final Device d = createDevice(1, 1, 0);
		assert d.status.tIn == 100; // 10°C

		d.status.tIn = 110; // 11°C
		UpdateResult result = di1.update(d);
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
	public void powerUnlimited() {
		di1.update(createDevice(1, 1, 10_000));
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());
		//
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.ON);
		assertEquals(CONSUMPTION_APPROVED, di1.getStatus());
		assertEquals(DeviceModel.DSX.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getScaldProtectionTemperature());
		verify(hs1).putDeviceUpdate(Mockito.<SetScaldProtectionTemperature>any());
	}

	@Test
	public void powerLimited() {
		final Device d = createDevice(1, 1, 10_000);
		assert d.status.setpoint > 0;
		final short referenceTemperature = d.status.setpoint;
		di1.update(d);
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());
		
		// APPROVED = 5_000
		di1.updateMaximumPowerConsumption(5_000, ElmStatus.OVERLOAD);
		assertEquals(CONSUMPTION_LIMITED, di1.getStatus());
		assertEquals(5_000, di1.getApprovedPowerWatt());
		assertEquals(242, di1.getScaldProtectionTemperature());
		verify(hs1).putDeviceUpdate(Mockito.<SetScaldProtectionTemperature>any());
		// next poll returns setpoint = scald-protection temperature:
		d.status.setpoint = 242;
		di1.update(d);
		assertEquals(242, di1.getActualDemandTemperature());
		assertEquals(referenceTemperature, di1.getUserDemandTemperature());
		
		// APPROVED = Unlimited
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.SATURATION);
		assertEquals(CONSUMPTION_APPROVED, di1.getStatus());
		assertEquals(DeviceModel.DSX.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getScaldProtectionTemperature());
		verify(hs1, times(2)).putDeviceUpdate(Mockito.<AsynchronousDeviceUpdate>any());
		// next poll returns restored setpoint temperature:
		d.status.setpoint = referenceTemperature;
		di1.update(d);
		assertEquals(referenceTemperature, di1.getActualDemandTemperature());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getUserDemandTemperature());
	}

	@Test
	public void powerDenied() {
		final Device d = createDevice(1, 1, 10_000);
		d.status.setpoint = 380;
		di1.update(d);
		assertEquals(CONSUMPTION_STARTED, di1.getStatus());
		//
		di1.updateMaximumPowerConsumption(NO_POWER, ElmStatus.OVERLOAD);
		assertEquals(CONSUMPTION_DENIED, di1.getStatus());
		assertEquals(0, di1.getApprovedPowerWatt());
		final short minScaldTemp = DeviceModel.DSX.getScaldProtectionTemperatureMin();
		assertEquals(minScaldTemp, di1.getScaldProtectionTemperature());
		verify(hs1).putDeviceUpdate(Mockito.<SetScaldProtectionTemperature>any());
		// next poll returns setpoint = scald-protection temperature:
		d.status.setpoint = minScaldTemp;
		di1.update(d);
		assertEquals(minScaldTemp, di1.getActualDemandTemperature());
		assertEquals(380, di1.getUserDemandTemperature());

		// APPROVED = 5_000
		di1.updateMaximumPowerConsumption(5_000, ElmStatus.OVERLOAD);
		assertEquals(CONSUMPTION_LIMITED, di1.getStatus());
		assertEquals(5_000, di1.getApprovedPowerWatt());
		assertEquals(242, di1.getScaldProtectionTemperature());
		verify(hs1, times(2)).putDeviceUpdate(Mockito.<SetScaldProtectionTemperature>any());
		// next poll returns setpoint = scald-protection temperature:
		d.status.setpoint = 242;
		di1.update(d);
		assertEquals(242, di1.getActualDemandTemperature());
		assertEquals(380, di1.getUserDemandTemperature());
		
		// APPROVED = Unlimited
		di1.updateMaximumPowerConsumption(UNLIMITED_POWER, ElmStatus.SATURATION);
		assertEquals(CONSUMPTION_APPROVED, di1.getStatus());
		assertEquals(DeviceModel.DSX.getPowerMaxWatt(), di1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getScaldProtectionTemperature());
		verify(hs1, times(3)).putDeviceUpdate(Mockito.<AsynchronousDeviceUpdate>any());
		d.status.setpoint = 380;
		di1.update(d);
		assertEquals(380, di1.getActualDemandTemperature());
		assertEquals(UNDEFINED_TEMPERATURE, di1.getUserDemandTemperature());
	}
}
