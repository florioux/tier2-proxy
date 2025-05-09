package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class RelayHandler extends SimpleChannelInboundHandler<Object> {
    private final Channel inboundChannel;

    RelayHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("handler added for {}", ctx.channel());
        }

        if (log.isDebugEnabled()) {
            log.debug("pipeline: {}", ctx.pipeline());
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (log.isDebugEnabled()) {
            log.debug("DA SOCAT {}: {}", ctx.channel().pipeline(), msg);
        }
        inboundChannel.writeAndFlush(ReferenceCountUtil.retain(msg));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        inboundChannel.close();
    }
}
