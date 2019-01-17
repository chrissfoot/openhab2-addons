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

public class HiveNode {
    public String id;
    public String name;
    public HiveAttributes attributes;
    public String linkedNode;
    public String firmwareVersion;
    public String model;
    public String macAddress;

}
