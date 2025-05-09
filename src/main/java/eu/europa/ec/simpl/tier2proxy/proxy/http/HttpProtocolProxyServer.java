package eu.europa.ec.simpl.tier2proxy.proxy.http;

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
public final class HttpProtocolProxyServer extends Server {
    private final EventLoopGroup workerGroup;
    private final ServerConfig serverConfig;
    private final ServerBootstrap server;

    public HttpProtocolProxyServer(
            OsType osType,
            EventLoopGroup bossGroup,
            HttpProtocolServerOptions httpProtocolServerOptions,
            Certificates certificates) {
        this.workerGroup = osType.eventLoopGroupSupplier();
        this.serverConfig = httpProtocolServerOptions.serverConfig();

        this.server = osType.serverBootstrapSupplier(false)
                .group(bossGroup, workerGroup)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new HttpProtocolProxyServerInitializer(httpProtocolServerOptions, certificates));
    }

    public String name() {
        return "http-protocol-proxy-server";
    }

    public void stop(long quietPeriod, long timeout, TimeUnit unit) {
        this.workerGroup.shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Override
    protected ServerConfig config() {
        return serverConfig;
    }

    @Override
    protected ChannelFuture doStart() {
        return server.bind(serverConfig.bindAddr(), serverConfig.bindPort());
    }
}
