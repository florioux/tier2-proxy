package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FromWebSocketHandlerTest {

    @Mock
    Addr dest;

    @Mock
    Channel source;

    @Mock
    ConnectionType connectionType;

    @Mock
    ChannelHandlerContext ctx;

    @Mock
    ChannelPipeline pipeline;

    @Mock
    Channel channel;

    @Mock
    ChannelFuture channelFuture;

    FromWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FromWebSocketHandler(dest, source, connectionType);
    }

    @Test
    void testChannelRead0WritesAndFlushesMessage() {
        var msg = new Object();
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        when(source.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any(ChannelFutureListener.class))).thenReturn(channelFuture);

        handler.channelRead0(ctx, msg);

        verify(source).writeAndFlush(any());
        verify(channelFuture, atLeastOnce()).addListener(any());
    }

    @Test
    void testChannelRead0RemovesHandlersWhenNoTlsContext() {
        var msg = new Object();
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        when(source.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any(ChannelFutureListener.class))).thenAnswer(invocation -> {
            var listener = invocation.getArgument(0, ChannelFutureListener.class);
            when(channelFuture.channel()).thenReturn(channel);
            listener.operationComplete(channelFuture);
            return channelFuture;
        });

        handler.channelRead0(ctx, msg);
    }
}
