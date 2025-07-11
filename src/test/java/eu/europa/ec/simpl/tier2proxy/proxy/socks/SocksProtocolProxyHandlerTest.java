package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import static io.netty.handler.codec.socksx.SocksVersion.SOCKS4a;
import static io.netty.handler.codec.socksx.SocksVersion.SOCKS5;
import static io.netty.handler.codec.socksx.SocksVersion.UNKNOWN;
import static io.netty.handler.codec.socksx.v5.Socks5AddressType.DOMAIN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.handler.TrafficHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocksProtocolProxyHandlerTest {

    @Mock
    private Certificates certificates;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelPipeline pipeline;

    @Mock
    private Channel channel;

    private SocksProtocolProxyHandler handler;
    private SocksProtocolServerOptions socksProtocolServerOptions;
    private final int maxContentLength = 65536;

    @BeforeEach
    void setUp() {
        var serverConfig = new ServerConfig(true, "localhost", 8080);
        socksProtocolServerOptions = new SocksProtocolServerOptions(serverConfig, maxContentLength);
        handler = new SocksProtocolProxyHandler(certificates, socksProtocolServerOptions);

        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    void testHandlerAdded() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        when(ctx.name()).thenReturn("testHandler");

        // When
        handler.handlerAdded(ctx);

        // Then
        verify(pipeline).addBefore(eq("testHandler"), eq(null), any(SocksPortUnificationServerHandler.class));
    }

    @Test
    void testHandlerRemovedWithSocks5() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        var socks5CommandRequest = mock(Socks5CommandRequest.class);
        when(socks5CommandRequest.type()).thenReturn(Socks5CommandType.CONNECT);
        when(socks5CommandRequest.dstAddr()).thenReturn("example.com");
        when(socks5CommandRequest.dstPort()).thenReturn(443);
        when(socks5CommandRequest.dstAddrType()).thenReturn(DOMAIN);
        when(socks5CommandRequest.version()).thenReturn(SOCKS5);

        handler.channelRead0(ctx, socks5CommandRequest);
        handler.handlerRemoved(ctx);

        // Verify
        verify(pipeline).remove(Socks5ServerEncoder.class);
        verify(pipeline).remove(Socks5CommandRequestDecoder.class);
        verify(pipeline).remove(Socks5InitialRequestDecoder.class);
    }

    @Test
    void testHandlerRemovedWithSocks4() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        var socks4CommandRequest = mock(Socks4CommandRequest.class);
        when(socks4CommandRequest.type()).thenReturn(Socks4CommandType.CONNECT);
        when(socks4CommandRequest.dstAddr()).thenReturn("127.0.0.1");
        when(socks4CommandRequest.dstPort()).thenReturn(443);
        when(socks4CommandRequest.version()).thenReturn(SOCKS4a);

        handler.channelRead0(ctx, socks4CommandRequest);
        handler.handlerRemoved(ctx);

        // Verify
        verify(pipeline).remove(Socks4ServerEncoder.class);
        verify(pipeline).remove(Socks4ServerDecoder.class);
    }

    @Test
    void testHandlerRemovedWithUnknownVersion() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        handler.handlerRemoved(ctx);

        // Verify - no removals should happen
        verify(pipeline, times(0)).remove(any(Class.class));
    }

    @Test
    void testChannelReadWithSocks4Connect() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        var socks4CommandRequest = mock(Socks4CommandRequest.class);
        when(socks4CommandRequest.type()).thenReturn(Socks4CommandType.CONNECT);
        when(socks4CommandRequest.dstAddr()).thenReturn("127.0.0.1");
        when(socks4CommandRequest.dstPort()).thenReturn(443);
        when(socks4CommandRequest.version()).thenReturn(SOCKS4a);

        // When
        handler.channelRead0(ctx, socks4CommandRequest);

        // Then
        verify(ctx).writeAndFlush(any(DefaultSocks4CommandResponse.class));
        verify(pipeline).replace(eq(handler), eq(TrafficHandler.class.getCanonicalName()), any(TrafficHandler.class));
    }

    @Test
    void testChannelReadWithSocks4NonConnect() {
        // Given
        var socks4CommandRequest = mock(Socks4CommandRequest.class);
        when(socks4CommandRequest.type()).thenReturn(Socks4CommandType.BIND);
        when(socks4CommandRequest.version()).thenReturn(SOCKS4a);

        // When
        handler.channelRead0(ctx, socks4CommandRequest);

        // Then
        verify(ctx).close();
    }

    @Test
    void testChannelReadWithSocks5InitialRequest() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        var socks5InitialRequest = mock(Socks5InitialRequest.class);
        when(socks5InitialRequest.version()).thenReturn(SOCKS5);

        // When
        handler.channelRead0(ctx, socks5InitialRequest);

        // Then
        verify(pipeline).addFirst(any(Socks5CommandRequestDecoder.class));
        verify(ctx).writeAndFlush(any(DefaultSocks5InitialResponse.class));
    }

    @Test
    void testChannelReadWithSocks5PasswordAuthRequest() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        var socks5PasswordAuthRequest = mock(Socks5PasswordAuthRequest.class);
        when(socks5PasswordAuthRequest.version()).thenReturn(SOCKS5);

        // When
        handler.channelRead0(ctx, socks5PasswordAuthRequest);

        // Then
        verify(pipeline).addFirst(any(Socks5CommandRequestDecoder.class));
        verify(ctx).writeAndFlush(any(DefaultSocks5PasswordAuthResponse.class));
    }

    @Test
    void testChannelReadWithSocks5CommandRequestConnect() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        var socks5CommandRequest = mock(Socks5CommandRequest.class);
        when(socks5CommandRequest.type()).thenReturn(Socks5CommandType.CONNECT);
        when(socks5CommandRequest.dstAddr()).thenReturn("example.com");
        when(socks5CommandRequest.dstPort()).thenReturn(443);
        when(socks5CommandRequest.dstAddrType()).thenReturn(DOMAIN);
        when(socks5CommandRequest.version()).thenReturn(SOCKS5);

        // When
        handler.channelRead0(ctx, socks5CommandRequest);

        // Then
        verify(ctx).writeAndFlush(any(DefaultSocks5CommandResponse.class));
        verify(pipeline).replace(eq(handler), eq(TrafficHandler.class.getCanonicalName()), any(TrafficHandler.class));
    }

    @Test
    void testChannelReadWithSocks5CommandRequestNonConnect() {
        // Given
        var socks5CommandRequest = mock(Socks5CommandRequest.class);
        when(socks5CommandRequest.type()).thenReturn(Socks5CommandType.BIND); // Non-CONNECT type
        when(socks5CommandRequest.version()).thenReturn(SOCKS5);

        // When
        handler.channelRead0(ctx, socks5CommandRequest);

        // Then
        verify(ctx).close();
    }

    @Test
    void testChannelReadWithUnknownVersion() {
        // Given
        var unknownSocksMessage = mock(io.netty.handler.codec.socksx.SocksMessage.class);
        when(unknownSocksMessage.version()).thenReturn(UNKNOWN);

        // When
        handler.channelRead0(ctx, unknownSocksMessage);

        // Then
        verify(ctx).close();
    }

    @Test
    void testOnServerConnectedCreatesTrafficHandler() {
        // Given
        when(ctx.pipeline()).thenReturn(pipeline);
        var socks5CommandRequest = mock(Socks5CommandRequest.class);
        when(socks5CommandRequest.type()).thenReturn(Socks5CommandType.CONNECT);
        when(socks5CommandRequest.dstAddr()).thenReturn("example.com");
        when(socks5CommandRequest.dstPort()).thenReturn(443);
        when(socks5CommandRequest.dstAddrType()).thenReturn(DOMAIN);
        when(socks5CommandRequest.version()).thenReturn(SOCKS5);

        // When
        handler.channelRead0(ctx, socks5CommandRequest);

        // Then
        verify(pipeline).replace(eq(handler), eq(TrafficHandler.class.getCanonicalName()), any(TrafficHandler.class));
    }
}
