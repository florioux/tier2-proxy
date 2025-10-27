package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MitmHandlerTest {

    @Mock
    private Addr dest;

    @Mock
    private Certificates certificates;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelPipeline pipeline;

    @Mock
    private Channel channel;

    @Mock
    private FullHttpRequest request;

    @Mock
    private SslContext sslContext;

    @Mock
    private AuthProviderClient authProviderClient;

    @Mock
    private BootstrapFactory bootstrapFactory;

    @Test
    void testHandlerAddedWithTls() throws IOException {
        when(ctx.pipeline()).thenReturn(pipeline);
        when(ctx.channel()).thenReturn(channel);
        when(sslContext.newEngine(any())).thenReturn(mock(javax.net.ssl.SSLEngine.class));
        when(pipeline.addBefore(any(), any(), any())).thenReturn(pipeline);

        when(sslContext.newEngine(any())).thenReturn(mock(javax.net.ssl.SSLEngine.class));

        try (MockedStatic<TLS> tlsMock = mockStatic(TLS.class)) {
            tlsMock.when(() -> TLS.getServerSslContext(any(), any())).thenReturn(sslContext);

            MitmHandler handler = new MitmHandler(dest, 1024, authProviderClient, sslContext);
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
        MitmHandler handler = new MitmHandler(dest, 1024, authProviderClient, bootstrapFactory, null);
        handler.handlerAdded(ctx);

        verify(pipeline, never()).addBefore(any(), eq("io.netty.handler.ssl.SslHandler"), any());
        verify(pipeline, atLeastOnce())
                .addBefore(any(), eq(io.netty.handler.codec.http.HttpServerCodec.class.getName()), any());
        verify(pipeline, atLeastOnce())
                .addBefore(any(), eq(io.netty.handler.codec.http.HttpObjectAggregator.class.getName()), any());
    }

    @Test
    void testHandlerRemovedWithoutTls() {
        when(pipeline.get(anyString())).thenReturn(mock(ChannelHandler.class));
        when(ctx.pipeline()).thenReturn(pipeline);

        MitmHandler handler = new MitmHandler(dest, 1024, authProviderClient, bootstrapFactory, null);
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
    void testShouldNotDoPreflightOnExcludedPaths() throws Exception {
        var method = MitmHandler.class.getDeclaredMethod("shouldDoPreflight", FullHttpRequest.class);
        method.setAccessible(true);

        testShouldDoPreflight(false, HttpMethod.POST, "/authApi/v1/mtls/ephemeralProof");
        testShouldDoPreflight(false, HttpMethod.GET, "/identityApi/v1/mtls/whoami");
        testShouldDoPreflight(false, HttpMethod.POST, "/sapApi/v1/mtls/token");
        testShouldDoPreflight(false, HttpMethod.PATCH, "/identityApi/v1/mtls/publicKey");
    }

    @Test
    void testShouldDoPreflightOnNonExcludedPaths() throws Exception {
        testShouldDoPreflight(true, HttpMethod.GET, "/some/other/uri");
    }

    private void testShouldDoPreflight(boolean shouldDo, HttpMethod httpMethod, String uri) throws Exception {
        var bootstrap = mock(Bootstrap.class);
        var channelFuture = mock(io.netty.channel.ChannelFuture.class);
        var channelFromFuture = mock(Channel.class);
        given(bootstrap.connect(anyString(), anyInt())).willReturn(channelFuture);
        given(channelFuture.addListener(any())).willReturn(channelFuture);
        given(channelFuture.channel()).willReturn(channelFromFuture);
        given(channelFromFuture.closeFuture()).willReturn(channelFuture);

        given(ctx.channel()).willReturn(channel);
        if (shouldDo) {
            given(authProviderClient.getEphemeralProofAndSendToDest(anyString()))
                    .willReturn(CompletableFuture.completedFuture(null));
        }
        given(dest.addr()).willReturn("test-addr");
        given(dest.port()).willReturn(443);

        given(bootstrapFactory.get(ctx)).willReturn(bootstrap);
        given(bootstrap.handler(any())).willReturn(bootstrap);
        given(request.retainedDuplicate()).willReturn(request);
        given(request.method()).willReturn(httpMethod);
        given(request.headers()).willReturn(mock(HttpHeaders.class));
        given(request.uri()).willReturn(uri);

        new MitmHandler(dest, 1024, authProviderClient, bootstrapFactory, sslContext).channelRead0(ctx, request);

        then(authProviderClient).should(shouldDo ? atLeastOnce() : never()).getEphemeralProofAndSendToDest(anyString());
    }

    private void testShouldDoPreflight2(boolean shouldDo, HttpMethod httpMethod, String uri) throws Exception {
        var method = MitmHandler.class.getDeclaredMethod("shouldDoPreflight", FullHttpRequest.class);
        method.setAccessible(true);

        var request = mock(FullHttpRequest.class);
        given(request.method()).willReturn(httpMethod);
        given(request.uri()).willReturn(uri);

        if (shouldDo) {
            assertTrue((Boolean) method.invoke(null, request));
        } else {
            assertFalse((Boolean) method.invoke(null, request));
        }
    }

    @Test
    void testSendEphemeralProof() {
        var ctx = mock(ChannelHandlerContext.class);
        var channel = mock(Channel.class);
        var eventLoop = mock(io.netty.channel.EventLoop.class);
        var addr = mock(Addr.class);
        var completableFuture = new java.util.concurrent.CompletableFuture<Void>();
        var destAddr = "test-addr";

        when(addr.addr()).thenReturn(destAddr);

        given(authProviderClient.getEphemeralProofAndSendToDest(anyString())).willReturn(completableFuture);
        var handler = new MitmHandler(addr, 1024, authProviderClient, bootstrapFactory, sslContext);
        var result = handler.sendEphemeralProof();
        assertThat(result).isEqualTo(completableFuture);
    }
}
