package eu.europa.ec.simpl.tier2proxy.proxy.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FromExternalResourceHandlerTest {
    @Mock
    ChannelHandlerContext ctx;

    @Mock
    FullHttpResponse response;

    @Test
    void testChannelRead0Success() {
        var future = new CompletableFuture<String>();
        var exceptionMap = Map.of(HttpResponseStatus.BAD_REQUEST, new IllegalArgumentException("bad request"));
        var handler = new FromExternalResourceHandler(future, exceptionMap);

        when(response.status()).thenReturn(HttpResponseStatus.OK);
        var content = mock(io.netty.buffer.ByteBuf.class);
        when(response.content()).thenReturn(content);
        when(content.toString(CharsetUtil.UTF_8)).thenReturn("success-body");

        handler.channelRead0(ctx, response);
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertEquals("success-body", future.join());
    }

    @Test
    void testChannelRead0KnownException() {
        var future = new CompletableFuture<String>();
        var exception = new IllegalArgumentException("bad request");
        var exceptionMap = Map.of(HttpResponseStatus.BAD_REQUEST, exception);
        var handler = new FromExternalResourceHandler(future, exceptionMap);

        when(response.status()).thenReturn(HttpResponseStatus.BAD_REQUEST);
        var content = mock(io.netty.buffer.ByteBuf.class);
        when(response.content()).thenReturn(content);
        when(content.toString(CharsetUtil.UTF_8)).thenReturn("error-body");

        handler.channelRead0(ctx, response);
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
        try {
            future.join();
            fail("Expected exception");
        } catch (Exception ex) {
            assertSame(exception, ex.getCause());
        }
    }

    @Test
    void testChannelRead0UnknownException() {
        var future = new CompletableFuture<String>();
        var handler = new FromExternalResourceHandler(future, Map.of());

        when(response.status()).thenReturn(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        var content = mock(io.netty.buffer.ByteBuf.class);
        when(response.content()).thenReturn(content);
        when(content.toString(CharsetUtil.UTF_8)).thenReturn("error-body");

        handler.channelRead0(ctx, response);
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
        try {
            future.join();
            fail("Expected exception");
        } catch (Exception ex) {
            assertTrue(ex.getCause() instanceof RuntimeException);
            assertEquals("Unexpected response from server", ex.getCause().getMessage());
        }
    }

    @Test
    void testExceptionCaught() {
        var future = new CompletableFuture<String>();
        var handler = new FromExternalResourceHandler(future, Map.of());
        var cause = new RuntimeException("handler error");

        handler.exceptionCaught(ctx, cause);
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
        try {
            future.join();
            fail("Expected exception");
        } catch (Exception ex) {
            assertSame(cause, ex.getCause());
        }
        verify(ctx).close();
    }
}
