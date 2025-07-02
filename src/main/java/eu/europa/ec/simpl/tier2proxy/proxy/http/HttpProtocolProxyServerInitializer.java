package eu.europa.ec.simpl.tier2proxy.proxy.http;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class HttpProtocolProxyServerInitializer extends ChannelInitializer<Channel> {
    private final HttpProtocolServerOptions httpProtocolServerOptions;
    private final Certificates certificates;

    @Override
    protected void initChannel(Channel channel) {
        log.debug("initializing pipeline for {}", channel);

        var handler = new HttpProxyHandler(httpProtocolServerOptions, certificates);
        channel.pipeline().addLast(HttpProxyHandler.class.getCanonicalName(), handler);
        log.debug("pipeline for {} initialized", channel);
    }
}
