package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mockStatic;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslContext;
import java.security.PrivateKey;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrafficHandlerTest {

    @Mock
    private SslContext sslContext;

    @Test
    void testShouldReplaceItselfWithMitmHandlerWhenTLSIsDetected() throws Exception {
        // Arrange
        var certificates = mock(Certificates.class, RETURNS_DEEP_STUBS);

        var dest = new Addr("localhost", 443);
        var ctx = mock(ChannelHandlerContext.class);
        var pipeline = mock(ChannelPipeline.class);
        var buf = Unpooled.buffer();

        buf.writeByte(0x16); // TLS ContentType: handshake
        buf.writeByte(0x03); // TLS Version major

        given(ctx.name()).willReturn("handler");
        given(ctx.pipeline()).willReturn(pipeline);
        given(pipeline.replace(any(ChannelHandler.class), anyString(), any(ChannelHandler.class)))
                .willReturn(pipeline);
        given(certificates.getCaCertificate().privateKey()).willReturn(mock(PrivateKey.class));
        try (MockedStatic<TLS> tlsMock = mockStatic(TLS.class)) {
            tlsMock.when(() -> TLS.extractSNI(any())).thenReturn(Optional.of("example.com"));
            tlsMock.when(() -> TLS.getServerSslContext(any(), any())).thenReturn(sslContext);

            var handler = new TrafficHandler(certificates, dest, 65536);

            // Act
            handler.channelRead0(ctx, buf);

            // Assert
            then(pipeline)
                    .should()
                    .replace(eq(handler), eq(MitmHandler.class.getCanonicalName()), any(MitmHandler.class));
        }
    }

    @Test
    void testShouldReplaceItselfWithMitmHandlerWhenHttpIsDetected() throws Exception {
        // Arrange
        var certificates = mock(Certificates.class);
        var dest = new Addr("localhost", 8080);
        var ctx = mock(ChannelHandlerContext.class);
        var pipeline = mock(ChannelPipeline.class);
        var buf = Unpooled.copiedBuffer("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes());

        given(ctx.name()).willReturn("handler");
        given(ctx.pipeline()).willReturn(pipeline);
        given(pipeline.replace(any(ChannelHandler.class), anyString(), any(ChannelHandler.class)))
                .willReturn(pipeline);

        TrafficHandler handler = new TrafficHandler(certificates, dest, 65536);

        // Act
        handler.channelRead0(ctx, buf);

        // Assert
        then(pipeline).should().replace(eq(handler), eq(MitmHandler.class.getCanonicalName()), any(MitmHandler.class));
    }
}
