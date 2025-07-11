package eu.europa.ec.simpl.tier2proxy.proxy.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.OsType;
import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpProtocolProxyServerTest {
    @Mock
    OsType osType;

    @Mock
    EventLoopGroup bossGroup;

    @Mock
    EventLoopGroup workerGroup;

    @Mock
    HttpProtocolServerOptions httpProtocolServerOptions;

    @Mock
    Certificates certificates;

    @Mock
    ServerConfig serverConfig;

    @Mock
    ServerBootstrap serverBootstrap;

    @Mock
    ChannelFuture channelFuture;

    HttpProtocolProxyServer httpProtocolProxyServer;

    @BeforeEach
    void setUp() {
        when(osType.eventLoopGroupSupplier()).thenReturn(workerGroup);
        when(httpProtocolServerOptions.serverConfig()).thenReturn(serverConfig);
        when(osType.serverBootstrapSupplier(false)).thenReturn(serverBootstrap);
        when(serverBootstrap.group(bossGroup, workerGroup)).thenReturn(serverBootstrap);
        when(serverBootstrap.handler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childHandler(any())).thenReturn(serverBootstrap);

        httpProtocolProxyServer =
                new HttpProtocolProxyServer(osType, bossGroup, httpProtocolServerOptions, certificates);
    }

    @Test
    void testNameReturnsCorrectValue() {
        var result = httpProtocolProxyServer.name();
        assertEquals("http-protocol-proxy-server", result);
    }

    @Test
    void testConfigReturnsServerConfig() {
        var result = httpProtocolProxyServer.config();
        assertEquals(serverConfig, result);
    }

    @Test
    void testStopShutsDownWorkerGroup() {
        var quietPeriod = 1L;
        var timeout = 2L;
        var unit = TimeUnit.SECONDS;
        httpProtocolProxyServer.stop(quietPeriod, timeout, unit);
        verify(workerGroup).shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Test
    void testDoStartBindsServer() {
        var bindAddr = "localhost";
        var bindPort = 8080;
        when(serverConfig.bindAddr()).thenReturn(bindAddr);
        when(serverConfig.bindPort()).thenReturn(bindPort);
        when(serverBootstrap.bind(bindAddr, bindPort)).thenReturn(channelFuture);
        var result = httpProtocolProxyServer.doStart();
        assertEquals(channelFuture, result);
    }
}
