package eu.europa.ec.simpl.tier2proxy.certificate;

import io.netty.handler.codec.http.HttpMethod;

public record CaEndpoint(String uri, HttpMethod method) {}
