package eu.europa.ec.simpl.tier2proxy;

import io.netty.channel.ChannelHandler;

public record HandlerInPipeline<T, S extends ChannelHandler>(T parentHandler, S handler) {

    public String handlerName() {
        return String.format(
                "%s@%s",
                this.parentHandler.getClass().getCanonicalName(),
                handler.getClass().getCanonicalName());
    }
}
