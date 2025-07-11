package eu.europa.ec.simpl.tier2proxy.proxy.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HTTPClientTest {

    @Mock
    Bootstrap bootstrap;

    @Mock
    ChannelFuture channelFuture;

    @Mock
    Channel channel;

    @Mock
    HttpRequest httpRequest;

    HTTPClient client;

    @BeforeEach
    void setUp() {
        client = new HTTPClient(bootstrap);
    }

    @Test
    void testCallWithErrorMapSetsPipelineAndConnects() {
        var errorMap = new HashMap<HttpResponseStatus, Throwable>();
        when(bootstrap.handler(any(ChannelInitializer.class))).thenReturn(bootstrap);
        when(bootstrap.connect(anyString(), anyInt())).thenReturn(channelFuture);
        when(httpRequest.getHost()).thenReturn("localhost");
        when(httpRequest.getPort()).thenReturn(8080);

        client.call(httpRequest, errorMap);

        verify(bootstrap).handler(any(ChannelInitializer.class));
        verify(bootstrap).connect("localhost", 8080);
    }

    @Test
    void testCallWithoutErrorMapUsesEmptyMap() {
        when(bootstrap.handler(any(ChannelInitializer.class))).thenReturn(bootstrap);
        when(bootstrap.connect(anyString(), anyInt())).thenReturn(channelFuture);
        when(httpRequest.getHost()).thenReturn("localhost");
        when(httpRequest.getPort()).thenReturn(8080);

        client.call(httpRequest);

        verify(bootstrap).handler(any(ChannelInitializer.class));
        verify(bootstrap).connect("localhost", 8080);
    }

    @Test
    void testProcessConnectSuccessSendsRequest() throws Exception {
        var future = mock(ChannelFuture.class);
        var channel = mock(Channel.class);
        when(future.isSuccess()).thenReturn(true);
        when(future.channel()).thenReturn(channel);
        var fullHttpRequest = mock(FullHttpRequest.class);
        when(httpRequest.build()).thenReturn(fullHttpRequest);

        var method = HTTPClient.class.getDeclaredMethod("processConnect", HttpRequest.class, ChannelFuture.class);
        method.setAccessible(true);
        method.invoke(client, httpRequest, future);

        verify(channel).writeAndFlush(fullHttpRequest);
    }

    @Test
    void testProcessConnectFailureCompletesFutureExceptionally() throws Exception {
        var future = mock(ChannelFuture.class);
        when(future.isSuccess()).thenReturn(false);
        when(future.cause()).thenReturn(new RuntimeException("fail"));
        when(future.channel()).thenReturn(mock(Channel.class));

        var responseFuture = client.getResponseFuture();
        var method = HTTPClient.class.getDeclaredMethod("processConnect", HttpRequest.class, ChannelFuture.class);
        method.setAccessible(true);
        method.invoke(client, httpRequest, future);

        assertTrue(responseFuture.isCompletedExceptionally());
    }

    @Test
    void testGetResponseFutureReturnsCompletableFuture() {
        var future = client.getResponseFuture();
        assertNotNull(future);
        assertTrue(future instanceof CompletableFuture);
    }
}
