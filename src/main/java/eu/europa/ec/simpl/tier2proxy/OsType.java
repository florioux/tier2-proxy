package eu.europa.ec.simpl.tier2proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum OsType {
    LINUX("linux"),
    WINDOWS("windows"),
    OTHER(""),
    UNKNOWN(null);

    private final String type;

    OsType(String type) {
        this.type = type;
    }

    EventLoopGroup bossGroup(int threadNum, Executor executor) {
        if (this.equals(LINUX)) {
            return new EpollEventLoopGroup(threadNum, executor);
        }

        return new NioEventLoopGroup(threadNum, executor);
    }

    public EventLoopGroup eventLoopGroupSupplier() {
        if (this.equals(LINUX)) {
            return new EpollEventLoopGroup();
        }

        return new NioEventLoopGroup();
    }

    public ServerBootstrap serverBootstrapSupplier(boolean isTransparent) {
        if (this.equals(LINUX)) {
            ServerBootstrap toReturn = new ServerBootstrap()
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .channel(EpollServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            if (isTransparent) {
                toReturn = toReturn.option(EpollChannelOption.IP_TRANSPARENT, true)
                        .childOption(EpollChannelOption.IP_TRANSPARENT, true);
            }
            return toReturn;
        }

        return new ServerBootstrap()
                .option(ChannelOption.SO_BACKLOG, 1024)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
    }

    static OsType fromOS() {
        if (log.isDebugEnabled()) {
            log.debug("checking os type");
        }
        String os = System.getProperty("os.name");
        if (log.isDebugEnabled()) {
            log.debug("os type is {}", os);
        }
        return fromStr(os);
    }

    private static OsType fromStr(String type) {
        if (type != null) {
            String lowerCasedType = type.toLowerCase();
            if (lowerCasedType.startsWith(LINUX.type)) {
                return LINUX;
            } else if (lowerCasedType.startsWith(WINDOWS.type)) {
                return WINDOWS;
            } else {
                return OTHER;
            }
        }
        return UNKNOWN;
    }
}
