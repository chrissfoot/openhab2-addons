/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hive.internal;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

import com.google.common.collect.Sets;

/**
 * The {@link HiveBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Chris Foot - Initial contribution
 */
@NonNullByDefault
public class HiveBindingConstants {

    public static final String BINDING_ID = "hive";

    public static final ThingTypeUID BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, "bridge");
    public static final ThingTypeUID THERMOSTAT_THING_TYPE = new ThingTypeUID(BINDING_ID, "thermostat");

    public static final Set<ThingTypeUID> BRIDGE_THING_TYPES_UIDS = Collections.singleton(BRIDGE_THING_TYPE);
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Sets.newHashSet(THERMOSTAT_THING_TYPE);

    // List of all Config Items
    public static final String CONFIG_USER_NAME = "USRNM";
    public static final String CONFIG_PASSWORD = "PASSWD";
    public static final String CONFIG_TOKEN = "TKN";

    // List of all node types
    public static final String RECEIVER_NODE_TYPE = "http://alertme.com/schema/json/node.class.thermostat.json#";
    public static final String THERMOSTAT_NODE_TYPE = "http://alertme.com/schema/json/node.class.thermostat.json#";

    // List of all channel ids
    public static final String CHANNEL_CURRENT_TEMPERATURE = "currenttemperature";
    public static final String CHANNEL_TARGET_TEMPERATURE = "targettemperature";
    public static final String CHANNEL_BOOST = "boost";
    public static final String CHANNEL_HEATING_ON = "heatingon";
    public static final String CHANNEL_HOTWATER_ON = "hotwateron";
    public static final String CHANNEL_MODE = "mode";
    public static final String CHANNEL_BOOST_REMAINING = "boostremaining";
    public static final String CHANNEL_THERMOSTAT_BATTERY = "thermostatbattery";

}
