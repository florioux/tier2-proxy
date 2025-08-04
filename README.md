# Tier2 Proxy

## Project Description

The **Tier 2 Outbound Proxy** is a middleware component within the **SIMPL-Open architecture**, designed to enable **secure, compliant agent-to-agent communication** from internal systems that **cannot be modified** to use the SIMPL HTTP client library.

### When and Why to Use It

This proxy is specifically intended for integration with:

- **Legacy (“brownfield”) systems** or third-party services that cannot be updated
- Applications built in **languages or frameworks incompatible** with the SIMPL client library
- Components that rely on HTTP clients **without support for dynamic credential selection or mutual TLS (mTLS)**

By routing outbound traffic through this proxy, such components can **participate in dataspace communication** securely and transparently—**without requiring code changes**.

### What It Provides

For each connected component, the outbound proxy transparently enables:

- **Credential-based authentication** via mTLS handshake with other SIMPL agents
- **Automatic logging** of outbound requests, destinations, and security handshakes
- **Inspection and enforcement** of dataspace compliance policies
- **Support for standard proxy protocols**, including **SOCKS 4a/5** and **HTTP CONNECT**

This makes it possible to integrate non-adaptable systems into the SIMPL ecosystem while maintaining a strong security and compliance posture.

---

**For additional technical details and design considerations, refer to the full documentation:**  
[https://confluence.simplprogramme.eu/display/SIMPL/8070+-+Tier+2+Outbound+Proxy](https://confluence.simplprogramme.eu/display/SIMPL/8070+-+Tier+2+Outbound+Proxy)

## 🚀 Runtime Behavior and Usage Examples

Once started, the Tier 2 Outbound Proxy listens on **three ports**, each serving a distinct purpose:

| Service                   | Description                                                                 | Default Port |
|---------------------------|-----------------------------------------------------------------------------|--------------|
| **Certificate Server**    | HTTP server for downloading the internal CA certificate                     | `3000`       |
| **HTTP/HTTPS Proxy**      | Handles outbound traffic via HTTP or HTTPS                                  | `3001`       |
| **SOCKS Proxy (v5)**      | SOCKS proxy for TCP-based outbound traffic                                  | `3002`       |

These ports can be customized via the application’s configuration:

```properties
proxy.certificate.server.port=3000
proxy.http.server.port=3001
proxy.socks.server.port=3002
```

### Prerequisites

Depending on how the proxy is expected to run (locally, with docker or k8s), prerequisites may vary.

- A participant agent deployed and onboarded within a dataspace - [Documentation](https://code.europa.eu/simpl/simpl-open/development/iaa/documentation)
- Kubernetes Cluster (if deploying the proxy in a Kubernetes environment)
    - kubectl - command-line tool for interacting with your cluster. See: https://kubernetes.io/docs/reference/kubectl/kubectl/
    - Helm - Package manager for Kubernetes used to deploy charts. See: https://helm.sh/
- Docker - See: https://docs.docker.com/

### Running the Proxy on Docker
The Tier 2 Outbound Proxy can be easily launched as a Docker container. Below is a basic example using the docker run command:

```bash
docker run --rm -it
  --name tier2-proxy
  --network host
  -v proxy-config/application.properties:/config/application
  --env PROXY_CERTIFICATES_SERVER_PORT=3000
  --env PROXY_HTTP_SERVER_PORT=3001
  --env PROXY_SOCKS_SERVER_PORT=3002
  --env SIMPL_AUTHENTICATION_PROVIDER_BASEURL=http://localhost:8105
  tier2-proxy:latest
```

Explanation:

- `--network` host: Ensures that the proxy can bind to the necessary ports on the host machine.
- `-v /tmp/proxy/config:/config`: Mounts the configuration directory into the container.
- `PROXY_CERTIFICATES_SERVER_PORT`, `PROXY_HTTP_SERVER_PORT`, `PROXY_SOCKS_SERVER_PORT`: Allow customization of the listening ports.
- `SIMPL_AUTHENTICATION_PROVIDER_BASEURL`: This must point to the base URL of the local Authentication Provider microservice used for SIMPL identity management and credential operations.

> Ensure that the `SIMPL_AUTHENTICATION_PROVIDER_BASEURL` is reachable by the container, and that the referenced service is up and running before starting the proxy.

### Running the Proxy on Kubernetes
The Tier 2 Outbound Proxy can be easily launched as a pod in kubernetes. Below is a basic example using the provided helm chart:

```bash
helm repo add tier2-proxy-charts https://code.europa.eu/api/v4/projects/1112/packages/helm/stable

helm install tier2-proxy tier2-proxy-charts/tier2-proxy \
--version <chart version> -f values.yaml
```

#### Example `values.yaml`
```yaml
env:
  - name: PROXY_CERTIFICATES_SERVER_PORT
    value: "{{- .Values.server.certificates.port }}"
  - name: PROXY_HTTP_SERVER_PORT
    value: "{{- .Values.server.http.port }}"
  - name: PROXY_SOCKS_SERVER_PORT
    value: "{{- .Values.server.socks.port }}"
  - name: SIMPL_AUTHENTICATION_PROVIDER_BASEURL
    value: "http://authentication-provider.{{ .Release.Namespace }}.svc.cluster.local:8080"
```

### Getting the CA Certificate
To retrieve the internal CA certificate used by the proxy, you can use the following command:

```shell
curl tier2-proxy.<your-namespace>.svc.cluster.local:3001/cert > ca.pem
```

### Testing the Proxy
You can verify that the proxy is functioning correctly using curl. Below are example commands for both **HTTP(S)** and **SOCKS5** modes, covering various handshake scenarios.

#### Using HTTP/HTTPS Proxy (Port 3001)
```shell
# MTLS on endpoint not protected by the ephemeral proof check
HTTPS_PROXY=tier2-proxy.<your-namespace>.svc.cluster.local:3001 \
curl --cacert ca.pem -x "tier2-proxy.<your-namespace>.svc.cluster.local:3001" -v -i \
'https://<tier2-destination-host>/identityApi/v1/mtls/whoami'

# MTLS on endpoint protected by the ephemeral proof check
HTTPS_PROXY=tier2-proxy.<your-namespace>.svc.cluster.local:3001 \
curl --cacert ca.pem -x "tier2-proxy.<your-namespace>.svc.cluster.local:3001" -v -i \
'https://<tier2-destination-host>/sapApi/v1/mtls/identityAttributes'

# Standard TLS fallback
HTTPS_PROXY=tier2-proxy.<your-namespace>.svc.cluster.local:3001 \
curl --cacert ca.pem -x "tier2-proxy.<your-namespace>.svc.cluster.local:3001" -v -i \
'https://www.google.com'
```

#### Using SOCKS5 Proxy (Port 3002)
```shell
# MTLS on endpoint not protected by the ephemeral proof check
curl --cacert ca.pem --socks5-hostname tier2-proxy.<your-namespace>.svc.cluster.local:3002 \
'https://<tier2-destination-host>/identityApi/v1/mtls/whoami'

# MTLS on endpoint protected by the ephemeral proof check
curl --cacert ca.pem --socks5-hostname tier2-proxy.<your-namespace>.svc.cluster.local:3002 \
'https://<tier2-destination-host>/sapApi/v1/mtls/identityAttributes'

# Standard TLS fallback
curl --cacert ca.pem --socks5-hostname tier2-proxy.<your-namespace>.svc.cluster.local:3002 \
'https://www.google.com'
```

These test commands help validate:

- TLS and mTLS negotiation
- Certificate injection
- Transparent routing
- Protocol fallback behavior

### How to configure application to use the Proxy

This is an introduction to using proxies in development environments.
When using a proxy in Java, **JVM flags** can be used at the time of application startup.
For more details, refer to the [Java documentation - Networking Properties](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/net/doc-files/net-properties.html).
```cli
# HTTP usage 
java -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=3001 -jar myapp.jar

# For SOCKS proxy configurations:
java -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=3003 -jar myapp.jar
```

If your application runs inside a Docker container, you need to ensure that the JVM variables are properly passed.
Alternatively, if you plan to use Helm for deployment, you can optionally configure proxy settings within the chart values.

#### Example Dockerfile
```dockerfile
FROM eclipse-temurin:17-jdk
COPY . /app
WORKDIR /app
CMD ["java", "-Dhttp.proxyHost=proxy.example.com", "-Dhttp.proxyPort=3001", "-jar", "myapp.jar"]
```

In Python, proxy configuration can be done in code or via environment variables.

```python
# Install socks support if it necessary.
pip install PySocks

# Setup environment variable
export https_proxy=socks5://<hostname or ip>:<port>

# Run your script. This example makes request using proxy and shows IP-address:
# echo Your real IP
python -c 'import requests;print(requests.get("http://ipinfo.io/ip").text)'

# echo IP with socks-proxy
python -c 'import requests;print(requests.get("https://ipinfo.io/ip").text)'
```