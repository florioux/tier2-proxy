package eu.europa.ec.simpl.tier2proxy.proxy.http;

import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.util.CharsetUtil;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class HTTPClient {

    private static final Configuration configuration = Configuration.getInstance();

    @Getter
    private final CompletableFuture<String> responseFuture = new CompletableFuture<>();

    private final Bootstrap bootstrap;

    public HTTPClient(Bootstrap bootstrap) {
        this.bootstrap = bootstrap.handler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline()
                        .addLast(new HttpClientCodec())
                        .addLast(new HttpObjectAggregator(
                                configuration.getHttpProtocolServerOptions().httpObjectAggregatorMaxContentLength()))
                        .addLast(new FromExternalResourceHandler(responseFuture));
            }
        });
    }

    public void call(HttpRequest httpRequest, Consumer<ChannelFuture> onError) {
        bootstrap.connect(httpRequest.getHost(), httpRequest.getPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.debug("Connected. Sending request.");
                var request = httpRequest.build();
                log.debug("Request: {}", request);
                future.channel().writeAndFlush(request);

            } else {
                onError.accept(future);
            }
        });
    }

    public void call(HttpRequest httpRequest) {
        call(httpRequest, future -> {
            log.error("Connection failed", future.cause());
            responseFuture.completeExceptionally(future.cause());
        });
    }

    @Slf4j
    private static class FromExternalResourceHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        private final CompletableFuture<String> future;

        public FromExternalResourceHandler(CompletableFuture<String> future) {
            this.future = future;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
            var body = response.content().toString(CharsetUtil.UTF_8);
            log.debug("Response status: {}", response.status());

            future.complete(body);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Exception in handler", cause);
            future.completeExceptionally(cause);
            ctx.close();
        }
    }
}
