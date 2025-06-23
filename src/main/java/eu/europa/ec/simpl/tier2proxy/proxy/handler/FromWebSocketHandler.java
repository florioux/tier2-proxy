package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class FromWebSocketHandler extends SimpleChannelInboundHandler<Object> {
    private final Addr dest;
    private final Channel source;
    private final SslContext tlsClientContext;
    private final ChannelHandler httpClientCodec = new HttpClientCodec();
    private final ChannelHandler httpObjectAggregator = new HttpObjectAggregator(65536);

    FromWebSocketHandler(Addr dest, Channel source, boolean isTLS) {
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
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("handler added for {}", this.dest);
        }

        if (this.tlsClientContext != null) {
            log.debug("handling client tls connection for {}", this.dest);

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
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.debug("handler removed for {}", this.dest);

        if (this.tlsClientContext != null) {
            ctx.pipeline().remove(SslHandler.class.getCanonicalName());
        }

        removeHandlers(ctx);
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
                        if (pipeline.get(MitmHandler.class.getCanonicalName()) != null) {
                            pipeline.remove(MitmHandler.class.getCanonicalName());
                        } else {
                            log.debug("MitmHandler already removed");
                        }
                    } else {
                        if (pipeline.get(HttpServerCodec.class.getCanonicalName()) != null) {
                            pipeline.remove(HttpServerCodec.class.getCanonicalName());
                        } else {
                            log.debug("HttpServerCodec already removed");
                        }

                        if (pipeline.get(HttpObjectAggregator.class.getCanonicalName()) != null) {
                            pipeline.remove(HttpObjectAggregator.class.getCanonicalName());
                        } else {
                            log.debug("HttpObjectAggregator already removed");
                        }
                    }
                })
                .addListener(future -> removeHandlers(ctx));
    }

    private void removeHandlers(ChannelHandlerContext ctx) {
        var channel = ctx.channel();
        var pipeline = channel.pipeline();
        log.debug("removing from {} http handlers", channel);

        if (pipeline.get(HttpClientCodec.class.getCanonicalName()) != null) {
            pipeline.remove(HttpClientCodec.class.getCanonicalName());
        }

        log.debug("http client codec already removed");

        if (pipeline.get(HttpObjectAggregator.class.getCanonicalName()) != null) {
            pipeline.remove(HttpObjectAggregator.class.getCanonicalName());
        } else {
            log.debug("http object aggregator already removed");
        }
    }
}
