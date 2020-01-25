# OpenNMS Azure IOT Plugin

The goal of this plugin is to model devices from Azure IOT hubs are nodes in OpenNMS.

## Build & install

Build and install the plugin into your local Maven repository using:
```
mvn clean install
```

From the OpenNMS Karaf shell:
```
feature:repo-add mvn:org.opennms.plugins.azure/iot-karaf-features/1.0.0-SNAPSHOT/xml
config:edit org.opennms.plugins.azure.iot
property-set connectionString HostName=xyz.azure-devices.net;SharedAccessKeyName=iothubowner;SharedAccessKey=xyz
config:update
feature:install opennms-plugins-azure-iot
```

Update automatically:
```
bundle:watch *
```

## TODO

* Support multiple IOT hubs
