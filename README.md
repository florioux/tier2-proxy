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

## Testing the Proxy
You can verify that the proxy is functioning correctly using curl. Below are example commands for both **HTTP(S)** and **SOCKS5** modes, covering various handshake scenarios.

### Using HTTP/HTTPS Proxy (Port 3001)

```properties
# MTLS with preflight check
HTTPS_PROXY=tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3001 \
curl -k -x "tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3001" -v -i \
'https://t2.iaa-dsstaging-authority.dev.simpl-europe.eu/identityApi/v1/mtls/whoami'

# MTLS direct
HTTPS_PROXY=tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3001 \
curl -k -x "tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3001" -v -i \
'https://t2.iaa-dsstaging-authority.dev.simpl-europe.eu/sapApi/v1/mtls/identityAttributes'

# Standard TLS fallback
HTTPS_PROXY=tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3001 \
curl -k -x "tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3001" -v -i \
'https://www.google.com'

```

### Using SOCKS5 Proxy (Port 3002)

```properties
# MTLS with preflight check
curl -k --socks5-hostname tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3002 \
'https://t2.iaa-dsstaging-authority.dev.simpl-europe.eu/identityApi/v1/mtls/whoami'

# MTLS direct
curl -k --socks5-hostname tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3002 \
'https://t2.iaa-dsstaging-authority.dev.simpl-europe.eu/sapApi/v1/mtls/identityAttributes'

# Standard TLS fallback
curl -k --socks5-hostname tier2-proxy.iaa-dsstaging-consumer.svc.cluster.local:3002 \
'https://www.google.com'

```

These test commands help validate:

- TLS and mTLS negotiation
- Certificate injection
- Transparent routing
- Protocol fallback behavior

## Running the Proxy
The Tier 2 Outbound Proxy can be easily launched as a Docker container. Below is a basic example using the docker run command:

```bash
docker run --rm -it
  --name tier2-proxy
  --network host
  -v /tmp/proxy/config:/config
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
