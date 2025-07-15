package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.PrivateKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MitmHandlerTest {

    private Addr dest;
    private Certificates certificates;
    private ChannelHandlerContext ctx;
    private ChannelPipeline pipeline;
    private Channel channel;
    private FullHttpRequest request;
    private SslContext sslContext;

    @BeforeEach
    void setUp() {
        dest = mock(Addr.class);
        certificates = mock(Certificates.class);
        ctx = mock(ChannelHandlerContext.class);
        pipeline = mock(ChannelPipeline.class);
        channel = mock(Channel.class);
        request = mock(FullHttpRequest.class);
        sslContext = mock(SslContext.class);
    }

    @Test
    void testHandlerAddedWithTls() throws IOException {
        when(ctx.pipeline()).thenReturn(pipeline);
        when(ctx.channel()).thenReturn(channel);
        when(dest.addr()).thenReturn("localhost");
        when(sslContext.newEngine(any())).thenReturn(mock(javax.net.ssl.SSLEngine.class));
        when(pipeline.addBefore(any(), any(), any())).thenReturn(pipeline);

        CertificateInfo certInfo = mock(CertificateInfo.class);
        when(certificates.certificateFor(anyString())).thenReturn(certInfo);
        when(certInfo.privateKey()).thenReturn(mock(PrivateKey.class));
        when(sslContext.newEngine(any())).thenReturn(mock(javax.net.ssl.SSLEngine.class));

        try (MockedStatic<TLS> tlsMock = mockStatic(TLS.class)) {
            tlsMock.when(() -> TLS.getServerSslContext(any(), any())).thenReturn(sslContext);

            MitmHandler handler = new MitmHandler(certificates, dest, 1024);
            handler.handlerAdded(ctx);

            verify(pipeline, atLeastOnce()).addBefore(any(), eq("io.netty.handler.ssl.SslHandler"), any());
            verify(pipeline, atLeastOnce())
                    .addBefore(any(), eq(io.netty.handler.codec.http.HttpServerCodec.class.getName()), any());
            verify(pipeline, atLeastOnce())
                    .addBefore(any(), eq(io.netty.handler.codec.http.HttpObjectAggregator.class.getName()), any());
        }
    }

    @Test
    void testHandlerAddedWithoutTls() {

        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.addBefore(any(), any(), any())).thenReturn(pipeline);

        MitmHandler handler = new MitmHandler(dest, 1024);
        handler.handlerAdded(ctx);

        verify(pipeline, never()).addBefore(any(), eq("io.netty.handler.ssl.SslHandler"), any());
        verify(pipeline, atLeastOnce())
                .addBefore(any(), eq(io.netty.handler.codec.http.HttpServerCodec.class.getName()), any());
        verify(pipeline, atLeastOnce())
                .addBefore(any(), eq(io.netty.handler.codec.http.HttpObjectAggregator.class.getName()), any());
    }

    @Test
    void testHandlerRemovedWithoutTls() {
        when(pipeline.remove(any(String.class))).thenReturn(mock(ChannelHandler.class));
        when(pipeline.get(anyString())).thenReturn(mock(ChannelHandler.class));
        when(ctx.pipeline()).thenReturn(pipeline);

        MitmHandler handler = new MitmHandler(dest, 1024);
        handler.handlerRemoved(ctx);

        verify(pipeline, never()).remove(SslHandler.class);
        verify(pipeline).get("io.netty.handler.codec.http.HttpServerCodec");
        verify(pipeline).get("io.netty.handler.codec.http.HttpObjectAggregator");
    }

    @Test
    void testInitChannelWithWebsocket() {
        var dest = mock(Addr.class);
        var source = mock(Channel.class);
        var ch = mock(io.netty.channel.socket.SocketChannel.class);
        var pipeline = mock(ChannelPipeline.class);
        when(ch.pipeline()).thenReturn(pipeline);
        when(pipeline.replace(any(ChannelHandler.class), anyString(), any())).thenReturn(pipeline);

        var initializer = new MitmHandler.OutboundChannelInitializer(dest, source, true, ConnectionType.HTTP);
        initializer.initChannel(ch);

        verify(pipeline).replace(eq(initializer), eq(FromWebSocketHandler.class.getCanonicalName()), any());
    }

    @Test
    void testInitChannelWithHttp() {
        var dest = mock(Addr.class);
        var source = mock(Channel.class);
        var ch = mock(io.netty.channel.socket.SocketChannel.class);
        var pipeline = mock(ChannelPipeline.class);
        when(ch.pipeline()).thenReturn(pipeline);
        when(pipeline.replace(any(ChannelHandler.class), anyString(), any())).thenReturn(pipeline);

        var initializer = new MitmHandler.OutboundChannelInitializer(dest, source, false, ConnectionType.HTTP);
        initializer.initChannel(ch);

        verify(pipeline).replace(eq(initializer), eq(FromHTTPHandler.class.getCanonicalName()), any());
    }

    @Test
    void testIsWebSocketUpgrade() throws Exception {
        var headers = mock(HttpHeaders.class);
        var message = mock(HttpMessage.class);
        when(message.headers()).thenReturn(headers);

        Method method = MitmHandler.class.getDeclaredMethod("isWebSocketUpgrade", HttpMessage.class);
        method.setAccessible(true);

        when(headers.get(HttpHeaderNames.UPGRADE)).thenReturn("websocket");
        when(headers.get(HttpHeaderNames.CONNECTION)).thenReturn("Upgrade");
        assertTrue((Boolean) method.invoke(null, message));

        // Negative case: missing UPGRADE
        when(headers.get(HttpHeaderNames.UPGRADE)).thenReturn(null);
        assertFalse((Boolean) method.invoke(null, message));

        // Negative case: missing CONNECTION
        when(headers.get(HttpHeaderNames.UPGRADE)).thenReturn("websocket");
        when(headers.get(HttpHeaderNames.CONNECTION)).thenReturn(null);
        assertFalse((Boolean) method.invoke(null, message));

        // Negative case: different values
        when(headers.get(HttpHeaderNames.UPGRADE)).thenReturn("notwebsocket");
        assertFalse((Boolean) method.invoke(null, message));
    }

    @Test
    void testShouldDoPreflight() throws Exception {
        var method = MitmHandler.class.getDeclaredMethod("shouldDoPreflight", String.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(null, "/authApi/v1/mtls/ephemeralProof"));
        assertFalse((Boolean) method.invoke(null, "/identityApi/v1/mtls/whoami"));
        assertFalse((Boolean) method.invoke(null, "/identityApi/v1/mtls/token"));
        assertFalse((Boolean) method.invoke(null, "/identityApi/v1/mtls/publicKey"));

        assertTrue((Boolean) method.invoke(null, "/some/other/uri"));
        assertTrue((Boolean) method.invoke(null, "/identityApi/v1/mtls/foobar"));
    }

    @Test
    void testSendEphemeralProof() {
        var ctx = mock(ChannelHandlerContext.class);
        var channel = mock(Channel.class);
        var eventLoop = mock(io.netty.channel.EventLoop.class);
        var addr = mock(Addr.class);
        var completableFuture = new java.util.concurrent.CompletableFuture<Void>();
        var destAddr = "test-addr";

        when(ctx.channel()).thenReturn(channel);
        when(channel.eventLoop()).thenReturn(eventLoop);
        when(addr.addr()).thenReturn(destAddr);

        try (var mocked = org.mockito.Mockito.mockConstruction(
                eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient.class, (mock, context) -> {
                    when(mock.getEphemeralProofAndSendToDest(destAddr)).thenReturn(completableFuture);
                })) {
            var handler = new MitmHandler(addr, 1024);
            var result = handler.sendEphemeralProof(ctx);
            assertTrue(result == completableFuture);

            var constructed = mocked.constructed();
            assertTrue(constructed.size() == 1);
            verify(constructed.get(0)).getEphemeralProofAndSendToDest(destAddr);
        }
    }
}
