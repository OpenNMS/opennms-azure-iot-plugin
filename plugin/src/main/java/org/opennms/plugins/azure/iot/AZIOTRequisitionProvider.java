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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;

import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisition;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisitionInterface;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisitionMetaData;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisitionNode;
import org.opennms.integration.api.v1.requisition.RequisitionProvider;
import org.opennms.integration.api.v1.requisition.RequisitionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinDevice;

public class AZIOTRequisitionProvider implements RequisitionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AZIOTRequisitionProvider.class);
    private static final String TYPE = "azure-iot";
    public static final String DEFAULT_FOREIGN_SOURCE = "AZ-IOT";
    public static final String METADATA_CONTEXT_ID = "AZ-IOT";

    public static final InetAddress NON_RESPONSIVE_IP_ADDRESS;
    static {
        try {
            // Addresses in the 192.0.2.0/24 block are reserved for documentation and should not respond to anything
            NON_RESPONSIVE_IP_ADDRESS = InetAddress.getByName("192.0.2.0");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private final AZIOTClient azIotClient;

    public AZIOTRequisitionProvider(AZIOTClient azIotClient) {
        this.azIotClient = Objects.requireNonNull(azIotClient);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public RequisitionRequest getRequest(Map<String, String> map) {
        return new AZIOTRequisitionRequest();
    }

    public static String toFID(DeviceTwinDevice d) {
        // Colons are typically used a separators, so we replace them to be safe
        return d.getDeviceId().replaceAll(":", "_");
    }

    @Override
    public Requisition getRequisition(RequisitionRequest requisitionRequest) {
        final ImmutableRequisition.Builder requisitionBuilder = ImmutableRequisition.newBuilder()
                .setForeignSource(DEFAULT_FOREIGN_SOURCE);
        try {
            azIotClient.forEachDevice(d -> {
                final ImmutableRequisitionNode.Builder nodeBuilder = ImmutableRequisitionNode.newBuilder()
                        .setForeignId(toFID(d))
                        .setNodeLabel(d.getDeviceId())
                        .addInterface(ImmutableRequisitionInterface.newBuilder()
                                .setIpAddress(NON_RESPONSIVE_IP_ADDRESS)
                                .addMonitoredService("AzureIOTDevice")
                                .build())
                        .addCategory("IOT")
                        // TODO: Derive these from fields in the twin
                        .addAsset("latitude", "45.340561")
                        .addAsset("longitude", "-75.910005")
                        .addMetaData(ImmutableRequisitionMetaData.newBuilder()
                                .setContext(METADATA_CONTEXT_ID)
                                .setKey("azUrl")
                                .setValue("")
                                .build());
                requisitionBuilder.addNode(nodeBuilder.build());
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to enumerate devices.", e);
        }
        return requisitionBuilder.build();
    }

    @Override
    public byte[] marshalRequest(RequisitionRequest requisitionRequest) {
        throw new UnsupportedOperationException("Minion not supported.");
    }

    @Override
    public RequisitionRequest unmarshalRequest(byte[] bytes) {
        throw new UnsupportedOperationException("Minion not supported.");
    }
}
