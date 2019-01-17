/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hive.internal;

import static org.openhab.binding.hive.internal.HiveBindingConstants.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.hive.internal.discovery.HiveDiscoveryService;
import org.openhab.binding.hive.internal.handler.HiveBridgeHandler;
import org.openhab.binding.hive.internal.handler.HiveThermostatHandler;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link HiveHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Chris Foot - Initial contribution
 */

@Component(configurationPid = "binding.hive", service = ThingHandlerFactory.class)
public class HiveHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.concat(HiveBindingConstants.BRIDGE_THING_TYPES_UIDS.stream(),
                    HiveBindingConstants.SUPPORTED_THING_TYPES_UIDS.stream()).collect(Collectors.toSet()));

    private final Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (BRIDGE_THING_TYPE.equals(thingTypeUID)) {
            HiveBridgeHandler handler = new HiveBridgeHandler((Bridge) thing);
            registerDiscoveryService(handler);
            return handler;
        } else if (THERMOSTAT_THING_TYPE.equals(thingTypeUID)) {
            return new HiveThermostatHandler(thing);
        }

        return null;
    }

    private synchronized void registerDiscoveryService(HiveBridgeHandler bridgeHandler) {
        HiveDiscoveryService hiveDiscoveryService = new HiveDiscoveryService(bridgeHandler);

        this.discoveryServiceRegs.put(bridgeHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), hiveDiscoveryService, new Hashtable<>()));
        bridgeHandler.hiveDiscoveryService = hiveDiscoveryService;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof HiveBridgeHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            serviceReg.unregister();
        }
    }
}
