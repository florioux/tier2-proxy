package eu.europa.ec.simpl.tier2proxy.certificate.http;

import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import io.netty.handler.codec.http.HttpMethod;

public record CertificateServerOptions(
        ServerConfig serverConfig,
        int httpObjectAggregatorMaxContentLength,
        HttpMethod caServingEndpointMethod,
        String caServingEndpointUri) {}
