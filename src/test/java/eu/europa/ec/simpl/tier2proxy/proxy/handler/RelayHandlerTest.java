package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.mockito.Mockito.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelayHandlerTest {
    @Mock
    Channel inboundChannel;

    @Mock
    ChannelHandlerContext ctx;

    @InjectMocks
    RelayHandler relayHandler;

    @Test
    void testHandlerAdded() {
        var channel = mock(Channel.class);
        var pipeline = mock(ChannelPipeline.class);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.pipeline()).thenReturn(pipeline);

        relayHandler.handlerAdded(ctx);

        verify(ctx).channel();
        verify(ctx).pipeline();
    }

    @Test
    void testChannelRead0() {
        var msg = new Object();
        var pipeline = mock(ChannelPipeline.class);
        var channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.pipeline()).thenReturn(pipeline);
        var retainedMsg = new Object();
        try (MockedStatic<ReferenceCountUtil> refUtilMock = mockStatic(ReferenceCountUtil.class)) {
            refUtilMock.when(() -> ReferenceCountUtil.retain(msg)).thenReturn(retainedMsg);
            relayHandler.channelRead0(ctx, msg);
            verify(inboundChannel).writeAndFlush(retainedMsg);
        }
    }

    @Test
    void testChannelInactive() {
        relayHandler.channelInactive(ctx);
        verify(inboundChannel).close();
    }
}
