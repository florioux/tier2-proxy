package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import eu.europa.ec.simpl.tier2proxy.ServerConfig;

@JsonNaming(value = PropertyNamingStrategies.KebabCaseStrategy.class)
public record SocksProtocolServerOptions(ServerConfig serverConfig, int httpObjectAggregatorMaxContentLength) {}
