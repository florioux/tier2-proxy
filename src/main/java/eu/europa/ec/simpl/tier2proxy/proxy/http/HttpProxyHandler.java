package eu.europa.ec.simpl.tier2proxy.proxy.http;

import eu.europa.ec.simpl.tier2proxy.HandlerInPipeline;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.handler.MitmHandler;
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
import io.netty.util.internal.StringUtil;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class HttpProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final HandlerInPipeline<ChannelHandler, ChannelHandler> httpServerCodec, httpObjectAggregator;
    private final HttpProtocolServerOptions httpProtocolServerOptions;
    private final Certificates certificates;

    private Addr dest;

    public HttpProxyHandler(HttpProtocolServerOptions httpProtocolServerOptions, Certificates certificates) {
        super();
        this.httpProtocolServerOptions = httpProtocolServerOptions;
        this.certificates = certificates;

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

        HandlerInPipeline<ChannelHandler, ChannelHandler> mitmHandler = new HandlerInPipeline<>(
                this,
                new MitmHandler(
                        this.certificates,
                        this.dest,
                        this.httpProtocolServerOptions.httpObjectAggregatorMaxContentLength()));

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
                new MitmHandler(this.dest, this.httpProtocolServerOptions.httpObjectAggregatorMaxContentLength()));

        ctx.pipeline()
                .replace(this, mitmHandler.handlerName(), mitmHandler.handler())
                .fireChannelRead(ReferenceCountUtil.retain(request));
    }

    @Getter
    static final class FullPath {

        private static final Pattern PATH_PATTERN = Pattern.compile("(https?)://([a-zA-Z0-9\\.\\-]+)(:(\\d+))?(/.*)");
        private static final Pattern CONNECT_ADDR_PATTERN = Pattern.compile("^([a-zA-Z0-9\\.\\-_]+):(\\d+)");

        private final String scheme;
        private final String host;
        private final int port;
        private final String path;

        static Addr resolveAddrInConnect(String addr) {
            Matcher matcher = CONNECT_ADDR_PATTERN.matcher(addr);
            if (matcher.find()) {
                return new Addr(matcher.group(1), Integer.parseInt(matcher.group(2)));
            } else {
                throw new IllegalStateException("Illegal tunnel addr: " + addr);
            }
        }

        private static int resolvePort(String scheme, String port) {
            if (StringUtil.isNullOrEmpty(port)) {
                return "https".equals(scheme) ? 443 : 80;
            }
            return Integer.parseInt(port);
        }

        FullPath(String fullPath) {
            var matcher = PATH_PATTERN.matcher(fullPath);
            if (matcher.find()) {
                var i = new AtomicInteger(0);
                this.scheme = matcher.group(i.incrementAndGet());
                this.host = matcher.group(i.incrementAndGet());
                this.port = resolvePort(scheme, matcher.group(i.incrementAndGet()));
                this.path = matcher.group(i.incrementAndGet());
            } else {
                throw new IllegalStateException("Illegal http proxy path: " + fullPath);
            }
        }

        Addr toAddr() {
            return new Addr(this.host, this.port);
        }

        @Override
        public String toString() {
            return "FullPath{" + "scheme='"
                    + scheme + '\'' + ", host='"
                    + host + '\'' + ", port="
                    + port + ", path='"
                    + path + '\'' + '}';
        }
    }
}
