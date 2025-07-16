package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class FromHTTPHandler extends FromDestinationHandler<FullHttpResponse> {

    FromHTTPHandler(Addr dest, Channel source, ConnectionType connectionType) {
        super(dest, source, connectionType);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {

        log.debug("forward data for {}: {} - {}", this.dest, response.status(), response.headers());
        log.debug("message forwarded {}", response.content().toString(StandardCharsets.UTF_8));
        var retained = response.retainedDuplicate();
        if (source.isActive()) {
            source.writeAndFlush(retained);
        } else {
            log.warn("Channel source is closed, cannot write response");
            retained.release();
        }
    }
}
