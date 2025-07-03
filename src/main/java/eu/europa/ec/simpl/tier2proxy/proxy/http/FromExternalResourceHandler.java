package eu.europa.ec.simpl.tier2proxy.proxy.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class FromExternalResourceHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private final CompletableFuture<String> future;
    private final Map<HttpResponseStatus, ? extends Throwable> exceptionMap;

    public FromExternalResourceHandler(
            CompletableFuture<String> future, Map<HttpResponseStatus, ? extends Throwable> exceptionMap) {
        this.future = future;
        this.exceptionMap = new HashMap<>(exceptionMap);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
        var body = response.content().toString(CharsetUtil.UTF_8);
        log.debug("Response status: {}", response.status());

        if (isSuccess(response.status())) {
            future.complete(body);
        } else if (exceptionMap.containsKey(response.status())) {
            future.completeExceptionally(exceptionMap.get(response.status()));
        } else {
            future.completeExceptionally(new RuntimeException("Unexpected response from server"));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in handler", cause);
        future.completeExceptionally(cause);
        ctx.close();
    }

    private static boolean isSuccess(HttpResponseStatus status) {
        return status.code() >= HttpResponseStatus.OK.code() && status.code() <= HttpResponseStatus.MULTI_STATUS.code();
    }
}
