package eu.europa.ec.simpl.tier2proxy.proxy.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpProxyHandlerTest {

    @Mock
    ChannelHandlerContext ctx;

    @Mock
    FullHttpRequest request;

    @Mock
    HttpProtocolServerOptions options;

    @Mock
    Certificates certificates;

    HttpProxyHandler handler;

    @BeforeEach
    void setup() {
        handler = new HttpProxyHandler(options, certificates);
    }

    @Test
    void testHandlerAddedAddsHandlersToPipeline() {
        var pipeline = mock(io.netty.channel.ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(ctx.name()).thenReturn("handlerName");
        when(pipeline.addBefore(anyString(), anyString(), any())).thenReturn(pipeline);

        handler.handlerAdded(ctx);

        verify(pipeline, times(2)).addBefore(anyString(), anyString(), any());
    }

    @Test
    void testHandlerRemovedRemovesHandlersFromPipeline() {
        var pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.remove(any(ChannelHandler.class))).thenReturn(pipeline);

        handler.handlerRemoved(ctx);

        verify(pipeline, times(2)).remove(any(ChannelHandler.class));
    }

    @Test
    void testChannelRead0PlainMethodReplacesHandlerAndFiresChannelRead() throws Exception {
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.uri()).thenReturn("http://example.com");
        var pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.replace(any(ChannelHandler.class), anyString(), any(ChannelHandler.class)))
                .thenReturn(pipeline);
        when(pipeline.fireChannelRead(any())).thenReturn(pipeline);

        handler.channelRead0(ctx, request);

        verify(pipeline).replace(any(ChannelHandler.class), anyString(), any(ChannelHandler.class));
        verify(pipeline).fireChannelRead(any());
    }
}
