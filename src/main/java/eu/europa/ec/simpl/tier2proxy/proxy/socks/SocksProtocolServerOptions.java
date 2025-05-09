package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import eu.europa.ec.simpl.tier2proxy.ServerConfig;

public record SocksProtocolServerOptions(ServerConfig serverConfig, int httpObjectAggregatorMaxContentLength) {}
