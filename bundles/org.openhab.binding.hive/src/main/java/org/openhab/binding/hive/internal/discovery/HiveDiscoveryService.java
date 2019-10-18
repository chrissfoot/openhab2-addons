/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hive.internal.discovery;

import static org.openhab.binding.hive.internal.HiveBindingConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.hive.internal.HiveBindingConstants;
import org.openhab.binding.hive.internal.classes.HiveNode;
import org.openhab.binding.hive.internal.handler.HiveBridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 *
 * @author Chris Foot - Initial contribution
 */

public class HiveDiscoveryService extends AbstractDiscoveryService {

    protected ScheduledFuture<?> hiveDiscoveryJob;
    private final Logger logger = LoggerFactory.getLogger(HiveDiscoveryService.class);
    private HiveBridgeHandler hiveBridgeHandler;

    public HiveDiscoveryService(HiveBridgeHandler bridgeHandler) {
        super(ImmutableSet.of(new ThingTypeUID(HiveBindingConstants.BINDING_ID, "-")), 2, true);
        this.hiveBridgeHandler = bridgeHandler;
    }

    public void addDevice(HiveNode node) {
        if (node.attributes.nodeType.reportedValue.equals(THERMOSTAT_NODE_TYPE)) {
            ThingUID bridgeUID = hiveBridgeHandler.getThing().getUID();
            ThingUID thingUID = new ThingUID(THERMOSTAT_THING_TYPE, node.id);

            Map<String, Object> properties = new HashMap<>(1);
            properties.put("linkedDevice", node.linkedNode);
            properties.put(Thing.PROPERTY_VENDOR, "Hive Limited");
            properties.put(Thing.PROPERTY_FIRMWARE_VERSION, node.firmwareVersion);
            properties.put(Thing.PROPERTY_MAC_ADDRESS, node.macAddress);
            properties.put(Thing.PROPERTY_MODEL_ID, node.model);

            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                    .withThingType(THERMOSTAT_THING_TYPE).withBridge(bridgeUID).withLabel("Thermostat")
                    .withProperties(properties).withRepresentationProperty(node.id).build();
            thingDiscovered(discoveryResult);
        }
    }

    @Override
    protected void startScan() {
        // Only scan if there is a bridge
        if (hiveBridgeHandler != null) {
            hiveBridgeHandler.checkForDevices();
        }
    }

    @Override
    public void startBackgroundDiscovery() {
        logger.debug("Start Hive device background discovery");
        if (hiveDiscoveryJob == null || hiveDiscoveryJob.isCancelled()) {
            hiveDiscoveryJob = scheduler.scheduleWithFixedDelay(this::startScan, 0, 240, TimeUnit.SECONDS);
        }
    }

    @Override
    public void stopBackgroundDiscovery() {
        logger.debug("Stop Hive device background discovery");
        if (hiveDiscoveryJob != null && !hiveDiscoveryJob.isCancelled()) {
            hiveDiscoveryJob.cancel(true);
            hiveDiscoveryJob = null;
        }
    }

}
