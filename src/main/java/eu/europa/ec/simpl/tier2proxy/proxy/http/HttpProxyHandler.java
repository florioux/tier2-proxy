package eu.europa.ec.simpl.tier2proxy.proxy.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.europa.ec.simpl.tier2proxy.HandlerInPipeline;
import eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import eu.europa.ec.simpl.tier2proxy.proxy.handler.BootstrapFactory;
import eu.europa.ec.simpl.tier2proxy.proxy.handler.MitmHandler;
import eu.europa.ec.simpl.util.PemConverter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class HttpProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final HandlerInPipeline<ChannelHandler, ChannelHandler> httpServerCodec;
    private final HandlerInPipeline<ChannelHandler, ChannelHandler> httpObjectAggregator;
    private final HttpProtocolServerOptions httpProtocolServerOptions;
    private final Certificates certificates;
    private final BootstrapFactory bootstrapFactory;

    private Addr dest;

    public HttpProxyHandler(
            HttpProtocolServerOptions httpProtocolServerOptions,
            Certificates certificates,
            BootstrapFactory bootstrapFactory) {
        super();
        this.httpProtocolServerOptions = httpProtocolServerOptions;
        this.certificates = certificates;
        this.bootstrapFactory = Objects.requireNonNullElseGet(bootstrapFactory, BootstrapFactory::new);

        this.httpServerCodec = new HandlerInPipeline<>(this, new HttpServerCodec());
        this.httpObjectAggregator = new HandlerInPipeline<>(
                this, new HttpObjectAggregator(httpProtocolServerOptions.httpObjectAggregatorMaxContentLength()));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.debug("adding handler for {}", ctx.channel());

        ctx.pipeline()
                .addBefore(ctx.name(), this.httpServerCodec.handlerName(), this.httpServerCodec.handler())
                .addBefore(ctx.name(), this.httpObjectAggregator.handlerName(), this.httpObjectAggregator.handler());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.debug("removing handler for {}", ctx.channel());

        ctx.pipeline().remove(this.httpServerCodec.handler()).remove(this.httpObjectAggregator.handler());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        log.info("reading {} from {}", request, ctx.channel());
        if (request.method() == HttpMethod.CONNECT) {
            handleConnectHTTPMessage(ctx, request);
        } else {
            handlePlainTextHTTPMessage(ctx, request);
        }
    }

    private void handleConnectHTTPMessage(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (this.dest == null) {
            this.dest = FullPath.resolveAddrInConnect(request.uri());
        }
        log.debug("[HttpProxyHandler] handleConnectHTTPMessage: {} -> {}", this.dest, request);

        var response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
        log.info("[Client] <= [Proxy] : {}", response);

        ctx.writeAndFlush(response);

        log.debug("hijacking TLS for {}", this.dest);
        var certificateInfo = certificates.certificateFor(dest.addr());

        HandlerInPipeline<ChannelHandler, ChannelHandler> mitmHandler = new HandlerInPipeline<>(
                this,
                new MitmHandler(
                        this.dest,
                        this.httpProtocolServerOptions.httpObjectAggregatorMaxContentLength(),
                        new AuthProviderClient(
                                new HTTPClient(bootstrapFactory.get(ctx)),
                                new ObjectMapper().registerModule(new JavaTimeModule()),
                                new PemConverter()),
                        TLS.getServerSslContext(certificateInfo.privateKey(), certificateInfo.certificate())));

        ctx.pipeline().replace(this, mitmHandler.handlerName(), mitmHandler.handler());
    }

    private void handlePlainTextHTTPMessage(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (this.dest == null) {
            var path = new FullPath(request.uri());
            this.dest = path.toAddr();
        }

        log.debug("handlePlainTextHTTPMessage: {} -> {}", this.dest, request);
        log.info("Establish connection to the target server to {}", dest);

        HandlerInPipeline<ChannelHandler, ChannelHandler> mitmHandler = new HandlerInPipeline<>(
                this,
                new MitmHandler(
                        this.dest,
                        this.httpProtocolServerOptions.httpObjectAggregatorMaxContentLength(),
                        new AuthProviderClient(
                                new HTTPClient(bootstrapFactory.get(ctx)),
                                new ObjectMapper().registerModule(new JavaTimeModule()),
                                new PemConverter()),
                        null));

        ctx.pipeline()
                .replace(this, mitmHandler.handlerName(), mitmHandler.handler())
                .fireChannelRead(ReferenceCountUtil.retain(request));
    }
}
