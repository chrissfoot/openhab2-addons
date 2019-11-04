/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.hive.internal.dto;

import org.eclipse.smarthome.core.library.types.DecimalType;

/**
 *
 * @author Chris Foot - Initial contribution
 */

public class HiveDeviceReading {
    public String deviceId;
    public String status;
    public DecimalType current;
    public DecimalType target;
    public String mode;
    public Boolean override;
    public Boolean overrideReadOnly;
    public long overrideRemaining;
    public int batteryLevel;
    public Boolean heating;
}
