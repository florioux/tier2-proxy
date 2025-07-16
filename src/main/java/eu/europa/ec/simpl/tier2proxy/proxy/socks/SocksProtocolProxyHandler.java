package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.handler.TrafficHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class SocksProtocolProxyHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private final ChannelHandler socksPortUnificationServerHandler;
    private final Certificates certificates;
    private final SocksProtocolServerOptions socksProtocolServerOptions;

    private SocksVersion socksVersion = SocksVersion.UNKNOWN;

    public SocksProtocolProxyHandler(Certificates certificates, SocksProtocolServerOptions socksProtocolServerOptions) {
        this.socksPortUnificationServerHandler = new SocksPortUnificationServerHandler();
        this.certificates = certificates;
        this.socksProtocolServerOptions = socksProtocolServerOptions;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.debug("adding handler for {}", ctx.channel());

        ctx.pipeline().addBefore(ctx.name(), null, this.socksPortUnificationServerHandler);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.debug("removing handler for {}", ctx.channel());

        ChannelPipeline pipeline = ctx.pipeline();
        switch (this.socksVersion) {
            case SOCKS5 -> {
                pipeline.remove(Socks5ServerEncoder.class);
                pipeline.remove(Socks5CommandRequestDecoder.class);
                pipeline.remove(Socks5InitialRequestDecoder.class);
            }
            case SOCKS4a -> {
                pipeline.remove(Socks4ServerEncoder.class);
                pipeline.remove(Socks4ServerDecoder.class);
            }
            default -> log.warn("No socks handlers to remove for {}", ctx.channel());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage socksMessage) {
        log.debug("reading channel data for {}: {}", ctx.channel(), socksMessage);
        this.socksVersion = socksMessage.version();
        switch (this.socksVersion) {
            case SOCKS4a:
                manageSocks4(ctx, (Socks4CommandRequest) socksMessage);
                break;
            case SOCKS5:
                manageSocks5(ctx, socksMessage);
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }

    private void manageSocks4(ChannelHandlerContext ctx, Socks4CommandRequest socksV4CmdRequest) {
        log.info("socks4 request: {}", socksV4CmdRequest);
        if (socksV4CmdRequest.type() == Socks4CommandType.CONNECT) {
            onSocksSuccess(ctx, socksV4CmdRequest);
        } else {
            ctx.close();
        }
    }

    private void manageSocks5(ChannelHandlerContext ctx, SocksMessage socksMessage) {
        switch (socksMessage) {
            case Socks5InitialRequest request -> manageSocks5InitialRequest(ctx, request);
            case Socks5PasswordAuthRequest request -> manageSocks5PasswordAuthRequest(ctx, request);
            case Socks5CommandRequest request -> manageSocks5CommandRequest(ctx, request);
            default -> ctx.close();
        }
    }

    private void manageSocks5CommandRequest(ChannelHandlerContext ctx, Socks5CommandRequest socks5CmdRequest) {
        log.info("sock5 command request: {}", socks5CmdRequest);
        if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
            onSocksSuccess(ctx, socks5CmdRequest);
        } else {
            ctx.close();
        }
    }

    private static void manageSocks5PasswordAuthRequest(
            ChannelHandlerContext ctx, Socks5PasswordAuthRequest socks5PasswordAuthRequest) {
        log.info("socks password authentication request: {}", socks5PasswordAuthRequest);
        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
        ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
    }

    private static void manageSocks5InitialRequest(
            ChannelHandlerContext ctx, Socks5InitialRequest socks5InitialRequest) {
        log.info("socks5 initial request: {}", socks5InitialRequest);
        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
    }

    private void onSocksSuccess(ChannelHandlerContext ctx, Socks4CommandRequest request) {
        var dest = new Addr(request.dstAddr(), request.dstPort());

        ctx.writeAndFlush(
                new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS, request.dstAddr(), request.dstPort()));

        onServerConnected(ctx, dest);
    }

    private void onSocksSuccess(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        var dest = new Addr(request.dstAddr(), request.dstPort());

        ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, request.dstAddrType(), request.dstAddr(), request.dstPort()));

        onServerConnected(ctx, dest);
    }

    private void onServerConnected(ChannelHandlerContext ctx, Addr dest) {
        var handler = new TrafficHandler(
                this.certificates, dest, this.socksProtocolServerOptions.httpObjectAggregatorMaxContentLength());

        ctx.pipeline().replace(this, TrafficHandler.class.getCanonicalName(), handler);
    }
}
