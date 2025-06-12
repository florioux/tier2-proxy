package eu.europa.ec.simpl.tier2proxy.certificate.http;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.codec.http.HttpUtil;
import java.io.IOException;
import java.security.cert.X509Certificate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CertificateServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final byte[] certificate;
    private final ChannelHandler httpServerCodec,
            httpObjectAggregator,
            httpContentCompressor,
            httpServerExpectContinueHandler;

    private final HttpMethod caServingEndpointMethod;
    private final String caServingEndpointUri;

    public CertificateServerHandler(CertificateServerOptions certificateServerOptions, X509Certificate certificate)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(
                    "server handler for certificate {}",
                    certificate.getSubjectX500Principal().getName());
        }

        this.certificate = Certificates.toPem(certificate);

        this.httpServerCodec = new HttpServerCodec();
        this.httpObjectAggregator = new HttpObjectAggregator(
                certificateServerOptions.httpObjectAggregatorMaxContentLength()); // TODO config
        this.httpContentCompressor = new HttpContentCompressor((CompressionOptions[]) null);
        this.httpServerExpectContinueHandler = new HttpServerExpectContinueHandler();

        this.caServingEndpointMethod = certificateServerOptions.caServingEndpointMethod();
        this.caServingEndpointUri = certificateServerOptions.caServingEndpointUri();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.debug("adding handler for {}", ctx.channel().remoteAddress());

        ctx.pipeline()
                .addBefore(ctx.name(), this.httpServerCodec.getClass().getCanonicalName(), this.httpServerCodec)
                .addBefore(
                        ctx.name(), this.httpObjectAggregator.getClass().getCanonicalName(), this.httpObjectAggregator)
                .addBefore(
                        ctx.name(),
                        this.httpContentCompressor.getClass().getCanonicalName(),
                        this.httpContentCompressor)
                .addBefore(
                        ctx.name(),
                        this.httpServerExpectContinueHandler.getClass().getCanonicalName(),
                        this.httpServerExpectContinueHandler);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.debug("removing handler for {}", ctx.channel().remoteAddress());

        ChannelPipeline pipeline = ctx.pipeline();

        pipeline.remove(this.httpServerCodec);
        pipeline.remove(this.httpContentCompressor);
        pipeline.remove(this.httpServerExpectContinueHandler);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        HttpResponse response;
        if (request.method().equals(this.caServingEndpointMethod)
                && request.uri().equals(this.caServingEndpointUri)) {
            log.info("serving certificate for {}", ctx.channel().remoteAddress());
            FullHttpResponse theResponse = new DefaultFullHttpResponse(
                    request.protocolVersion(), HttpResponseStatus.OK, Unpooled.wrappedBuffer(this.certificate));
            theResponse
                    .headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
                    .setInt(
                            HttpHeaderNames.CONTENT_LENGTH,
                            theResponse.content().readableBytes());

            response = theResponse;
        } else {
            log.warn("requested not found resource for {}", ctx.channel().remoteAddress());

            FullHttpResponse theResponse =
                    new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_FOUND);
            theResponse
                    .headers()
                    .setInt(
                            HttpHeaderNames.CONTENT_LENGTH,
                            theResponse.content().readableBytes());
            response = theResponse;
        }

        if (keepAlive) {
            if (!request.protocolVersion().isKeepAliveDefault()) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        ChannelFuture f = ctx.writeAndFlush(response);

        if (!keepAlive) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel inactive for {}", ctx.channel().remoteAddress());
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn("exception serving certificate for {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
