package eu.europa.ec.simpl.tier2proxy;

import io.netty.channel.EventLoopGroup;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record TeardownPolicy(int quietPeriod, int timeout, TimeUnit unit) {

    static Runnable tearDownJob(TeardownPolicy teardownPolicy, EventLoopGroup bossGroup, Server... servers) {
        return () -> doTearDown(teardownPolicy, bossGroup, servers);
    }

    private static void doTearDown(TeardownPolicy teardownPolicy, EventLoopGroup bossGroup, Server... servers) {
        log.info("stopping server");

        if (servers != null) {
            for (var aServer : servers) {
                if (aServer != null) {
                    log.info("stopping server {}", aServer.name());

                    aServer.stop(teardownPolicy.quietPeriod(), teardownPolicy.timeout(), teardownPolicy.unit());
                    log.info("stopped server {}", aServer.name());
                }
            }
        } else {
            log.warn("no server to stop");
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully(teardownPolicy.quietPeriod(), teardownPolicy.timeout(), teardownPolicy.unit());
        } else {
            log.warn("boss group is null");
        }

        log.info("server shut down");
    }
}
