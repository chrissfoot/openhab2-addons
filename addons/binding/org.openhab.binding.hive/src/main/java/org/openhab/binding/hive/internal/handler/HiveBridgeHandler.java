/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hive.internal.handler;

import static org.openhab.binding.hive.internal.HiveBindingConstants.*;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.hive.internal.classes.HiveAttributes;
import org.openhab.binding.hive.internal.classes.HiveLoginResponse;
import org.openhab.binding.hive.internal.classes.HiveNode;
import org.openhab.binding.hive.internal.classes.HiveNodes;
import org.openhab.binding.hive.internal.discovery.HiveDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link HiveBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Foot - Initial contribution
 */

public class HiveBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(HiveBridgeHandler.class);

    private static final int DISCOVER_TIMEOUT_SECONDS = 30;
    private String username;
    private String password;
    public String token = "";
    private static HttpClient client = new HttpClient(new SslContextFactory(true));
    private Boolean online = false;
    protected Gson gson = new Gson();
    public HiveDiscoveryService hiveDiscoveryService;
    protected ScheduledFuture<?> refreshJob;

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
        logger.info("Init Hive Home");
        if (getToken()) {
            updateStatus(ThingStatus.ONLINE);
            online = true;
            hiveDiscoveryService.startBackgroundDiscovery();
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
        try {
            for (Thing handler : getThing().getThings()) {
                ThingHandler thingHandler = handler.getHandler();
                if (thingHandler instanceof HiveThermostatHandler) {
                    HiveThermostatHandler thermostatHandler = (HiveThermostatHandler) thingHandler;
                    thermostatHandler.updateChannel();
                }
            }
        } catch (Exception e) {
            logger.debug("Error on channel update {}", e);
        }
    }

    @Override
    public void dispose() {
        hiveDiscoveryService.stopBackgroundDiscovery();
        refreshJob.cancel(true);
    }

    private boolean getToken() {
        Configuration configuration = getConfig();

        username = (String) configuration.get(CONFIG_USER_NAME);
        password = (String) configuration.get(CONFIG_PASSWORD);
        token = (String) configuration.get(CONFIG_TOKEN);

        try {
            if (!client.isStarted()) {
                client.start();
            }
            // Test the token to see if it's working, if it is, use it
            ContentResponse response;
            int statusCode = 0;

            if (token != null) {
                response = client.newRequest("https://api-prod.bgchprod.info:443/omnia/nodes").method(HttpMethod.GET)
                        .header("Accept", "application/vnd.alertme.zoo-6.1+json")
                        .header("Content-Type", "application/vnd.alertme.zoo-6.1+json")
                        .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                        .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();
                statusCode = response.getStatus();

                if (statusCode == HttpStatus.OK_200) {
                    // Token is still working, no need to get another
                    return true;
                }
            }

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
            request.header("Accept", "application/vnd.alertme.zoo-6.1+json");
            request.header("X-Omnia-Client", "Openhab 2");

            response = request.send();

            statusCode = response.getStatus();

            if (statusCode != HttpStatus.OK_200) {
                String statusLine = response.getStatus() + " " + response.getReason();
                logger.error("Method failed: {}", statusLine);
                return false;
            }

            Gson gson = new GsonBuilder().create();
            HiveLoginResponse o = gson.fromJson(response.getContentAsString(), HiveLoginResponse.class);
            if (o.sessions != null) {
                configuration.put(CONFIG_TOKEN, o.sessions.get(0).sessionId);
                token = o.sessions.get(0).sessionId;
                return true;
            }
            logger.error("No token response: {}", response.getContentAsString());
        } catch (Exception e) {
            logger.error("Exception in login method: {}", e);
            return false;
        }
        return false;
    }

    public void checkForDevices() {
        if (online) {
            ContentResponse response;
            try {
                response = client.newRequest("https://api-prod.bgchprod.info:443/omnia/nodes").method(HttpMethod.GET)
                        .header("Accept", "application/vnd.alertme.zoo-6.1+json")
                        .header("Content-Type", "application/vnd.alertme.zoo-6.1+json")
                        .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                        .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();
                int statusCode = response.getStatus();

                if (statusCode == HttpStatus.UNAUTHORIZED_401) {
                    // Token expired, get a new one and try again
                    getToken();
                    statusCode = response.getStatus();
                }

                if (statusCode != HttpStatus.OK_200) {
                    String statusLine = response.getStatus() + " " + response.getReason();
                    logger.error("Method failed: {}", statusLine);
                    return;
                }

                Gson gson = new GsonBuilder().create();
                HiveNodes o = gson.fromJson(response.getContentAsString(), HiveNodes.class);

                if (o.nodes.size() > 0) {
                    // Loop through the nodes and add thermostats but only if they are showing a temperature as the api
                    // reports lots of thermostats for some reason
                    HiveNode thermostat = null;
                    HiveNode systemInfo = null;
                    for (HiveNode node : o.nodes) {
                        if (node.attributes != null && node.attributes.nodeType != null
                                && node.attributes.nodeType.reportedValue.equals(THERMOSTAT_NODE_TYPE)
                                && node.attributes.temperature != null) {
                            // If this a thermostat, and it has a current temperature, add it to discovery results
                            thermostat = node;
                        }
                        if (node.attributes != null && node.attributes.batteryLevel != null) {
                            // If this a thermostat, and it has a current temperature, add it to discovery results
                            systemInfo = node;
                        }
                    }
                    if (thermostat != null) {
                        if (systemInfo != null) {
                            thermostat.linkedNode = systemInfo.id;
                            thermostat.firmwareVersion = systemInfo.attributes.softwareVersion.displayValue;
                            thermostat.model = systemInfo.attributes.model.displayValue;
                            thermostat.macAddress = systemInfo.attributes.macAddress.displayValue;
                        }
                        hiveDiscoveryService.addDevice(thermostat);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to add node to discovery results: {}", e);
                return;
            }
        }
    }

    public HiveAttributes getThermostatReading(Thing thing) {
        if (online) {
            ContentResponse response;
            try {
                // Get thermostat reading
                response = client.newRequest("https://api-prod.bgchprod.info:443/omnia/nodes/" + thing.getUID().getId())
                        .method(HttpMethod.GET).header("Accept", "application/vnd.alertme.zoo-6.1+json")
                        .header("Content-Type", "application/vnd.alertme.zoo-6.1+json")
                        .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                        .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();

                int statusCode = response.getStatus();

                if (statusCode == HttpStatus.UNAUTHORIZED_401) {
                    // Token expired, get a new one and try again
                    getToken();
                    statusCode = response.getStatus();
                }

                if (statusCode != HttpStatus.OK_200) {
                    // If it failed, log the error
                    String statusLine = response.getStatus() + " " + response.getReason();
                    logger.error("Method failed: {}", statusLine);
                    return null;
                }

                Gson gson = new GsonBuilder().create();
                String responseString = response.getContentAsString();
                HiveNodes o = gson.fromJson(responseString, HiveNodes.class);

                HiveAttributes reading = o.nodes.get(0).attributes;

                // Get battery level
                response = client
                        .newRequest("https://api-prod.bgchprod.info:443/omnia/nodes/"
                                + thing.getProperties().get("linkedDevice"))
                        .method(HttpMethod.GET).header("Accept", "application/vnd.alertme.zoo-6.1+json")
                        .header("Content-Type", "application/vnd.alertme.zoo-6.1+json")
                        .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                        .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();

                statusCode = response.getStatus();

                if (statusCode != HttpStatus.OK_200) {
                    // If it failed, log the error
                    String statusLine = response.getStatus() + " " + response.getReason();
                    logger.error("Method failed: {}", statusLine);
                    return null;
                }

                gson = new GsonBuilder().create();
                responseString = response.getContentAsString();
                o = gson.fromJson(responseString, HiveNodes.class);

                reading.batteryLevel = o.nodes.get(0).attributes.batteryLevel;

                return reading;
            } catch (Exception e) {
                logger.warn("Failed to get thermostat reading");
                return null;
            }
        }
        return null;
    }

    public void setTargetTemperature(ThingUID uid, float f) {
        if (online) {
            ContentResponse response;
            try {
                String setObject = "{\"nodes\": [{\"attributes\": {\"targetHeatTemperature\": {\"targetValue\": " + f
                        + "}}}]}";

                response = client.newRequest("https://api-prod.bgchprod.info:443/omnia/nodes/" + uid.getId())
                        .method(HttpMethod.PUT).header("Accept", "application/vnd.alertme.zoo-6.1+json")
                        .header("Content-Type", "application/vnd.alertme.zoo-6.1+json")
                        .header("X-Omnia-Client", "Openhab 2").header("X-Omnia-Access-Token", token)
                        .timeout(DISCOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .content(new StringContentProvider(setObject)).send();

                int statusCode = response.getStatus();

                if (statusCode == HttpStatus.UNAUTHORIZED_401) {
                    // Token expired, get a new one and try again
                    getToken();
                    statusCode = response.getStatus();
                }

                if (statusCode != HttpStatus.OK_200) {
                    // If it failed, log the error
                    String statusLine = response.getStatus() + " " + response.getReason();
                    logger.error("Method failed: {}", statusLine);
                }
            } catch (Exception e) {
                logger.error("Failed to update target temperature: {}", e);
            }
        }
    }
}
