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
import java.util.Objects;
import java.util.function.Consumer;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinDevice;
import com.microsoft.azure.sdk.iot.service.devicetwin.Query;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;

public class AZIOTClient {

    private final String connectionString;

    public AZIOTClient(String connectionString) {
        this.connectionString = Objects.requireNonNull(connectionString);
    }

    public void forEachDevice(Consumer<DeviceTwinDevice> consumer) throws IOException, IotHubException {
        DeviceTwin twinClient = DeviceTwin.createFromConnectionString(connectionString);
        // See https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-query-language for query ref.
        Query twinQuery = twinClient.queryTwin("SELECT * FROM devices", 10);
        while (twinClient.hasNextDeviceTwin(twinQuery)) {
            DeviceTwinDevice d = twinClient.getNextDeviceTwin(twinQuery);
            consumer.accept(d);
        }
    }
}
