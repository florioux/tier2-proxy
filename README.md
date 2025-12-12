# Tier2 Proxy

> **Purpose**: The Tier 2 outbound proxy is a Simpl-Open middleware architecture component. It is designed to enable secure and compliant agent-to-agent communication from internal components that cannot be modified using the SIMPL HTTP client library.

---

## 📑 Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [⚡ Quick Start](#-quick-start)
    - [Run Locally](#run-locally)
4. [Installation guide](#installation-guide)
5. [User Guide](#user-guide)
6. [Testing](#testing)
7. [Contact & Support](#contact--support)
8. [License](#-license)

---

## Overview

The Tier 2 outbound proxy acts as a compliance and connectivity enabler within the SIMPL Open agent architecture. It bridges the gap between dataspace trust requirements and the practical realities of legacy or externally developed systems. By supporting passive interception and credential-aware forwarding, it provides a pathway for agent-to-agent communication even in constrained deployment contexts.

These components may include:

- Brownfield systems that are legacy or third-party and cannot be updated to use custom libraries
- Services implemented in incompatible programming languages or frameworks
- Applications using HTTP client stacks that do not support SIMPL’s extensions for dynamic credential selection or mTLS

By transparently routing their outbound traffic through the proxy, these components can participate in dataspace communications without needing to be rewritten.

Additionally, for each component, we will enable:

- automatic logging
- credential-based authentication (via mTLS negotiation with other SIMPL participants)

### Key Functions

The outbound proxy serves the following functions:

- Enable dataspace-compliant communication for non-adaptable or brownfield components
- Intercept and inspect outbound HTTP(S) traffic while preserving secure communication
- Facilitate dynamic mTLS authentication with other SIMPL agents using onboarded credentials
- Capture audit destination endpoints and protocols used
- Provide operational flexibility through support for standard proxy protocols (SOCKS 4a/5 and HTTP CONNECT)

### High-level overview

How the proxy interacts with any SIMPL is described in this diagram:

![diagram](docs/imgs/Tier 2 Outbound Proxy.png)

A SIMPL-Open agent can be configured to route outbound traffic from a specific internal component through the
outbound proxy by setting it as the component’s designated proxy. Once configured, all HTTP(S) communication
initiated by that component is transparently redirected to the proxy, enabling inspection, dynamic credential
injection, and compliance enforcement without requiring changes to the component itself.

---

## Prerequisites

The project is tested against the following toolchain versions. Using different major versions may lead to build issues.

| Tool       | Required / Tested Versions | Notes                                                        |
|------------|----------------------------|--------------------------------------------------------------|
| Java       | 21                         | ------                                                       |
| Maven      | 3.9+                       | Package manager for java dependencies.                       |
| Git        | 2.30.0+                    | Source control.                                              |
| Docker     | Latest stable              | For container builds via provided `Dockerfile`.              |
| Kubernetes | Latest stable              | For managing cluster and scalability/availability of cluster |
| Helm       | 3.19.0+                    | For Kubernetes reusable charts                               |


## ⚡ Quick Start

### Run Locally

Steps to quickly run the service locally for development or testing:

```bash
# Navigate to your project directory
cd <path_to_tier2-proxy>
# Build the project
mvn clean package
# Run the service
java -jar target/tier2-proxy-<version>.jar or generate a runner inside your IDE
# The proxy will be available on ports 3000, 3001, 3002 by default.
```

---

## Installation guide

For the user guide, please refer to the navigation below:

- [`deployment-guide`](documents/deployment-guide/README.md)
- [`installation-guide`](documents/installation-guide/README.md)
- [`upgrade-guide`](documents/upgrade-guide/README.md)

---

## User guide

For the user guide, please refer to:

- [`user-manual`](documents/user-manual/README.md)

---

## Testing

Steps to execute the test suite:

```bash
mvn test
```

You can also verify that the proxy is functioning correctly using curl. Below are example commands for both **HTTP(S)** and **SOCKS5** modes, covering various handshake scenarios.

First, get the CA certificate:
```shell
curl tier2-proxy.<your-namespace>.svc.cluster.local:3001/cert > ca.pem
```

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

---

## Contact & Support

- **Issue Tracker**: Please submit your issues and feature requests via the GitLab issue tracker of the repository.
- **Support**: cnect-simpl@ec.europa.eu

---

## 📝 License

This project is licensed under the European Union Public Licence (EUPL) version 1.2. Please see the [LICENSE](LICENSE) file for details.

---

📌 _This README is part of the **Simpl-Open** project documentation standards. Every component
repository should maintain an up-to-date README covering the sections above._
