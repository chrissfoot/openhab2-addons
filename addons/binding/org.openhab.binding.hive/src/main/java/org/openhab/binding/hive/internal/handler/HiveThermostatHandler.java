/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hive.internal.handler;

import javax.measure.quantity.Temperature;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.hive.internal.HiveBindingConstants;
import org.openhab.binding.hive.internal.classes.HiveAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chris Foot - Initial contribution
 */

public class HiveThermostatHandler extends BaseThingHandler {
    private Logger logger = LoggerFactory.getLogger(HiveThermostatHandler.class);
    protected int failureCount = 0;
    protected HiveBridgeHandler bridgeHandler;

    public HiveThermostatHandler(Thing thing) {
        super(thing);
    }

    protected void updateChannel() {
        HiveAttributes reading = getThermostatReading();

        if (reading == null) {
            failureCount++;
            if (failureCount > 2) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Unable to get the status of your thermostat, this may be a temporary problem with the HIVE api");
            }
            return;
        }

        Channel temperatureChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_CURRENT_TEMPERATURE);
        if (temperatureChannel != null) {
            updateState(temperatureChannel.getUID().getId(), new DecimalType(reading.temperature.reportedValue));
        }

        Channel targetChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_TARGET_TEMPERATURE);
        if (targetChannel != null) {
            updateState(targetChannel.getUID().getId(), new DecimalType(reading.targetHeatTemperature.reportedValue));
        }

        Channel heatingOnChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_HEATING_ON);
        if (heatingOnChannel != null) {
            updateState(heatingOnChannel.getUID().getId(), OnOffType.valueOf(reading.stateHeatingRelay.reportedValue));
        }

        Channel hotWaterOnChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_HOTWATER_ON);
        if (hotWaterOnChannel != null) {
            if (reading.stateHotWaterRelay != null) {
                updateState(hotWaterOnChannel.getUID().getId(),
                        OnOffType.valueOf(reading.stateHotWaterRelay.reportedValue));
            }
        }

        Channel thermostatBatteryChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_THERMOSTAT_BATTERY);
        if (thermostatBatteryChannel != null) {
            updateState(thermostatBatteryChannel.getUID().getId(),
                    DecimalType.valueOf(reading.batteryLevel.displayValue));
        }

        // Work out which mode we are in
        Boolean scheduleLock = Boolean.parseBoolean(reading.activeScheduleLock.displayValue);
        Boolean heatCoolMode = reading.activeHeatCoolMode.displayValue.equals("HEAT");
        Boolean boost = reading.activeHeatCoolMode.displayValue.equals("BOOST");

        Channel modeChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_MODE);
        if (modeChannel != null) {
            if (boost) {
                updateState(modeChannel.getUID().getId(), new StringType("Boost"));
            } else if (scheduleLock && heatCoolMode) {
                updateState(modeChannel.getUID().getId(), new StringType("Manual"));
            } else if (!scheduleLock && heatCoolMode) {
                updateState(modeChannel.getUID().getId(), new StringType("Schedule"));
            } else if (scheduleLock && !heatCoolMode) {
                updateState(modeChannel.getUID().getId(), new StringType("Off"));
            }
        }

        Channel boostRemainingChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_BOOST_REMAINING);
        if (boostRemainingChannel != null) {
            if (reading.activeOverrides != null && boost) {
                int expiryMinutes = new Integer(reading.scheduleLockDuration.displayValue);
                if (expiryMinutes > 0) {
                    int hours = expiryMinutes / 60;
                    int minutes = expiryMinutes % 60;
                    updateState(boostRemainingChannel.getUID().getId(),
                            new StringType(String.format("%d:%02d", hours, minutes)));
                } else {
                    updateState(boostRemainingChannel.getUID().getId(), new StringType("N/A"));
                }
            } else {
                updateState(boostRemainingChannel.getUID().getId(), new StringType("Not Boosting"));
            }
        }
    }

    public HiveAttributes getThermostatReading() {
        return bridgeHandler.getThermostatReading(this.thing);
    }

    protected HiveBridgeHandler getHiveBridgeHandler() {
        if (this.bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                logger.debug("Required bridge not defined for device {}.", this.getThing().getUID());
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof HiveBridgeHandler) {
                this.bridgeHandler = (HiveBridgeHandler) handler;
            } else {
                logger.debug("No available bridge handler found for device {} bridge {} .", this.getThing().getUID(),
                        bridge.getUID());
                return null;
            }
        }
        return this.bridgeHandler;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            getHiveBridgeHandler().requestRefresh();
            return;
        }
        switch (channelUID.getId()) {
            case HiveBindingConstants.CHANNEL_TARGET_TEMPERATURE: {
                if (command instanceof QuantityType) {
                    QuantityType<Temperature> temperature = (QuantityType<Temperature>) command;
                    getHiveBridgeHandler().setTargetTemperature(getThing().getUID(), temperature.floatValue());
                } else {
                    logger.error("CHANNEL_TARGET_TEMPERATURE channel only supports DecimalType");
                }
                break;
            }
            default:
                logger.error("Channel unknown {}", channelUID.getId());
        }
    }
}
