package eu.europa.ec.simpl.tier2proxy;

import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class Server {
	public abstract  String name();
	public abstract void stop(long quietPeriod, long timeout, TimeUnit unit);
	protected abstract ChannelFuture doStart();
	protected abstract ServerConfig config();

	public final ChannelFuture start() {
		ServerConfig config = config();
		return this.doStart()
				.addListener(future -> {
					if (future.isSuccess()) {
						if(log.isInfoEnabled()) {
							log.info("server {}:{} started", config.bindAddr(), config.bindPort());
						}
					}
				})
				.channel()
				.closeFuture();
	}
}
