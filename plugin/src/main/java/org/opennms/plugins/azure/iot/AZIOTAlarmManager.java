/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.plugins.azure.iot;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.opennms.integration.api.v1.collectors.CollectionSet;
import org.opennms.integration.api.v1.collectors.CollectionSetPersistenceService;
import org.opennms.integration.api.v1.collectors.immutables.ImmutableNumericAttribute;
import org.opennms.integration.api.v1.collectors.resource.NodeResource;
import org.opennms.integration.api.v1.collectors.resource.NumericAttribute;
import org.opennms.integration.api.v1.collectors.resource.immutables.ImmutableCollectionSet;
import org.opennms.integration.api.v1.collectors.resource.immutables.ImmutableCollectionSetResource;
import org.opennms.integration.api.v1.collectors.resource.immutables.ImmutableNodeResource;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.integration.api.v1.model.InMemoryEvent;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.immutables.ImmutableEventParameter;
import org.opennms.integration.api.v1.model.immutables.ImmutableInMemoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinDevice;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;

public class AZIOTAlarmManager {
    private static final Logger LOG = LoggerFactory.getLogger(AZIOTAlarmManager.class);

    private final NodeDao nodeDao;
    private final EventForwarder eventForwarder;
    private final CollectionSetPersistenceService collectionSetPersistenceService;

    private final AZIOTClient azIotClient;

    private Timer timer;

    public AZIOTAlarmManager(AZIOTClient azIotClient, NodeDao nodeDao, EventForwarder eventForwarder, CollectionSetPersistenceService collectionSetPersistenceService) {
        this.azIotClient = Objects.requireNonNull(azIotClient);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.eventForwarder = Objects.requireNonNull(eventForwarder);
        this.collectionSetPersistenceService = Objects.requireNonNull(collectionSetPersistenceService);
    }

    public void init() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(AZIOTAlarmManager.class.getName());
                try {
                    syncState();
                } catch (Exception e) {
                    LOG.error("Oops", e);
                }
            }
        }, 0, TimeUnit.SECONDS.toMillis(60));
    }

    public void destroy() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * TODO: We could implement this more efficiently by only fetching information on devices
     * that have changed since the last poll.
     *
     * If they haven't changed, they still have the same state, so we can then resend the existing state
     * if we want to keep the alarm alive.
     */
    public void syncState() throws IOException, IotHubException {
        LOG.info("Synchronizing state with devices in IOT hub...");
        azIotClient.forEachDevice(d -> {
            final String foreignId = AZIOTRequisitionProvider.toFID(d);
            final Node node = nodeDao.getNodeByForeignSourceAndForeignId(AZIOTRequisitionProvider.DEFAULT_FOREIGN_SOURCE, foreignId);
            if (node == null) {
                LOG.info("Did not node for FID: {}. Skipping. This can be expected to happen transiently but may be a problem if it persists.", foreignId);
                return;
            }

            // Update the connection state alarm
            sendDeviceConnectionStateUpdateEvent(node, d.getConnectionState());

            // Send some metrics
            final CollectionSet collectionSet = toCollectionSet(node, d);
            collectionSetPersistenceService.persist(node.getId(), getFirstInetAddress(node), collectionSet);

            d.getReportedPropertiesVersion();
            d.getDesiredPropertiesVersion();
        });
    }

    private void sendDeviceConnectionStateUpdateEvent(org.opennms.integration.api.v1.model.Node node, String connectionStateString) {
        DeviceConnectionState connectionState;
        if ("disconnected".equalsIgnoreCase(connectionStateString)) {
            connectionState = DeviceConnectionState.DISCONNECTED;
        } else if ("connected".equalsIgnoreCase(connectionStateString)) {
            connectionState = DeviceConnectionState.CONNECTED;
        } else {
            connectionState = DeviceConnectionState.UNNMAPPED;
        }

        final InMemoryEvent event = ImmutableInMemoryEvent.newBuilder()
                .setUei(EventConstants.DEVICE_CONNECTION_STATE)
                .setSource(EventConstants.SOURCE)
                .setNodeId(node.getId())
                .setSeverity(connectionState.getSeverity())
                .addParameter(ImmutableEventParameter.newInstance("connectionState", connectionStateString))
                .build();
        eventForwarder.sendAsync(event);
    }

    public CollectionSet toCollectionSet(Node node, DeviceTwinDevice d) {
        final ImmutableCollectionSet.Builder csetBuilder = ImmutableCollectionSet.newBuilder()
                .setStatus(CollectionSet.Status.SUCCEEDED);

        final NodeResource nodeResource = ImmutableNodeResource.newBuilder()
                .setNodeId(node.getId())
                .build();

        String groupName = "deviceTwinStats";
        ImmutableCollectionSetResource.Builder<NodeResource> nodeLevelResource = ImmutableCollectionSetResource.newBuilder(NodeResource.class)
               .setResource(nodeResource);
        if (d.getDesiredPropertiesVersion() != null) {
            nodeLevelResource.addNumericAttribute(counter("desiredPropVersion", groupName, d.getDesiredPropertiesVersion()));
        }
        if (d.getReportedPropertiesVersion() != null) {
            nodeLevelResource.addNumericAttribute(counter("reportedPropVersion", groupName, d.getReportedPropertiesVersion()));
        }
        csetBuilder.addCollectionSetResource(nodeLevelResource.build());

        return csetBuilder.build();
    }

    private NumericAttribute counter(String name, String group, Integer value) {
        return ImmutableNumericAttribute.newBuilder()
                .setName(name)
                .setGroup(group)
                .setType(NumericAttribute.Type.COUNTER)
                .setValue(value.doubleValue())
                .build();
    }

    private static InetAddress getFirstInetAddress(Node node) {
        return node.getIpInterfaces().get(0).getIpAddress();
    }

}
