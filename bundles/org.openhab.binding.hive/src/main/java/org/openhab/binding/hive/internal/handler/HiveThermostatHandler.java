/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 * <p>
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
    protected OnOffType boostCache = OnOffType.OFF;

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

        Channel boostOnChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_BOOST);
        if (boostOnChannel != null) {
            if (reading.activeHeatCoolMode.reportedValue.equals("BOOST")) {
                boostCache = OnOffType.ON;
                updateState(boostOnChannel.getUID().getId(), OnOffType.ON);
            } else {
                boostCache = OnOffType.OFF;
                updateState(boostOnChannel.getUID().getId(), OnOffType.OFF);
            }
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
                    updateState(boostRemainingChannel.getUID().getId(), new DecimalType(expiryMinutes));
                } else {
                    updateState(boostRemainingChannel.getUID().getId(), new DecimalType(0));
                }
            } else {
                updateState(boostRemainingChannel.getUID().getId(), new DecimalType(0));
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
            case HiveBindingConstants.CHANNEL_BOOST_REMAINING: {
                if (command instanceof DecimalType) {
                    if (boostCache == OnOffType.ON) {
                        DecimalType duration = (DecimalType) command;
                        getHiveBridgeHandler().boost(getThing().getUID(), OnOffType.ON, duration.intValue());
                    }
                } else {
                    logger.error("CHANNEL_BOOST_REMAINING only supports numbers.");
                }
                break;
            }
            case HiveBindingConstants.CHANNEL_BOOST: {
                if (command instanceof OnOffType) {
                    OnOffType boost = (OnOffType) command;
                    getHiveBridgeHandler().boost(getThing().getUID(), boost, 30);
                    boostCache = boost;
                    Channel boostOnChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_BOOST);
                    if (boostOnChannel != null) {
                        updateState(boostOnChannel.getUID().getId(), boost);
                    }
                    Channel boostRemainingChannel = getThing().getChannel(HiveBindingConstants.CHANNEL_BOOST_REMAINING);
                    if (boostRemainingChannel != null) {
                        if (boost == OnOffType.ON) {
                            updateState(boostRemainingChannel.getUID().getId(), new DecimalType(30));
                        } else {
                            updateState(boostRemainingChannel.getUID().getId(), new DecimalType(0));
                        }
                    }
                } else {
                    logger.error("CHANNEL_BOOST only supports switch type.");
                }
                break;
            }
            default:
                logger.error("Channel unknown {}", channelUID.getId());
        }

    }
}
