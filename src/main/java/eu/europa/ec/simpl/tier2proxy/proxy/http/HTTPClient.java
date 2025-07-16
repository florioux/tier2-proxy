package eu.europa.ec.simpl.tier2proxy.proxy.http;

import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class HTTPClient {

    private static final Configuration configuration = Configuration.getInstance();

    @Getter
    private final CompletableFuture<String> responseFuture = new CompletableFuture<>();

    private final Bootstrap bootstrap;

    public HTTPClient(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void call(HttpRequest httpRequest, Map<HttpResponseStatus, ? extends Throwable> errorMap) {
        bootstrap
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(configuration
                                        .getHttpProtocolServerOptions()
                                        .httpObjectAggregatorMaxContentLength()))
                                .addLast(new FromExternalResourceHandler(responseFuture, errorMap));
                    }
                })
                .connect(httpRequest.getHost(), httpRequest.getPort())
                .addListener((ChannelFutureListener) future -> processConnect(httpRequest, future));
    }

    public void call(HttpRequest httpRequest) {
        call(httpRequest, new HashMap<>());
    }

    private void processConnect(HttpRequest httpRequest, ChannelFuture future) {
        if (future.isSuccess()) {
            log.debug("Connected. Sending request.");
            var request = httpRequest.build();
            log.debug("Request: {}", request);
            future.channel().writeAndFlush(request);

        } else {
            log.error("Failed to sending request");
            responseFuture.completeExceptionally(future.cause());
            future.channel().close();
        }
    }
}
