/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hive.internal.classes;

/**
 *
 * @author Chris Foot - Initial contribution
 */

public class HiveAttributes {
    public HiveAttribute temperature;
    public HiveAttribute stateHeatingRelay;
    public HiveAttribute stateHotWaterRelay;
    public HiveAttribute targetHeatTemperature;
    public HiveAttribute nodeType;
    public HiveAttribute activeScheduleLock;
    public HiveAttribute activeHeatCoolMode;
    public HiveArrayAttribute activeOverrides;
    public HiveAttribute scheduleLockDuration;
    public HiveAttribute batteryLevel;
    public HiveAttribute softwareVersion;
    public HiveAttribute model;
    public HiveAttribute macAddress;
}
