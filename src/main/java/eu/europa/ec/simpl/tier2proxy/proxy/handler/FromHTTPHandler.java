package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class FromHTTPHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private final Addr dest;
    private final Channel source;
    private final SslContext tlsClientContext;
    private final ChannelHandler httpClientCodec = new HttpClientCodec(),
            httpObjectAggregator = new HttpObjectAggregator(65536);

    FromHTTPHandler(Addr dest, Channel source, boolean isTLS) {
        this.dest = dest;
        this.source = source;

        if (isTLS) {

            try {
                this.tlsClientContext = TLS.getClientSslContext();
            } catch (SSLException e) {
                throw new IllegalStateException("client ssl context cannot be initialized", e);
            }
        } else {

            this.tlsClientContext = null;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("handler added for {}", this.dest);
        }

        if (this.tlsClientContext != null) {
            if (log.isDebugEnabled()) {
                log.debug("handling client tls connection for {}", this.dest);
            }

            SSLEngine sslEngine = this.tlsClientContext.newEngine(ctx.alloc(), dest.addr(), dest.port());

            ctx.pipeline().addBefore(ctx.name(), SslHandler.class.getCanonicalName(), new SslHandler(sslEngine));
        } else if (log.isDebugEnabled()) {
            log.debug("handling client plaintext connection for {}", this.dest);
        }

        ctx.pipeline()
                .addBefore(ctx.name(), HttpClientCodec.class.getCanonicalName(), httpClientCodec)
                .addBefore(ctx.name(), HttpObjectAggregator.class.getCanonicalName(), httpObjectAggregator);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("handler removed for {}", this.dest);
        }

        if (this.tlsClientContext != null) {
            ctx.pipeline().remove(SslHandler.class.getCanonicalName());
        }
        ctx.pipeline().remove(httpClientCodec);
        ctx.pipeline().remove(httpObjectAggregator);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
        if (log.isDebugEnabled()) {
            log.debug("forward data for {}: {}", this.dest, response.status());
        }
        FullHttpResponse retainedResponse = ReferenceCountUtil.retain(response);
        source.writeAndFlush(retainedResponse);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("channel inactive for {}", this.dest);
        }
        source.close();
        ctx.close();
    }
}
