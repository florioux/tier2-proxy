package eu.europa.ec.simpl.tier2proxy.certificate.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.OsType;
import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificateServerTest {
    @Mock
    OsType osType;

    @Mock
    EventLoopGroup bossGroup;

    @Mock
    EventLoopGroup workerGroup;

    @Mock
    CertificateServerOptions certificateServerOptions;

    @Mock
    X509Certificate caCertificate;

    @Mock
    ServerConfig serverConfig;

    @Mock
    ServerBootstrap serverBootstrap;

    @Mock
    ChannelFuture channelFuture;

    @Test
    void testNameReturnsExpectedValue() {
        // arrange
        mockBootstrap();
        var certificateServer = createServer();
        // act
        var result = certificateServer.name();
        // assert
        assertEquals("certificate-ca-server", result);
    }

    @Test
    void testStopCallsShutdownGracefully() {
        // arrange
        mockBootstrap();
        var certificateServer = createServer();
        // act
        certificateServer.stop(1L, 2L, TimeUnit.SECONDS);
        // assert
        verify(workerGroup).shutdownGracefully(1L, 2L, TimeUnit.SECONDS);
    }

    @Test
    void testConfigReturnsServerConfig() {
        // arrange
        mockBootstrap();
        var certificateServer = createServer();
        // act
        var config = certificateServer.config();
        // assert
        assertSame(serverConfig, config);
    }

    @Test
    void testDoStartBindsServer() {
        // arrange
        mockBootstrap();
        when(serverConfig.bindAddr()).thenReturn("localhost");
        when(serverConfig.bindPort()).thenReturn(1234);
        when(serverBootstrap.bind("localhost", 1234)).thenReturn(channelFuture);
        var certificateServer = createServer();
        // act
        var future = certificateServer.doStart();
        // assert
        assertSame(channelFuture, future);
        verify(serverBootstrap).bind("localhost", 1234);
    }

    private void mockBootstrap() {
        when(osType.eventLoopGroupSupplier()).thenReturn(workerGroup);
        when(certificateServerOptions.serverConfig()).thenReturn(serverConfig);
        when(osType.serverBootstrapSupplier(false)).thenReturn(serverBootstrap);
        when(serverBootstrap.group(any(EventLoopGroup.class), any(EventLoopGroup.class)))
                .thenReturn(serverBootstrap);
        when(serverBootstrap.handler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childHandler(any())).thenReturn(serverBootstrap);
        var principal = mock(X500Principal.class);
        when(principal.getName()).thenReturn("CN=Test");
        when(caCertificate.getSubjectX500Principal()).thenReturn(principal);
    }

    private CertificateServer createServer() {
        return new CertificateServer(osType, bossGroup, certificateServerOptions, caCertificate);
    }
}
