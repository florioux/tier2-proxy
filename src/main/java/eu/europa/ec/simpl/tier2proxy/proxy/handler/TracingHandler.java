package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TracingHandler extends ChannelDuplexHandler {
	private final String where;
	TracingHandler(String where) {
		super();
		this.where = where;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (log.isDebugEnabled()) {
			log.debug("{} - [{}] - READ {}: {}", where, ctx.channel(), ctx.channel().pipeline(), msg);
		}
		ctx.fireChannelRead(msg);

		log.info("<-------- {}", ctx.pipeline().get(SslHandler.class) == null);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("{} - [{}] - WRITE {}: {}", where, ctx.channel(), ctx.channel().pipeline(), msg);
		}

		super.write(ctx, msg, promise);

		log.info("<-------- {}", ctx.pipeline().get(SslHandler.class) == null);
	}
}
