package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FromDestinationHandlerTest {

    @Mock
    Addr dest;

    @Mock
    Channel source;

    @Mock
    SslContext sslContext;

    @Mock
    ChannelHandlerContext ctx;

    @Mock
    Channel channel;

    @Mock
    ChannelPipeline pipeline;

    @Mock
    SSLEngine sslEngine;

    FromDestinationHandler<Object> handler;

    ConnectionType connectionType;

    @Test
    void testExceptionCaughtNotMTLS() throws Exception {
        var cause = new RuntimeException("fail");
        handler = new FromDestinationHandler<>(dest, source, ConnectionType.TLS) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {}
        };
        handler.tlsClientContext = sslContext;
        handler.exceptionCaught(ctx, cause);
    }

    @Test
    void testExceptionCaughtMTLS() throws Exception {
        handler = new FromDestinationHandler<>(dest, source, ConnectionType.MTLS) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {}
        };
        handler.tlsClientContext = sslContext;
        var cause = new RuntimeException("fail");
        handler.exceptionCaught(ctx, cause);
    }

    @Test
    void testHandlerRemovedWithTLS() {
        handler = new FromDestinationHandler<>(dest, source, ConnectionType.TLS) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {}
        };
        handler.tlsClientContext = sslContext;
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        when(ctx.pipeline()).thenReturn(pipeline);
        handler.handlerRemoved(ctx);
        verify(pipeline).remove(SslHandler.class.getCanonicalName());
    }

    @Test
    void testChannelInactiveNotMTLS() {
        handler = new FromDestinationHandler<>(dest, source, ConnectionType.TLS) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {}
        };
        handler.tlsClientContext = sslContext;
        handler.channelInactive(ctx);
        verify(source).close();
    }

    @Test
    void testChannelInactiveMTLS() {
        handler = new FromDestinationHandler<>(dest, source, ConnectionType.MTLS) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {}
        };
        handler.tlsClientContext = sslContext;
        handler.channelInactive(ctx);
        verify(source, never()).close();
    }

    @Test
    void testRemoveHandlers() {
        handler = new FromDestinationHandler<>(dest, source, ConnectionType.TLS) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {}
        };
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        FromDestinationHandler.removeHandlers(ctx);
        verify(channel, atLeastOnce()).pipeline();
    }

    @Test
    void testRemoveHttpHandlerHandlerPresent() {
        var handlerMock = mock(ChannelHandler.class);
        when(pipeline.get(HttpClientCodec.class.getCanonicalName())).thenReturn(handlerMock);
        FromDestinationHandler.removeHttpHandler(pipeline, HttpClientCodec.class);
        verify(pipeline).remove(HttpClientCodec.class.getCanonicalName());
    }

    @Test
    void testRemoveHttpHandlerHandlerAbsent() {
        when(pipeline.get(HttpClientCodec.class.getCanonicalName())).thenReturn(null);
        FromDestinationHandler.removeHttpHandler(pipeline, HttpClientCodec.class);
        verify(pipeline, never()).remove(HttpClientCodec.class.getCanonicalName());
    }
}
