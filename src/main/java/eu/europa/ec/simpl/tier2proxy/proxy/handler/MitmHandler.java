package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLEngine;
import java.io.IOException;

@Slf4j
public final class MitmHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private final Addr           dest;
	private final ChannelHandler httpServerCodec, httpObjectAggregator;
	private SslContext tlsServerContext;

	public MitmHandler(Addr dest, int httpObjectAggregatorMaxContentLength) {
		super();
		this.dest = dest;

		this.httpServerCodec = new HttpServerCodec();
		this.httpObjectAggregator = new HttpObjectAggregator(httpObjectAggregatorMaxContentLength);
	}

	public MitmHandler(Certificates certificates, Addr dest, int httpObjectAggregatorMaxContentLength) throws IOException {
		this(dest, httpObjectAggregatorMaxContentLength);
		if (log.isDebugEnabled()) {
			log.debug("preparing handler for destination {}", dest);
		}
		CertificateInfo certificateInfo = certificates.certificateFor(dest.addr());

		this.tlsServerContext = TLS.getServerSslContext(certificateInfo.privateKey(), certificateInfo.certificate());
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		ChannelPipeline pipeline = ctx.pipeline();
		if (this.tlsServerContext != null) {
			if (log.isDebugEnabled()) {
				log.debug("handling server tls connection for {}", this.dest);
			}

			SSLEngine sslEngine = this.tlsServerContext.newEngine(ctx.channel().alloc());
			pipeline.addBefore(ctx.name(), SslHandler.class.getCanonicalName(), new SslHandler(sslEngine));
		} else if (log.isDebugEnabled()) {

			log.debug("handling server plaintext connection for {}", this.dest);
		}

		pipeline.addBefore(ctx.name(), HttpServerCodec.class.getCanonicalName(), this.httpServerCodec)
				.addBefore(ctx.name(), HttpObjectAggregator.class.getCanonicalName(), this.httpObjectAggregator);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("handlerRemoved for {}", this.dest);
		}

		ChannelPipeline pipeline = ctx.pipeline();
		if (this.tlsServerContext != null) {
			pipeline.remove(SslHandler.class);
		}

		if (log.isDebugEnabled()) {
			log.debug("removing from {} http handlers", ctx);
		}

		if (pipeline.get(HttpServerCodec.class.getCanonicalName()) != null) {
			pipeline.remove(HttpServerCodec.class.getCanonicalName());
		} if (log.isDebugEnabled()) {
			log.debug("http server codec already removed");
		}

		if (pipeline.get(HttpObjectAggregator.class.getCanonicalName()) != null) {
			pipeline.remove(HttpObjectAggregator.class.getCanonicalName());
		} else if (log.isDebugEnabled()) {
			log.debug("http object aggregator already removed");
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
		if (log.isDebugEnabled()) {
			log.debug("channelRead0 {}: {}", this.dest, request);
		}

		FullHttpRequest retainedRequest = ReferenceCountUtil.retain(request);

		boolean isWebsocket = MitmHandler.isWebSocketUpgrade(retainedRequest);
		ChannelInitializer<SocketChannel> handler = new OutboundChannelInitializer(
				this.dest, ctx.channel(), isWebsocket, tlsServerContext != null);
		Bootstrap bootstrap = new Bootstrap()
				.group(ctx.channel().eventLoop())
				.channel(ctx.channel().getClass())
				.handler(handler);

		Channel channel = bootstrap.connect(dest.addr(), dest.port())
				.addListener((ChannelFutureListener) future -> {
					if(future.isSuccess()) {
						if(log.isDebugEnabled()) {
							log.debug("writing request into destination for {}", this.dest);
						}

						future.channel().writeAndFlush(retainedRequest);
					} else {
						if(log.isWarnEnabled()) {
							log.warn("destination is not active for {}", this.dest);
						}
						ctx.close();
					}
				})
				.channel();

		if (isWebsocket) {
			ctx.pipeline()
					.addLast(RelayHandler.class.getCanonicalName(), new RelayHandler(channel));
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		if (log.isDebugEnabled()) {
			log.debug("channel inactive for {}", this.dest);
		}
	}

	private static boolean isWebSocketUpgrade(HttpMessage response) {
		HttpHeaders headers = response.headers();
		return "websocket".equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE))
				&& "Upgrade".equalsIgnoreCase(headers.get(HttpHeaderNames.CONNECTION));
	}
}

@Slf4j
@RequiredArgsConstructor
final class OutboundChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final Addr dest;
	private final Channel source;
	private final boolean isWebsocket, isTls;

	@Override
	protected void initChannel(SocketChannel ch) {
		ChannelPipeline pipeline = ch.pipeline();
		if(log.isInfoEnabled()) {
			log.info("preparing channel pipeline for {}", dest);
		}

		ChannelHandler handler;
		if(isWebsocket) {
			if(log.isDebugEnabled()) {
				log.debug("handling websocket connection for {}", dest);
			}
			handler = new FromWebSocketHandler(dest, source, isTls);
		} else {
			if(log.isDebugEnabled()) {
				log.debug("handling http connection for {}", dest);
			}
			handler = new FromHTTPHandler(dest, source, isTls);
		}

		pipeline.replace(this, handler.getClass().getCanonicalName(), handler);
	}
}
