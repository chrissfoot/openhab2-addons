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
package org.openhab.binding.hive.internal.handler;

import static org.openhab.binding.hive.internal.HiveBindingConstants.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.hive.internal.discovery.HiveDiscoveryService;
import org.openhab.binding.hive.internal.dto.HiveCurrentStatus;
import org.openhab.binding.hive.internal.dto.HiveDeviceReading;
import org.openhab.binding.hive.internal.dto.HiveLoginResponse;
import org.openhab.binding.hive.internal.dto.HiveNode;
import org.openhab.binding.hive.internal.dto.HiveNodes;
import org.openhab.binding.hive.internal.dto.HiveTRV;
import org.openhab.binding.hive.internal.dto.HiveThermostat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link HiveBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Foot - Initial contribution
 */
@NonNullByDefault
public class HiveBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(HiveBridgeHandler.class);

    private static final int DISCOVER_TIMEOUT_SECONDS = 30;
    private String username = "";
    private String password = "";
    public String token = "";
    private static HttpClient client = new HttpClient(new SslContextFactory(true));
    private Boolean online = false;
    protected Gson gson = new Gson();
    protected HiveCurrentStatus lastStatus = new HiveCurrentStatus();
    protected Date lastStatusFetched = new Date(0);
    protected Boolean gettingStatus = false;

    @Nullable public HiveDiscoveryService hiveDiscoveryService;
    @Nullable protected ScheduledFuture<?> refreshJob;

    public HiveBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // There is nothing to handle in the bridge handler
    }

    @Override
    public void initialize() {
        // See if our login works
        if (getToken()) {
            updateStatus(ThingStatus.ONLINE);
            online = true;
            if (hiveDiscoveryService != null) {
                hiveDiscoveryService.startBackgroundDiscovery();
            }
            checkForDevices();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Unable to login, please check your username/password");
        }
    }

    public void requestRefresh() {
        startAutomaticRefresh();
    }

    private void startAutomaticRefresh() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
        refreshJob = scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                updateChannels();
            }
        }, 5, 30, TimeUnit.SECONDS);
    }

    private void updateChannels() {
        if (getThing().getThings().isEmpty()) {
            return;
        }
        for (Thing handler : getThing().getThings()) {
            ThingHandler thingHandler = handler.getHandler();
            if (thingHandler instanceof HiveThermostatHandler) {
                HiveThermostatHandler thermostatHandler = (HiveThermostatHandler) thingHandler;
                thermostatHandler.updateChannel();
            }
        }
    }

    @Override
    public void dispose() {
        if (hiveDiscoveryService != null) {
            hiveDiscoveryService.stopBackgroundDiscovery();
        }
        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
    }

    private boolean getToken() {
        Configuration configuration = getConfig();

        username = (String) configuration.get(CONFIG_USER_NAME);
        password = (String) configuration.get(CONFIG_PASSWORD);
        token = (String) configuration.get(CONFIG_TOKEN);

        if (!client.isStarted()) {
            try {
                client.start();
            } catch (Exception e) {
                logger.warn("Unable to start httpclient: {}", e.getMessage());
                return false;
            }
        }
        // Test the token to see if it's working, if it is, use it
        ContentResponse response;
        int statusCode = 0;

        JsonObject sessionObject = new JsonObject();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("caller", "WEB");
        jsonObject.addProperty("username", username);
        jsonObject.addProperty("password", password);
        JsonArray array = new JsonArray();
        array.add(jsonObject);
        sessionObject.add("sessions", array);

        Request request = client.POST("https://api-prod.bgchprod.info:443/omnia/auth/sessions");
        request.content(new StringContentProvider(gson.toJson(sessionObject)), "application/json");
        request.timeout(5000, TimeUnit.MILLISECONDS);
        request.header("Accept", "application/vnd.alertme.zoo-6.5+json");
        request.header("X-Omnia-Client", "Openhab 2");

        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Unable to communicate with Hive API: {}", e.getMessage());
            return false;
        }

        statusCode = response.getStatus();

        if (statusCode != HttpStatus.OK_200) {
            String statusLine = response.getStatus() + " " + response.getReason();
            logger.warn("Error communicating with Hive API: {}", statusLine);
            return false;
        }

        HiveLoginResponse o = gson.fromJson(response.getContentAsString(), HiveLoginResponse.class);
        if (o.sessions != null) {
            configuration.put(CONFIG_TOKEN, o.sessions.get(0).sessionId);
            token = o.sessions.get(0).sessionId;
            return true;
        }
        logger.warn("Hive API did not provide token: {}", response.getContentAsString());

        return false;
    }

    public void checkForDevices() {
        if (online) {
            ContentResponse response;
            try {
                response = client.newRequest("https://api-prod.bgchprod.info:443/omnia/nodes").method(HttpMethod.GET)
                        .header("Accept", "application/vnd.alertme.zoo-6.5+json")
                        .header("Content-Type", "application/vnd.alertme.zoo-6.5+json")
                        .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                        .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                if (e.getMessage().contains("WWW-Authenticate header")) {
                    getToken();
                } else {
                    logger.warn("Failed to get new token from Hive API: {}", e.getMessage());
                }
                return;
            }
            int statusCode = response.getStatus();

            if (statusCode == HttpStatus.UNAUTHORIZED_401) {
                // Token expired, get a new one and try again
                getToken();
                statusCode = response.getStatus();
            }

            if (statusCode != HttpStatus.OK_200) {
                String statusLine = response.getStatus() + " " + response.getReason();
                logger.warn("Error while reading from Hive API: {}", statusLine);
                return;
            }

            HiveNodes o = gson.fromJson(response.getContentAsString(), HiveNodes.class);

            if (o.nodes.size() > 0) {
                // Loop through the nodes and add thermostats
                // There are many types including: heating, hot water, UI and TRV
                ArrayList<HiveNode> heatingNodes = new ArrayList<HiveNode>();
                ArrayList<HiveNode> hotwaterNodes = new ArrayList<HiveNode>();
                ArrayList<HiveNode> thermostatUINodes = new ArrayList<HiveNode>();

                // TODO: Find out what happens if the user has more than one thermostat! Specifically, how are they
                // linked

                HiveThermostat thermostat = new HiveThermostat();
                for (HiveNode node : o.nodes) {
                    if (node.nodeType.equals(THERMOSTAT_NODE_TYPE)
                            && node.features.get("device_management_v1").get("productType").reportedValue
                                    .equals("HEATING")) {
                        // This is a heating node
                        heatingNodes.add(node);
                    } else if (node.nodeType.equals(THERMOSTAT_NODE_TYPE)
                            && node.features.get("device_management_v1").get("productType").reportedValue
                                    .equals("HOT_WATER")) {
                        // Check that this is the hot water node
                        hotwaterNodes.add(node);
                    } else if (node.nodeType.equals(THERMOSTATUI_NODE_TYPE)) {
                        // This is the thermostat UI node
                        thermostatUINodes.add(node);
                    } else if (node.nodeType.equals(TRV_NODE_TYPE)) {
                        HiveTRV trv = new HiveTRV();
                        trv.id = node.id;
                        trv.name = node.name;
                        if (hiveDiscoveryService != null) {
                            hiveDiscoveryService.addTRV(trv);
                        }
                    }
                }
                if (thermostatUINodes.size() > 0) {
                    thermostat.uiId = thermostatUINodes.get(0).id;

                    if (heatingNodes.size() > 0) {
                        thermostat.heatingId = heatingNodes.get(0).id;
                    }
                    if (hotwaterNodes.size() > 0) {
                        thermostat.hotwaterId = hotwaterNodes.get(0).id;
                    }

                    if (hiveDiscoveryService != null) {
                        hiveDiscoveryService.addThermostat(thermostat);
                    }
                }
            }
        }
    }

    public HiveCurrentStatus getReading() {
        return getReading(false);
    }

    public HiveCurrentStatus getReading(Boolean ignoreCached) {
        HiveNodes o = null;
        HiveCurrentStatus status = new HiveCurrentStatus();
        status.readings = new ArrayList<HiveDeviceReading>();

        // Check if we are already going to the API, if so, wait for the result of the previous call
        if (gettingStatus) {
            int timeout = 0;
            while (gettingStatus && timeout < 100) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.warn("Get Reading Thread Interrupted");
                    return status;
                }
                timeout++;
            }
            if (timeout >= 100) {
                return status;
            }
            return lastStatus;
        }

        // Check for cached api call so that we don't overwhelm the api
        // This method could easily be called 10 times in a second if the user have 10 trvs for instance
        // So we cache the last fetched status to avoid all those extra api calls
        // Date FiveMinutesAgo = new Date(new Date().getTime() - 300000);
        // if (lastStatusFetched.after(FiveMinutesAgo) && !ignoreCached) {
        // return lastStatus;
        // }

        gettingStatus = true;

        if (online) {
            ContentResponse response;
            int statusCode = 0;
            String responseString = "";

            // Get all nodes
            try {
                response = client.newRequest("https://api-prod.bgchprod.info:443/omnia/nodes").method(HttpMethod.GET)
                        .header("Accept", "application/vnd.alertme.zoo-6.5+json")
                        .header("Content-Type", "application/vnd.alertme.zoo-6.5+json")
                        .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                        .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();

                statusCode = response.getStatus();

                if (statusCode == HttpStatus.UNAUTHORIZED_401) {
                    // Token expired, get a new one and try again
                    getToken();
                    statusCode = response.getStatus();
                }

                if (statusCode != HttpStatus.OK_200) {
                    // If it failed, log the error
                    String statusLine = response.getStatus() + " " + response.getReason();
                    logger.warn("Error while reading from Hive API: {}", statusLine);
                    status.isValid = false;
                    return status;
                }

                responseString = response.getContentAsString();
                o = gson.fromJson(responseString, HiveNodes.class);
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                logger.warn("Failed to get nodes: {}", e.getMessage());
                status.isValid = false;
                return status;
            }
            status.isValid = true;
        }

        // Loop through the nodes, adding the status details for each one as we go
        if (o.nodes.size() > 0) {

            for (HiveNode node : o.nodes) {
                HiveDeviceReading reading = new HiveDeviceReading();
                reading.deviceId = node.id;

                if (node.nodeType.equals(THERMOSTAT_NODE_TYPE)
                        && node.features.get("device_management_v1").get("productType").reportedValue
                                .equals("HEATING")) {
                    // This is a heating node
                    reading.current = new DecimalType(
                            node.features.get("temperature_sensor_v1").get("temperature").reportedValue.toString());
                    reading.target = new DecimalType(
                            node.features.get("heating_thermostat_v1").get("targetHeatTemperature").reportedValue
                                    .toString());
                    reading.heating = (node.features.get("heating_thermostat_v1")
                            .get("operatingState").reportedValue == "HEAT");
                    reading.override = node.features.get("heating_thermostat_v1")
                            .get("temporaryOperatingModeOverride").reportedValue.equals("TRANSIENT");

                    if (reading.override) {
                        if (node.features.get("heating_thermostat_v1").get("operatingStateReason").reportedValue
                                .equals("BM_INTERLOCK")) {
                            reading.status = "TRV Calling For Heat";
                            reading.overrideReadOnly = true;
                        } else {
                            reading.status = "Boost";
                            LocalDateTime timeNow = LocalDateTime.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                            LocalDateTime boostExpires = LocalDateTime.parse(
                                    node.features.get("transient_mode_v1").get("endDatetime").reportedValue.toString(),
                                    formatter);
                            long minutesLeft = ChronoUnit.MINUTES.between(timeNow, boostExpires);
                            reading.overrideRemaining = minutesLeft;
                            reading.overrideReadOnly = false;
                        }
                    } else {
                        reading.status = node.features.get("heating_thermostat_v1").get("operatingMode").reportedValue
                                .toString();
                    }

                    status.readings.add(reading);

                } else if (node.nodeType.equals(THERMOSTAT_NODE_TYPE)
                        && node.features.get("device_management_v1").get("productType").reportedValue
                                .equals("HOT_WATER")) {
                    // This is a hot water node

                } else if (node.nodeType.equals(THERMOSTATUI_NODE_TYPE)) {
                    // This is a thermostat UI node

                } else if (node.nodeType.equals(TRV_NODE_TYPE)) {

                }
            }
        }

        lastStatus = status;
        lastStatusFetched = new Date();
        gettingStatus = false;
        return status;
    }

    private void sendCommandToAPI(String setObject, ThingUID uid, String type) {
        try {
            ContentResponse response;
            response = client.newRequest("https://api-prod.bgchprod.info:443/omnia/nodes/" + uid.getId())
                    .method(HttpMethod.PUT).header("Accept", "application/vnd.alertme.zoo-6.5+json")
                    .header("Content-Type", "application/vnd.alertme.zoo-6.5+json")
                    .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                    .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS).content(new StringContentProvider(setObject))
                    .send();

            int statusCode = response.getStatus();

            if (statusCode == HttpStatus.UNAUTHORIZED_401) {
                // Token expired, get a new one and try again
                getToken();
                statusCode = response.getStatus();
            }

            if (statusCode != HttpStatus.OK_200) {
                // If it failed, log the error
                String statusLine = response.getStatus() + " " + response.getReason();
                logger.warn("Error while reading from Hive API: {}", statusLine);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Failed to update {}: {}", type, e.getMessage());
        }
    }

    public void boost(ThingUID uid, OnOffType boost, int duration) {
        if (online) {
            // if (boost == OnOffType.ON) {
            // String setObject = "{\"nodes\": [{\"attributes\": {\"activeHeatCoolMode\": {\"targetValue\": \"BOOST\"},"
            // + "\"scheduleLockDuration\": {\"targetValue\": " + duration + "}}}]}";
            // callClient(setObject, uid, "boost");
            // } else if (boost == OnOffType.OFF) {
            // String setObject = "{\"nodes\": [{\"attributes\": {\"activeHeatCoolMode\": {\"targetValue\": \"HEAT\"}, "
            // + "\"activeScheduleLock\": {\"targetValue\": \"True\"}}}]}";
            // callClient(setObject, uid, "boost");
            // }
        }
    }

    public void setTargetTemperature(ThingUID uid, float f) {
        if (online) {
            String setObject = "{\"nodes\": [{\"features\": {\"heating_thermostat_v1\" : {\"targetHeatTemperature\": {\"targetValue\": "
                    + f + "}}}}]}";
            sendCommandToAPI(setObject, uid, "target temperature");
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(HiveDiscoveryService.class);
    }

    public void setDiscoveryService(HiveDiscoveryService thingDiscoveryService) {
        this.hiveDiscoveryService = thingDiscoveryService;
    }
}
