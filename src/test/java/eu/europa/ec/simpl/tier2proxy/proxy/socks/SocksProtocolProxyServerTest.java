package eu.europa.ec.simpl.tier2proxy.proxy.socks;

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
class SocksProtocolProxyServerTest {
    @Mock
    OsType osType;

    @Mock
    EventLoopGroup bossGroup;

    @Mock
    EventLoopGroup workerGroup;

    @Mock
    SocksProtocolServerOptions socksProtocolServerOptions;

    @Mock
    Certificates certificates;

    @Mock
    ServerBootstrap serverBootstrap;

    @Mock
    ServerConfig serverConfig;

    @Mock
    ChannelFuture channelFuture;

    SocksProtocolProxyServer server;

    @BeforeEach
    void setUp() {
        when(osType.eventLoopGroupSupplier()).thenReturn(workerGroup);
        when(socksProtocolServerOptions.serverConfig()).thenReturn(serverConfig);
        when(osType.serverBootstrapSupplier(false)).thenReturn(serverBootstrap);
        when(serverBootstrap.group(bossGroup, workerGroup)).thenReturn(serverBootstrap);
        when(serverBootstrap.handler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childHandler(any())).thenReturn(serverBootstrap);
        server = new SocksProtocolProxyServer(osType, bossGroup, socksProtocolServerOptions, certificates);
    }

    @Test
    void testNameReturnsExpectedValue() {
        var result = server.name();
        assertEquals("socks-protocol-proxy-server", result);
    }

    @Test
    void testStopShutsDownWorkerGroup() {
        var quietPeriod = 1L;
        var timeout = 2L;
        var unit = TimeUnit.SECONDS;
        server.stop(quietPeriod, timeout, unit);
        verify(workerGroup).shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Test
    void testDoStartBindsServer() throws Exception {
        var bindAddr = "127.0.0.1";
        var bindPort = 1080;
        when(serverConfig.bindAddr()).thenReturn(bindAddr);
        when(serverConfig.bindPort()).thenReturn(bindPort);
        when(serverBootstrap.bind(bindAddr, bindPort)).thenReturn(channelFuture);
        var result = server.doStart();
        assertEquals(channelFuture, result);
        verify(serverBootstrap).bind(bindAddr, bindPort);
    }

    @Test
    void testConfigReturnsServerConfig() {
        var result = server.config();
        assertEquals(serverConfig, result);
    }
}
