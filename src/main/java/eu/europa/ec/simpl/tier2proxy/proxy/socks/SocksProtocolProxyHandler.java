package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.handler.TrafficHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.*;
import io.netty.handler.codec.socksx.v5.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class SocksProtocolProxyHandler extends SimpleChannelInboundHandler<SocksMessage> {
	private final ChannelHandler             socksPortUnificationServerHandler;
	private final Certificates               certificates;
	private final SocksProtocolServerOptions socksProtocolServerOptions;

	private SocksVersion socksVersion = SocksVersion.UNKNOWN;

	public SocksProtocolProxyHandler(Certificates certificates, SocksProtocolServerOptions socksProtocolServerOptions) {
		this.socksPortUnificationServerHandler = new SocksPortUnificationServerHandler();
		this.certificates = certificates;
		this.socksProtocolServerOptions = socksProtocolServerOptions;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) {
		if (log.isDebugEnabled()) {
			log.debug("adding handler for {}", ctx.channel());
		}

		ctx.pipeline()
		   .addBefore(ctx.name(), null, this.socksPortUnificationServerHandler);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) {
		if (log.isDebugEnabled()) {
			log.debug("removing handler for {}", ctx.channel());
		}

		ChannelPipeline pipeline = ctx.pipeline();
		switch(this.socksVersion) {
			case SOCKS5 -> {
				pipeline.remove(Socks5ServerEncoder.class);
				pipeline.remove(Socks5CommandRequestDecoder.class);
				pipeline.remove(Socks5InitialRequestDecoder.class);
			}
			case SOCKS4a -> {
				pipeline.remove(Socks4ServerEncoder.class);
				pipeline.remove(Socks4ServerDecoder.class);
			}
			case null, default -> {
				if (log.isWarnEnabled()) {
					log.warn("No socks handlers to remove for {}", ctx.channel());
				}
			}
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, SocksMessage socksMessage) {
		if (log.isDebugEnabled()) {
			log.debug("reading channel data for {}: {}", ctx.channel(), socksMessage);
		}
		this.socksVersion = socksMessage.version();
		switch (this.socksVersion) {
			case SOCKS4a:
				if (log.isInfoEnabled()) {
					log.info("socks4 request: {}", socksMessage);
				}
				Socks4CommandRequest socksV4CmdRequest = (Socks4CommandRequest) socksMessage;
				if (socksV4CmdRequest.type() == Socks4CommandType.CONNECT) {
					onSocksSuccess(ctx, socksV4CmdRequest);
				} else {
					ctx.close();
				}
				break;
			case SOCKS5:
				switch(socksMessage) {
					case Socks5InitialRequest socks5InitialRequest -> {
						if (log.isInfoEnabled()) {
							log.info("socks5 initial request: {}", socks5InitialRequest);
						}
						ctx.pipeline()
						   .addFirst(new Socks5CommandRequestDecoder());
						ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
					}
					case Socks5PasswordAuthRequest socks5PasswordAuthRequest -> {
						if (log.isInfoEnabled()) {
							log.info("socks password authentication request: {}", socks5PasswordAuthRequest);
						}
						ctx.pipeline()
						   .addFirst(new Socks5CommandRequestDecoder());
						ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
					}
					case Socks5CommandRequest socks5CmdRequest -> {
						if (log.isInfoEnabled()) {
							log.info("sock5 command request: {}", socks5CmdRequest);
						}
						if(socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
							onSocksSuccess(ctx, socks5CmdRequest);
						} else {
							ctx.close();
						}
					}
					default -> ctx.close();
				}
				break;
			case UNKNOWN:
				ctx.close();
				break;
		}
	}

	private void onSocksSuccess(ChannelHandlerContext ctx, Socks4CommandRequest request) {
		Addr dest = new Addr(request.dstAddr(), request.dstPort());

		ctx.writeAndFlush(new DefaultSocks4CommandResponse(
				Socks4CommandStatus.SUCCESS,
				request.dstAddr(),
				request.dstPort()));

		onServerConnected(ctx, dest);
	}

	private void onSocksSuccess(ChannelHandlerContext ctx, Socks5CommandRequest request) {
		Addr dest = new Addr(request.dstAddr(), request.dstPort());

		ctx.writeAndFlush(new DefaultSocks5CommandResponse(
				Socks5CommandStatus.SUCCESS,
				request.dstAddrType(),
				request.dstAddr(),
				request.dstPort()));

		onServerConnected(ctx, dest);
	}

	private void onServerConnected(ChannelHandlerContext ctx, Addr dest) {
		TrafficHandler handler = new TrafficHandler(this.certificates, dest, this.socksProtocolServerOptions.httpObjectAggregatorMaxContentLength());

		ctx.pipeline()
		   .replace(this, TrafficHandler.class.getCanonicalName(), handler);
	}
}
