package eu.europa.ec.simpl.tier2proxy;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public record TeardownPolicy(int quietPeriod, int timeout, TimeUnit unit) {
	private static final Logger log = LoggerFactory.getLogger(TeardownPolicy.class);

	static Runnable tearDownJob(TeardownPolicy teardownPolicy, EventLoopGroup bossGroup, Server... servers) {
		return () -> {
			if (log.isInfoEnabled()) {
				log.info("stopping server");
			}

			if (servers != null) {
				for (var aServer : servers) {
					if (aServer != null) {
						if (log.isInfoEnabled()) {
							log.info("stopping server {}", aServer.name());
						}

						aServer.stop(teardownPolicy.quietPeriod(), teardownPolicy.timeout(), teardownPolicy.unit());
						if (log.isInfoEnabled()) {
							log.info("stopped server {}", aServer.name());
						}
					}
				}
			} else if (log.isWarnEnabled()) {
				log.warn("no server to stop");
			}

			if (bossGroup != null) {
				bossGroup.shutdownGracefully(
						teardownPolicy.quietPeriod(), teardownPolicy.timeout(), teardownPolicy.unit());
			} else if (log.isWarnEnabled()) {
				log.warn("boss group is null");
			}

			if (log.isInfoEnabled()) {
				log.info("server shut down");
			}
		};
	}
}
