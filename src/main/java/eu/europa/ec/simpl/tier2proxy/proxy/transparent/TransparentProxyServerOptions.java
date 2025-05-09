package eu.europa.ec.simpl.tier2proxy.proxy.transparent;

import eu.europa.ec.simpl.tier2proxy.ServerConfig;

public record TransparentProxyServerOptions(
		ServerConfig serverConfig,
		int httpObjectAggregatorMaxContentLength
) {
}
