package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public final class TrafficHandler extends SimpleChannelInboundHandler<ByteBuf> {
	private final Certificates certificates;
	private final Addr         dest;
	private final int          httpObjectAggregatorMaxContentLength;

	private boolean isTLS(ByteBuf msg) {
		int firstByte = msg.getUnsignedByte(0);
		int secondByte = msg.getUnsignedByte(1);

		return firstByte == 0x16 && secondByte == 0x03;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
		if (log.isInfoEnabled()) {
			log.info("inbound for {}: {}", ctx.name(), msg);
		}
		var messageCopy = msg.copy();
		boolean isTls = isTLS(messageCopy);

		if (isTls) {
			Optional<String> sni = TLS.extractSNI(messageCopy);

			if (sni.isEmpty()) {
				throw new IllegalStateException("SNI must be present");
			}

			Addr newDest = new Addr(sni.get(), this.dest.port());

			ctx.pipeline()
					.replace(this, MitmHandler.class.getCanonicalName(), new MitmHandler(this.certificates, newDest, this.httpObjectAggregatorMaxContentLength))
					.fireChannelRead(ReferenceCountUtil.retain(msg));
		} else {
			EmbeddedChannel em = new EmbeddedChannel();

			em.pipeline().addFirst(
					new HttpServerCodec(),
					new HttpObjectAggregator(65536)
			);

			em.writeInbound(ReferenceCountUtil.retain(msg));

			FullHttpRequest request = em.readInbound();

			ctx.pipeline()
					.replace(this, MitmHandler.class.getCanonicalName(), new MitmHandler(this.dest, this.httpObjectAggregatorMaxContentLength))
					.fireChannelRead(ReferenceCountUtil.retain(request));
		}
	}
}
