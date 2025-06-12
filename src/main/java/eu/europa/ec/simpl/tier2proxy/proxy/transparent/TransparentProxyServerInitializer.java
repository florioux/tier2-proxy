package eu.europa.ec.simpl.tier2proxy.proxy.transparent;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.handler.TrafficHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.socket.SocketChannel;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class TransparentProxyServerInitializer extends ChannelInitializer<SocketChannel> {
    private final Certificates certificates;
    private final TransparentProxyServerOptions transparentProxyServerOptions;

    @Override
    protected void initChannel(SocketChannel channel) {
        Boolean isTransparent = channel.config().getOption(EpollChannelOption.IP_TRANSPARENT);
        if (isTransparent != null && !isTransparent) {

            throw new IllegalStateException("channel without transparent option");
        }

        Addr dest;
        try {
            dest = OsIntegration.getDestAddr(channel);
        } catch (UnknownHostException e) {
            log.warn("transparent proxy remote address is not a valid address", e);
            return;
        }

        log.debug("initializing pipeline for {}", channel);

        channel.pipeline()
                .addLast(
                        TrafficHandler.class.getCanonicalName(),
                        new TrafficHandler(
                                this.certificates,
                                dest,
                                this.transparentProxyServerOptions.httpObjectAggregatorMaxContentLength()));
        log.debug("pipeline for {} initialized", channel);
    }
}
