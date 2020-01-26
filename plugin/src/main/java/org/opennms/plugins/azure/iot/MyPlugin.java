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

import java.io.File;
import java.io.IOException;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinDevice;
import com.microsoft.azure.sdk.iot.service.devicetwin.Query;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.rest.LogLevel;

/**
 * Must support multiple IOT hubs
 */
public class MyPlugin {

    public void syncState() {
        // Enumerate all of the nodes
        // Enumerate all of the alarms
        // Search for state changes - max, min
        // Is the device connected, or not
    }

    // Can we enumerate the IOT hubs, and identify which regions they are in?
    public void getIotHubData() throws IOException {
        Azure azure = Azure.configure()
                .withLogLevel(LogLevel.BASIC)
                .authenticate((File)null)
                .withDefaultSubscription();
        // There's no Azure IOT Hub Mgmt. SDK, so we'd need to use REST
        // See https://stackoverflow.com/questions/49142765/is-there-a-java-sdk-for-iot-hub-resource
        // See https://feedback.azure.com/forums/321918-azure-iot/suggestions/33564925-azure-iot-hub-management-sdk-for-java
    }

    public static void main(String[] args) throws IOException, IotHubException {
        DeviceTwin twinClient = DeviceTwin.createFromConnectionString("HostName=rnd-maas.azure-devices.net;SharedAccessKeyName=iothubowner;SharedAccessKey=VnQmg/+UGiAObDRH9pyQDFsYh0zatUhLjpcic2ChS1s=");
        // See https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-query-language for query ref.
        Query twinQuery = twinClient.queryTwin("SELECT * FROM devices", 10);
        while (twinClient.hasNextDeviceTwin(twinQuery)) {
            DeviceTwinDevice d = twinClient.getNextDeviceTwin(twinQuery);
            System.out.printf("Device with id: %s has connections state: %s\n", d.getDeviceId(), d.getConnectionState());
            // d.getConnectionState(); // connection state
            // d.getDeviceId(); // unique id

            // Desired property revisions metric
            // d.getDesiredProperties() // metric
        }

        // For polling, periodically issue queries to find devices with state changes since the last known poll
    }
}
