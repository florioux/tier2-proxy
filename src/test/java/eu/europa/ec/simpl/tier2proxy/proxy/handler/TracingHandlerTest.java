package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.mockito.Mockito.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracingHandlerTest {
    @Mock
    ChannelHandlerContext ctx;

    @Mock
    Channel channel;

    @Mock
    ChannelPromise promise;

    @Mock
    ChannelPipeline pipeline;

    @Mock
    SslHandler sslHandler;

    @Test
    void testChannelReadLogsAndFiresRead() {
        var handler = new TracingHandler("testWhere");
        var msg = "testMsg";
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(null);

        handler.channelRead(ctx, msg);

        verify(ctx).fireChannelRead(msg);
        verify(pipeline, atLeastOnce()).get(SslHandler.class);
    }

    @Test
    void testChannelReadWithSslHandler() {
        var handler = new TracingHandler("testWhere");
        var msg = "testMsg";
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);

        handler.channelRead(ctx, msg);

        verify(ctx).fireChannelRead(msg);
        verify(pipeline, atLeastOnce()).get(SslHandler.class);
    }

    @Test
    void testWriteLogsAndCallsSuper() throws Exception {
        var handler = new TracingHandler("testWhere");
        var msg = "testMsg";
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(null);

        handler.write(ctx, msg, promise);

        verify(pipeline, atLeastOnce()).get(SslHandler.class);
    }

    @Test
    void testWriteWithSslHandler() throws Exception {
        var handler = new TracingHandler("testWhere");
        var msg = "testMsg";
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);

        handler.write(ctx, msg, promise);

        verify(pipeline, atLeastOnce()).get(SslHandler.class);
    }
}
