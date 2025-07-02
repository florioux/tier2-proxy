package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MitmHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    public static final String PARTICIPANT_EPHEMERAL_PROOF_TARGET_V1 = "/authApi/v1/mtls/ephemeralProof";
    private static final String AUTHORITY_WHOAMI_TARGET_V1 = "/identityApi/v1/mtls/whoami";
    private static final String AUTHORITY_EPHEMERAL_PROOF_TARGET_V1 = "/identityApi/v1/mtls/token";
    private static final String AUTHORITY_PUBLIC_KEY_TARGET_V1 = "/identityApi/v1/mtls/publicKey";

    private static final Set<String> NO_PREFLIGHT_PATHS = Set.of(
            AUTHORITY_EPHEMERAL_PROOF_TARGET_V1,
            AUTHORITY_PUBLIC_KEY_TARGET_V1,
            PARTICIPANT_EPHEMERAL_PROOF_TARGET_V1,
            AUTHORITY_WHOAMI_TARGET_V1);

    private final Addr dest;
    private final ChannelHandler httpServerCodec;
    private final ChannelHandler httpObjectAggregator;
    private SslContext tlsServerContext;

    public MitmHandler(Addr dest, int httpObjectAggregatorMaxContentLength) {
        super();
        this.dest = dest;
        this.httpServerCodec = new HttpServerCodec();
        this.httpObjectAggregator = new HttpObjectAggregator(httpObjectAggregatorMaxContentLength);
    }

    public MitmHandler(Certificates certificates, Addr dest, int httpObjectAggregatorMaxContentLength)
            throws IOException {
        this(dest, httpObjectAggregatorMaxContentLength);
        log.debug("preparing handler for destination {}", dest);
        var certificateInfo = certificates.certificateFor(dest.addr());

        this.tlsServerContext = TLS.getServerSslContext(certificateInfo.privateKey(), certificateInfo.certificate());
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (this.tlsServerContext != null) {
            log.debug("handling server tls connection for {}", this.dest);

            var sslEngine = this.tlsServerContext.newEngine(ctx.channel().alloc());
            pipeline.addBefore(ctx.name(), SslHandler.class.getCanonicalName(), new SslHandler(sslEngine));
        } else {
            log.debug("handling server plaintext connection for {}", this.dest);
        }

        pipeline.addBefore(ctx.name(), HttpServerCodec.class.getCanonicalName(), this.httpServerCodec)
                .addBefore(ctx.name(), HttpObjectAggregator.class.getCanonicalName(), this.httpObjectAggregator);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.debug("handlerRemoved for {}", this.dest);

        ChannelPipeline pipeline = ctx.pipeline();
        if (this.tlsServerContext != null) {
            pipeline.remove(SslHandler.class);
        }

        log.debug("removing from {} http handlers", ctx);

        if (pipeline.get(HttpServerCodec.class.getCanonicalName()) != null) {
            pipeline.remove(HttpServerCodec.class.getCanonicalName());
        }
        log.debug("http server codec already removed");

        if (pipeline.get(HttpObjectAggregator.class.getCanonicalName()) != null) {
            pipeline.remove(HttpObjectAggregator.class.getCanonicalName());
        } else {
            log.debug("http object aggregator already removed");
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        log.debug("channelRead0 {}: {}", this.dest, request);

        var retainedRequest = request.retainedDuplicate();
        retainedRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        var isWebsocket = MitmHandler.isWebSocketUpgrade(retainedRequest);
        var connectionType = tlsServerContext != null ? ConnectionType.MTLS : ConnectionType.HTTP;

        if (Objects.equals(connectionType, ConnectionType.MTLS)) {
            log.debug("handling MTLS connection for {}", this.dest);
            if (shouldDoPreflight(retainedRequest.uri())) {
                sendEphemeralProof(ctx)
                        .thenAccept(futureCtx -> {
                            log.debug("Ephemeral proof sent to peer {}", this.dest);
                            doRealCall(ctx, isWebsocket, connectionType, retainedRequest);
                        })
                        .exceptionally(ex -> {
                            log.warn("Unable to send ephemeral proof to peer {}", this.dest, ex);
                            doRealCall(ctx, isWebsocket, connectionType, retainedRequest);
                            return null;
                        });
            } else {
                log.debug("skipping preflight for {}", this.dest);
                doRealCall(ctx, isWebsocket, connectionType, retainedRequest);
            }
        } else {
            log.debug("Detected {} connection for {}", connectionType, this.dest);
            doRealCall(ctx, isWebsocket, connectionType, retainedRequest);
        }
    }

    private void doRealCall(
            ChannelHandlerContext ctx,
            boolean isWebsocket,
            ConnectionType connectionType,
            FullHttpRequest retainedRequest) {
        var handler = new OutboundChannelInitializer(this.dest, ctx.channel(), isWebsocket, connectionType);
        retainedRequest.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var channel = connectToDestination(ctx, handler, retainedRequest);

        var copyRetainedRequest = retainedRequest.copy();

        channel.closeFuture().addListener((ChannelFutureListener) future -> {
            log.debug("connection {} closed to destination {}", connectionType, this.dest);
            var handlerTLS = new OutboundChannelInitializer(this.dest, ctx.channel(), isWebsocket, ConnectionType.TLS);
            connectToDestination(ctx, handlerTLS, copyRetainedRequest);
        });

        if (isWebsocket) {
            ctx.pipeline().addLast(RelayHandler.class.getCanonicalName(), new RelayHandler(ctx.channel()));
        }
    }

    public CompletableFuture<Void> sendEphemeralProof(ChannelHandlerContext ctx) {
        return new AuthProviderClient(new Bootstrap()
                        .group(ctx.channel().eventLoop())
                        .channel(ctx.channel().getClass()))
                .getEphemeralProofAndSendToDest(dest.addr());
    }

    private static boolean shouldDoPreflight(String uri) {
        return !NO_PREFLIGHT_PATHS.contains(uri);
    }

    private Channel connectToDestination(
            ChannelHandlerContext ctx, OutboundChannelInitializer handlerMTLS, FullHttpRequest retainedRequest) {
        var bootstrap = new Bootstrap()
                .group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .handler(handlerMTLS);

        return bootstrap
                .connect(dest.addr(), dest.port())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.debug("writing request into destination for {}", this.dest);
                        future.channel().writeAndFlush(retainedRequest);
                    } else {
                        log.warn("destination is not active for {}", this.dest);
                        ctx.close();
                    }
                })
                .channel();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.debug("channel inactive for {}", this.dest);
    }

    private static boolean isWebSocketUpgrade(HttpMessage response) {
        HttpHeaders headers = response.headers();
        return "websocket".equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE))
                && "Upgrade".equalsIgnoreCase(headers.get(HttpHeaderNames.CONNECTION));
    }

    @Slf4j
    @RequiredArgsConstructor
    static final class OutboundChannelInitializer extends ChannelInitializer<SocketChannel> {
        private final Addr dest;
        private final Channel source;
        private final boolean isWebsocket;
        private final ConnectionType connectionType;

        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            log.info("preparing channel pipeline for {} with connection type {}", dest, connectionType);
            ChannelHandler handler;
            if (isWebsocket) {
                log.debug("handling websocket connection for {}", dest);
                handler = new FromWebSocketHandler(dest, source, connectionType);
            } else {
                log.debug("handling http connection for {}", dest);
                handler = new FromHTTPHandler(dest, source, connectionType);
            }

            pipeline.replace(this, handler.getClass().getCanonicalName(), handler);
        }
    }
}
