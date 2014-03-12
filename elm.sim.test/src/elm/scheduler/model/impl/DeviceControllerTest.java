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
import static elm.scheduler.model.impl.ModelTestUtil.FLOW_OFF;
import static elm.scheduler.model.impl.ModelTestUtil.FLOW_ON;
import static elm.scheduler.model.impl.ModelTestUtil.createDeviceWithInfo;
import static elm.scheduler.model.impl.ModelTestUtil.createDeviceWithStatus;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import elm.hs.api.Device;
import elm.hs.api.ElmStatus;
import elm.hs.api.DeviceCharacteristics.DeviceModel;
import elm.scheduler.model.DeviceController.UpdateResult;
import elm.scheduler.model.HomeServer;
import elm.scheduler.model.RemoteDeviceUpdate;
import elm.scheduler.model.UnsupportedDeviceModelException;

public class DeviceControllerTest {

	static final String ID = "d1";
	static final int EXPECTED_WAITING_TIME = 5_000;

	HomeServer hs;
	DeviceControllerImpl dc1;

	@Before
	public void setup() {
		hs = mock(HomeServer.class);
		try {
			dc1 = new DeviceControllerImpl(hs, createDeviceWithInfo(1, 1), ID);
		} catch (UnsupportedDeviceModelException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Test
	public void update_NeedsStatus() {
		// update(): Status object required for given Device
		assertEquals(INITIALIZING, dc1.getStatus());
		Device d = createDeviceWithInfo(1, 1);
		UpdateResult result = dc1.update(d);
		assertEquals(UpdateResult.DEVICE_STATUS_REQUIRED, result);
		assertEquals(INITIALIZING, dc1.getStatus());
		//
		d = createDeviceWithStatus(1, 1, 0, FLOW_OFF);
		result = dc1.update(d);
		assertEquals(UpdateResult.NO_UPDATES, result);
		assertEquals(READY, dc1.getStatus());
		//
		d = createDeviceWithInfo(1, 1);
		d.info.flags = 0; // means heater ON
		result = dc1.update(d);
		assertEquals(UpdateResult.DEVICE_STATUS_REQUIRED, result);
		//
		d = createDeviceWithStatus(1, 1, 10_000, FLOW_ON);
		assert d.status.flags == 0; // means heater ON
		result = dc1.update(d);
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		//
		d = createDeviceWithStatus(1, 1, 0, FLOW_OFF); // heater OFF
		result = dc1.update(d);
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_ENDED, dc1.getStatus());
		//
	}

	@Test
	public void update_DevicePower() {
		// update(): demand power has changed
		assertEquals(0, dc1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), dc1.getApprovedPowerWatt());

		UpdateResult result = dc1.update(createDeviceWithStatus(1, 1, 10_000, FLOW_ON)); // device turned ON
		assertEquals(9_949, dc1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), dc1.getApprovedPowerWatt());
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_STARTED, dc1.getStatus());

		result = dc1.update(createDeviceWithStatus(1, 1, 0, FLOW_OFF)); // device turned OFF
		assertEquals(0, dc1.getDemandPowerWatt());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), dc1.getApprovedPowerWatt());
		assertEquals(UpdateResult.URGENT_UPDATES, result);
		assertEquals(CONSUMPTION_ENDED, dc1.getStatus());
	}

	@Test
	public void update_IntakeWaterTemperature() {
		// update(): intake water temperature has changed
		Device d = createDeviceWithStatus(1, 1, 10_000, FLOW_ON);
		assert d.status.tIn == 100; // 10°C
		// initialize DeviceManager:
		UpdateResult result = dc1.update(d);
		assertEquals(100, dc1.getIntakeWaterTemperatureUnits());
		assertEquals(UpdateResult.URGENT_UPDATES, result);

		// confirm CONSUMPTION_STARTED
		dc1.updateMaximumPowerConsumption(ElmStatus.ON, UNLIMITED_POWER);
		assertEquals(CONSUMPTION_APPROVED, dc1.getStatus());

		d.status.tIn = 110; // 11°C
		result = dc1.update(d);
		// minor difference ignored:
		assertEquals(100, dc1.getIntakeWaterTemperatureUnits());
		assertEquals(UpdateResult.NO_UPDATES, result);

		d.status.tIn = 125; // 12.5°C
		result = dc1.update(d);
		assertEquals(125, dc1.getIntakeWaterTemperatureUnits());
		// minor update while not consuming:
		assertEquals(UpdateResult.URGENT_UPDATES, result);

		d = createDeviceWithStatus(1, 1, 0, FLOW_OFF);
		dc1.update(d);
		// confirm CONSUMPTION_ENDED
		dc1.updateMaximumPowerConsumption(ElmStatus.ON, UNLIMITED_POWER);
		assertEquals(READY, dc1.getStatus());
		
		d.status.tIn = 100; // 10°C
		result = dc1.update(d);
		assertEquals(100, dc1.getIntakeWaterTemperatureUnits());
		// ignored while not consuming:
		assertEquals(UpdateResult.NO_UPDATES, result);
	}

	@Test
	public void updateUserFeedback() {
		final String id = dc1.getId();
		dc1.setStatus(READY);
		dc1.updateUserFeedback(ElmStatus.ON, 0);
		verify(hs, times(1)).dispatchElmUserFeedback(id, ElmStatus.ON, 0);
		//
		dc1.updateUserFeedback(ElmStatus.SATURATION, 0);
		verify(hs, times(1)).dispatchElmUserFeedback(id, ElmStatus.SATURATION, 0);
		//
		dc1.updateUserFeedback(ElmStatus.OVERLOAD, 0);
		verify(hs, times(1)).dispatchElmUserFeedback(id, ElmStatus.OVERLOAD, 0);

		dc1.setStatus(CONSUMPTION_STARTED);
		// schedulerStatus is irrelevant => device status is always ON:
		for (ElmStatus schedulerStatus : ElmStatus.values()) {
			dc1.updateUserFeedback(schedulerStatus, 0);
			// feedback is only dispatched after values changed => no changes after first iteration
			verify(hs, times(2)).dispatchElmUserFeedback(id, ElmStatus.ON, 0);
		}
		verifyNoMoreInteractions(hs);

		dc1.setStatus(CONSUMPTION_APPROVED);
		// schedulerStatus is irrelevant:
		for (ElmStatus schedulerStatus : ElmStatus.values()) {
			dc1.updateUserFeedback(schedulerStatus, 0);
			verify(hs, times(2)).dispatchElmUserFeedback(id, ElmStatus.ON, 0);
		}
		verifyNoMoreInteractions(hs);

		dc1.setStatus(CONSUMPTION_LIMITED);
		// schedulerStatus is irrelevant:
		for (ElmStatus schedulerStatus : ElmStatus.values()) {
			dc1.updateUserFeedback(schedulerStatus, 0);
			verify(hs, times(2)).dispatchElmUserFeedback(id, ElmStatus.SATURATION, 0);
		}
		verifyNoMoreInteractions(hs);

		dc1.setStatus(CONSUMPTION_DENIED);
		// schedulerStatus is irrelevant:
		for (ElmStatus schedulerStatus : ElmStatus.values()) {
			dc1.updateUserFeedback(schedulerStatus, 10_000);
			// called for the first time with (OVERLOAD, 10_000):
			verify(hs, times(1)).dispatchElmUserFeedback(id, ElmStatus.OVERLOAD, 10_000);
		}
		verifyNoMoreInteractions(hs);

		dc1.setStatus(ERROR);
		// schedulerStatus is irrelevant:
		for (ElmStatus schedulerStatus : ElmStatus.values()) {
			dc1.updateUserFeedback(schedulerStatus, 0);
			verify(hs, times(1)).dispatchElmUserFeedback(id, ElmStatus.ERROR, 0);
		}
		verifyNoMoreInteractions(hs);
	}

	@Test
	public void updatePowerConsumption_Unlimited() {
		// updateMaximumPowerConsumption():
		dc1.update(createDeviceWithStatus(1, 1, 10_000, FLOW_ON)); // turn ON
		assertEquals(CONSUMPTION_STARTED, dc1.getStatus());
		assertEquals(9_949, dc1.getDemandPowerWatt());
		verify(hs).dispatchElmUserFeedback(dc1.getId(), ElmStatus.ON, 0);
		//
		dc1.updateMaximumPowerConsumption(ElmStatus.ON, UNLIMITED_POWER);
		dc1.updateUserFeedback(ElmStatus.ON, 0);
		assertEquals(CONSUMPTION_APPROVED, dc1.getStatus());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), dc1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, dc1.getScaldProtectionTemperatureUnits());
		verify(hs, times(2)).dispatchElmUserFeedback(dc1.getId(), ElmStatus.ON, 0);
		//
		dc1.update(createDeviceWithStatus(1, 1, 0, FLOW_OFF)); // turn OFF
		assertEquals(CONSUMPTION_ENDED, dc1.getStatus());
		assertEquals(0, dc1.getDemandPowerWatt());
		//
		dc1.updateMaximumPowerConsumption(ElmStatus.ON, UNLIMITED_POWER);
		dc1.updateUserFeedback(ElmStatus.ON, 0);
		assertEquals(READY, dc1.getStatus());
		verify(hs, times(2)).dispatchElmUserFeedback(dc1.getId(), ElmStatus.ON, 0);

		verifyNoMoreInteractions(hs);
	}

	@Test
	public void updatePowerConsumption_Limited() {
		final Device d = createDeviceWithStatus(1, 1, 20_000, FLOW_ON);
		final short referenceTemperature = d.status.setpoint;
		dc1.update(d);
		assertEquals(CONSUMPTION_STARTED, dc1.getStatus());

		// APPROVED = 8_000 W
		dc1.updateMaximumPowerConsumption(ElmStatus.OVERLOAD, 8_000);
		dc1.updateUserFeedback(ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
		assertEquals(CONSUMPTION_LIMITED, dc1.getStatus());
		assertEquals(8_000, dc1.getApprovedPowerWatt());
		assertEquals(243, dc1.getScaldProtectionTemperatureUnits());
		verify(hs).putDeviceUpdate(Mockito.<RemoteDeviceUpdate> any());
		// next poll returns setpoint = scald-protection temperature:
		d.setSetpoint((short) 242);
		dc1.update(d);
		assertEquals(referenceTemperature, dc1.getUserDemandTemperatureUnits());

		// APPROVED = Unlimited
		dc1.updateMaximumPowerConsumption(ElmStatus.SATURATION, UNLIMITED_POWER);
		dc1.updateUserFeedback(ElmStatus.SATURATION, 0);
		assertEquals(CONSUMPTION_APPROVED, dc1.getStatus());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), dc1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, dc1.getScaldProtectionTemperatureUnits());
		verify(hs, times(2)).putDeviceUpdate(Mockito.<RemoteDeviceUpdate> any());
		// next poll returns NEARLY the restored setpoint temperature:
		short deviceSetpoint = (short) (referenceTemperature - 5);
		d.status.setpoint = deviceSetpoint;
		dc1.update(d);
		// small changes in temperature are ignored:
		assertEquals(referenceTemperature, dc1.getUserDemandTemperatureUnits());
		//
		deviceSetpoint = (short) (referenceTemperature - 25);
		d.status.setpoint = deviceSetpoint;
		dc1.update(d);
		// small changes in temperature are ignored:
		assertEquals(deviceSetpoint, dc1.getUserDemandTemperatureUnits());
	}

	@Test
	public void updatePowerConsumption_Denied() {
		final Device d = createDeviceWithStatus(1, 1, 20_000, FLOW_ON);
		final short referenceTemperature = d.status.setpoint;
		dc1.update(d);
		assertEquals(CONSUMPTION_STARTED, dc1.getStatus());
		//
		dc1.updateMaximumPowerConsumption(ElmStatus.OVERLOAD, NO_POWER);
		dc1.updateUserFeedback(ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
		assertEquals(CONSUMPTION_DENIED, dc1.getStatus());
		assertEquals(0, dc1.getApprovedPowerWatt());
		final short minScaldTemp = DeviceModel.SIM.getTemperatureOff();
		assertEquals(minScaldTemp, dc1.getScaldProtectionTemperatureUnits());
		verify(hs).putDeviceUpdate(Mockito.<RemoteDeviceUpdate> any());
		// next poll returns setpoint = scald-protection temperature:
		d.status.setpoint = minScaldTemp;
		d.setSetpoint(minScaldTemp);
		dc1.update(d);
		assertEquals(referenceTemperature, dc1.getUserDemandTemperatureUnits());

		// APPROVED = 8_000 W
		dc1.updateMaximumPowerConsumption(ElmStatus.OVERLOAD, 8_000);
		dc1.updateUserFeedback(ElmStatus.OVERLOAD, EXPECTED_WAITING_TIME);
		assertEquals(CONSUMPTION_LIMITED, dc1.getStatus());
		assertEquals(8_000, dc1.getApprovedPowerWatt());
		assertEquals(243, dc1.getScaldProtectionTemperatureUnits());
		verify(hs, times(2)).putDeviceUpdate(Mockito.<RemoteDeviceUpdate> any());
		// next poll returns setpoint = scald-protection temperature:
		d.setSetpoint((short) 243);
		dc1.update(d);
		assertEquals(referenceTemperature, dc1.getUserDemandTemperatureUnits());

		// APPROVED = Unlimited
		dc1.updateMaximumPowerConsumption(ElmStatus.SATURATION, UNLIMITED_POWER);
		dc1.updateUserFeedback(ElmStatus.SATURATION, 0);
		assertEquals(CONSUMPTION_APPROVED, dc1.getStatus());
		assertEquals(DeviceModel.SIM.getPowerMaxWatt(), dc1.getApprovedPowerWatt());
		assertEquals(UNDEFINED_TEMPERATURE, dc1.getScaldProtectionTemperatureUnits());
		verify(hs, times(3)).putDeviceUpdate(Mockito.<RemoteDeviceUpdate> any());
		d.setSetpoint((short) 380);
		dc1.update(d);
		// change in temperature is accepted:
		assertEquals(380, dc1.getUserDemandTemperatureUnits());
	}
}
