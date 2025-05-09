package eu.europa.ec.simpl.tier2proxy.proxy.http;

import eu.europa.ec.simpl.tier2proxy.ServerConfig;

public record HttpProtocolServerOptions(ServerConfig serverConfig, int httpObjectAggregatorMaxContentLength) {}
