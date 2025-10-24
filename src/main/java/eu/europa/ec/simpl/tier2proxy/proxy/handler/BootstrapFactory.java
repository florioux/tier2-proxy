package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;

public class BootstrapFactory {

    public Bootstrap get(ChannelHandlerContext ctx) {
        return new Bootstrap()
                .group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass());
    }
}
