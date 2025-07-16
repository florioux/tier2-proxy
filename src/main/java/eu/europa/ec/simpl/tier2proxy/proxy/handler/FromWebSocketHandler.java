package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class FromWebSocketHandler extends FromDestinationHandler<Object> {

    FromWebSocketHandler(Addr dest, Channel source, ConnectionType connectionType) {
        super(dest, source, connectionType);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        log.debug("DAL ECHO {}: {}", ctx.channel().pipeline(), msg);
        var retainedMessage = ReferenceCountUtil.retain(msg);
        source.writeAndFlush(retainedMessage)
                .addListener((ChannelFutureListener) future -> {
                    var channel = future.channel();
                    var pipeline = channel.pipeline();
                    log.debug("removing from source channel {} http handlers", channel);

                    if (tlsClientContext == null) {
                        removeManInTheMiddleHandler(pipeline);
                    }
                })
                .addListener(future -> removeHandlers(ctx));
    }

    private static void removeManInTheMiddleHandler(ChannelPipeline pipeline) {
        removeHttpHandler(pipeline, MitmHandler.class);
    }
}
