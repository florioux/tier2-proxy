package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import eu.europa.ec.simpl.tier2proxy.OsType;
import eu.europa.ec.simpl.tier2proxy.Server;
import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SocksProtocolProxyServer extends Server {
    private final EventLoopGroup workerGroup;
    private final ServerConfig serverConfig;

    private final ServerBootstrap server;

    public SocksProtocolProxyServer(
            OsType osType,
            EventLoopGroup bossGroup,
            SocksProtocolServerOptions socksProtocolServerOptions,
            Certificates certificates) {
        this.workerGroup = osType.eventLoopGroupSupplier();
        this.serverConfig = socksProtocolServerOptions.serverConfig();

        this.server = osType.serverBootstrapSupplier(false)
                .group(bossGroup, workerGroup)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new SocksProtocolProxyServerInitializer(certificates, socksProtocolServerOptions));
    }

    public String name() {
        return "socks-protocol-proxy-server";
    }

    public void stop(long quietPeriod, long timeout, TimeUnit unit) {
        this.workerGroup.shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Override
    protected ChannelFuture doStart() {
        return server.bind(serverConfig.bindAddr(), serverConfig.bindPort());
    }

    @Override
    protected ServerConfig config() {
        return serverConfig;
    }
}
