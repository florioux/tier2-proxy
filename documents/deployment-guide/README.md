# Deployment Guide

This guide covers deploying the Tier 2 Proxy using Docker and Kubernetes.

## Prerequisites

- A participant agent deployed and onboarded within a dataspace - [Documentation](https://code.europa.eu/simpl/simpl-open/development/iaa/documentation)
- Kubernetes Cluster (if deploying the proxy in a Kubernetes environment)
    - kubectl - command-line tool for interacting with your cluster. See: https://kubernetes.io/docs/reference/kubectl/kubectl/
    - Helm - Package manager for Kubernetes used to deploy charts. See: https://helm.sh/
- Docker - See: https://docs.docker.com/

## Running the Proxy on Docker
The Tier 2 Outbound Proxy can be easily launched as a Docker container. Below is a basic example using the docker run command:

```bash
docker run --rm -it \
  --name tier2-proxy \
  --network host \
  -v proxy-config/application.properties:/config/application \
  --env PROXY_CERTIFICATES_SERVER_PORT=3000 \
  --env PROXY_HTTP_SERVER_PORT=3001 \
  --env PROXY_SOCKS_SERVER_PORT=3002 \
  --env SIMPL_AUTHENTICATION_PROVIDER_BASEURL=http://localhost:8105 \
  tier2-proxy:latest
```

Explanation:

- `--network host`: Ensures that the proxy can bind to the necessary ports on the host machine.
- `-v /tmp/proxy/config:/config`: Mounts the configuration directory into the container.
- `PROXY_CERTIFICATES_SERVER_PORT`, `PROXY_HTTP_SERVER_PORT`, `PROXY_SOCKS_SERVER_PORT`: Allow customization of the listening ports.
- `SIMPL_AUTHENTICATION_PROVIDER_BASEURL`: This must point to the base URL of the local Authentication Provider microservice used for SIMPL identity management and credential operations.

> Ensure that the `SIMPL_AUTHENTICATION_PROVIDER_BASEURL` is reachable by the container, and that the referenced service is up and running before starting the proxy.

## Running the Proxy on Kubernetes

The Tier 2 Outbound Proxy can be easily launched as a pod in Kubernetes.

The [`values.yaml`](../charts/values.yaml) file is self-contained and provides all the necessary configuration for a standard deployment. 
However, you can customize any parameter according to your needs, by overriding values via another yaml (`-f other-values.yaml`).

This approach ensures a ready-to-use configuration, but remains flexible for different environments or specific requirements.
Below is a basic example using the provided Helm chart:

```bash
helm repo add tier2-proxy-charts https://code.europa.eu/api/v4/projects/1112/packages/helm/stable

helm install tier2-proxy tier2-proxy-charts/tier2-proxy \
--version <chart version> 
```

