package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static eu.europa.ec.simpl.tier2proxy.enums.ConnectionType.MTLS;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FromHTTPHandlerTest {

    @Mock
    Addr dest;

    @Mock
    Channel source;

    @Mock
    ChannelHandlerContext ctx;

    @Mock
    FullHttpResponse response;

    @Mock
    io.netty.buffer.ByteBuf content;

    FromHTTPHandler handler;

    @Test
    void testChannelRead0SourceActive() {
        try (var mock = mockStatic(TLS.class)) {
            var sslContext = mock(io.netty.handler.ssl.SslContext.class, RETURNS_DEEP_STUBS);
            mock.when(() -> TLS.getClientSslContext(any())).thenReturn(sslContext);

            handler = new FromHTTPHandler(dest, source, MTLS);

            var retained = mock(FullHttpResponse.class);
            when(response.retainedDuplicate()).thenReturn(retained);
            when(source.isActive()).thenReturn(true);
            when(response.content()).thenReturn(content);
            when(content.toString(java.nio.charset.StandardCharsets.UTF_8)).thenReturn("body");

            handler.channelRead0(ctx, response);

            verify(source).writeAndFlush(retained);
            verify(retained, never()).release();
        }
    }

    @Test
    void testHandlerAdded() {
        try (var mock = mockStatic(TLS.class)) {
            var sslContext = mock(io.netty.handler.ssl.SslContext.class, RETURNS_DEEP_STUBS);
            mock.when(() -> TLS.getClientSslContext(any())).thenReturn(sslContext);

            handler = new FromHTTPHandler(dest, source, MTLS);

            var pipeline = mock(ChannelPipeline.class);
            var handlerName = "testHandler";
            given(ctx.pipeline()).willReturn(pipeline);
            given(ctx.pipeline().addBefore(anyString(), anyString(), any())).willReturn(pipeline);
            given(ctx.name()).willReturn(handlerName);

            handler.handlerAdded(ctx);

            then(ctx.pipeline())
                    .should()
                    .addBefore(
                            eq(handlerName), eq(HttpClientCodec.class.getCanonicalName()), any(HttpClientCodec.class));
            then(ctx.pipeline())
                    .should()
                    .addBefore(
                            eq(handlerName),
                            eq(HttpObjectAggregator.class.getCanonicalName()),
                            any(HttpObjectAggregator.class));
            then(ctx.pipeline())
                    .should()
                    .addBefore(eq(handlerName), eq(SslHandler.class.getCanonicalName()), any(SslHandler.class));
        }
    }

    @Test
    void testChannelRead0SourceInactive() {
        try (var mock = mockStatic(TLS.class)) {
            var sslContext = mock(io.netty.handler.ssl.SslContext.class, RETURNS_DEEP_STUBS);
            mock.when(() -> TLS.getClientSslContext(any())).thenReturn(sslContext);

            handler = new FromHTTPHandler(dest, source, MTLS);

            var retained = mock(FullHttpResponse.class);
            when(response.retainedDuplicate()).thenReturn(retained);
            when(source.isActive()).thenReturn(false);
            when(response.content()).thenReturn(content);
            when(content.toString(java.nio.charset.StandardCharsets.UTF_8)).thenReturn("body");

            handler.channelRead0(ctx, response);

            verify(source, never()).writeAndFlush(any());
            verify(retained).release();
        }
    }

    @Test
    void testExceptionCaughtMTLS() throws Exception {
        try (var mock = mockStatic(TLS.class)) {
            var sslContext = mock(io.netty.handler.ssl.SslContext.class, RETURNS_DEEP_STUBS);
            mock.when(() -> TLS.getClientSslContext(any())).thenReturn(sslContext);

            handler = new FromHTTPHandler(dest, source, MTLS);

            var cause = new RuntimeException("fail");
            handler.exceptionCaught(ctx, cause);
        }
    }

    @Test
    void testChannelInactiveMTLS() {
        try (var mock = mockStatic(TLS.class)) {
            var sslContext = mock(io.netty.handler.ssl.SslContext.class, RETURNS_DEEP_STUBS);
            mock.when(() -> TLS.getClientSslContext(any())).thenReturn(sslContext);

            handler = new FromHTTPHandler(dest, source, MTLS);

            handler.channelInactive(ctx);
            verify(source, never()).close();
        }
    }
}
