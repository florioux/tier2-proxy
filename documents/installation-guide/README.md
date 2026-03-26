# Installation Guide

This guide provides instructions on how to install and run the Tier 2 Proxy.

## Building from Source

To build the proxy from source, you will need Java 21 and Maven 3.9+.

```bash
# Clone the repository
git clone <repository_url>
cd tier2-proxy
# Build the project
mvn clean package
```

The built artifact will be in the `target` directory. You can run it using:
```bash
java -jar target/tier2-proxy-<version>.jar
```

## Getting the CA Certificate
To retrieve the internal CA certificate used by the proxy, you can use the following command. Make sure to adapt the service name and namespace to your environment.

```shell
curl tier2-proxy.<your-namespace>.svc.cluster.local:3000/cert > ca.pem
```
This certificate needs to be trusted by clients that will use the proxy.
