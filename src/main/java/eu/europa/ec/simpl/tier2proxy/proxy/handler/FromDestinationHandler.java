package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import java.util.Objects;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class FromDestinationHandler<T> extends SimpleChannelInboundHandler<T> {

    protected final Addr dest;
    protected final Channel source;
    protected SslContext tlsClientContext;
    protected final ConnectionType connectionType;

    private final ChannelHandler httpClientCodec = new HttpClientCodec();
    private final ChannelHandler httpObjectAggregator = new HttpObjectAggregator(65536);

    protected FromDestinationHandler(Addr dest, Channel source, ConnectionType connectionType) {
        this.dest = dest;
        this.source = source;
        this.connectionType = connectionType;
        try {
            this.tlsClientContext = TLS.getClientSslContext(connectionType);
        } catch (SSLException e) {
            throw new IllegalStateException("client ssl context cannot be initialized", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (isNotMTLS()) {
            log.error("exception caught for {}: {}", this.dest, cause.getMessage());
            super.exceptionCaught(ctx, cause);
        } else {
            log.warn("exception caught for mTLS connection");
            log.debug("", cause);
        }
    }

    private boolean isNotMTLS() {
        return !Objects.equals(connectionType, ConnectionType.MTLS);
    }

    protected static void removeHandlers(ChannelHandlerContext ctx) {
        var channel = ctx.channel();
        var pipeline = channel.pipeline();

        removeHttpHandler(pipeline, HttpClientCodec.class);
        removeHttpHandler(pipeline, HttpObjectAggregator.class);
    }

    protected static void removeHttpHandler(ChannelPipeline pipeline, Class<? extends ChannelHandler> clazz) {
        log.debug("removing from {} http handler {}", pipeline, clazz.getSimpleName());

        if (pipeline.get(clazz.getCanonicalName()) != null) {
            pipeline.remove(clazz.getCanonicalName());
        } else {
            log.debug("{} already removed", clazz.getSimpleName());
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.debug("handler added for {}", this.dest);
        log.debug("source active: {}", source.isActive());
        if (this.tlsClientContext != null) {
            log.debug("handling client tls connection for {}", this.dest);

            var sslEngine = this.tlsClientContext.newEngine(ctx.alloc(), dest.addr(), dest.port());

            ctx.pipeline().addBefore(ctx.name(), SslHandler.class.getCanonicalName(), new SslHandler(sslEngine));
        } else {
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

        FromDestinationHandler.removeHandlers(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("channel inactive for {}", this.dest);
        if (isNotMTLS()) {
            source.close();
        }
    }
}
