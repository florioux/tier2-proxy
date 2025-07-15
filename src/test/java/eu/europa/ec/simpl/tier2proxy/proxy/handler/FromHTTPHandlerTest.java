package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static eu.europa.ec.simpl.tier2proxy.enums.ConnectionType.HTTP;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FromHTTPHandlerTest {

    @Mock
    Addr dest;

    @Mock
    Channel source;

    @Mock
    ChannelHandlerContext ctx;

    @Mock
    FullHttpResponse response;

    @Mock
    io.netty.buffer.ByteBuf content;

    FromHTTPHandler handler;

    @BeforeEach
    void setUp() {
        var connectionType = HTTP;
        handler = new FromHTTPHandler(dest, source, connectionType);
    }

    @Test
    void testChannelRead0SourceActive() {
        var retained = mock(FullHttpResponse.class);
        when(response.retainedDuplicate()).thenReturn(retained);
        when(source.isActive()).thenReturn(true);
        when(response.content()).thenReturn(content);
        when(content.toString(java.nio.charset.StandardCharsets.UTF_8)).thenReturn("body");

        handler.channelRead0(ctx, response);

        verify(source).writeAndFlush(retained);
        verify(retained, never()).release();
    }

    @Test
    void testChannelRead0SourceInactive() {
        var retained = mock(FullHttpResponse.class);
        when(response.retainedDuplicate()).thenReturn(retained);
        when(source.isActive()).thenReturn(false);
        when(response.content()).thenReturn(content);
        when(content.toString(java.nio.charset.StandardCharsets.UTF_8)).thenReturn("body");

        handler.channelRead0(ctx, response);

        verify(source, never()).writeAndFlush(any());
        verify(retained).release();
    }
}
