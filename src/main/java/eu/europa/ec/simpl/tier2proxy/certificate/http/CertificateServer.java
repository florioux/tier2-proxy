package eu.europa.ec.simpl.tier2proxy.certificate.http;

import eu.europa.ec.simpl.tier2proxy.OsType;
import eu.europa.ec.simpl.tier2proxy.Server;
import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CertificateServer extends Server {
    private final EventLoopGroup workerGroup;
    private final ServerConfig serverConfig;
    private final ServerBootstrap server;

    public CertificateServer(
            OsType osType,
            EventLoopGroup bossGroup,
            CertificateServerOptions certificateServerOptions,
            X509Certificate caCertificate) {
        this.workerGroup = osType.eventLoopGroupSupplier();
        this.serverConfig = certificateServerOptions.serverConfig();

        this.server = osType.serverBootstrapSupplier(false)
                .group(bossGroup, this.workerGroup)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new CertificateServerHandlerInitializer(certificateServerOptions, caCertificate));
    }

    public String name() {
        return "certificate-ca-server";
    }

    public void stop(long quietPeriod, long timeout, TimeUnit unit) {
        this.workerGroup.shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Override
    protected ServerConfig config() {
        return this.serverConfig;
    }

    @Override
    protected ChannelFuture doStart() {
        return server.bind(serverConfig.bindAddr(), serverConfig.bindPort());
    }
}
