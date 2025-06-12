package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class SocksProtocolProxyServerInitializer extends ChannelInitializer<Channel> {
    private final Certificates certificates;
    private final SocksProtocolServerOptions socksProtocolServerOptions;

    @Override
    protected void initChannel(Channel channel) {
        log.debug("initializing pipeline for {}", channel);

        channel.pipeline()
                .addLast(
                        SocksProtocolProxyHandler.class.getCanonicalName(),
                        new SocksProtocolProxyHandler(this.certificates, this.socksProtocolServerOptions));
        log.debug("pipeline for {} initialized", channel);
    }
}
