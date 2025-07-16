package eu.europa.ec.simpl.tier2proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerTest {

    @Mock
    ServerConfig mockConfig;

    @Mock
    ChannelFuture mockChannelFuture;

    @Mock
    Channel mockChannel;

    static class TestServer extends Server {
        private final ServerConfig config;
        private final ChannelFuture channelFuture;

        TestServer(ServerConfig config, ChannelFuture channelFuture) {
            this.config = config;
            this.channelFuture = channelFuture;
        }

        @Override
        public String name() {
            return "testServer";
        }

        @Override
        public void stop(long quietPeriod, long timeout, TimeUnit unit) {
            // No operation for test purposes
        }

        @Override
        protected ChannelFuture doStart() {
            return channelFuture;
        }

        @Override
        protected ServerConfig config() {
            return config;
        }
    }

    @Test
    void testNameReturnsCorrectValue() {
        var server = new TestServer(mockConfig, mockChannelFuture);
        var result = server.name();
        assertEquals("testServer", result);
    }

    @Test
    void testStartReturnsCloseFuture() {
        when(mockConfig.bindAddr()).thenReturn("localhost");
        when(mockConfig.bindPort()).thenReturn(8080);
        when(mockChannelFuture.addListener(any())).thenAnswer(invocation -> {
            var listener = (GenericFutureListener<Future<? super Void>>) invocation.getArgument(0);
            // Simulate success
            var future = mock(ChannelFuture.class);
            when(future.isSuccess()).thenReturn(true);
            listener.operationComplete(future);
            return mockChannelFuture;
        });
        when(mockChannelFuture.channel()).thenReturn(mockChannel);
        when(mockChannel.closeFuture()).thenReturn(mockChannelFuture);

        var server = new TestServer(mockConfig, mockChannelFuture);
        var result = server.start();
        assertNotNull(result);
        assertEquals(mockChannelFuture, result);
    }

    @Test
    void testStopDoesNotThrow() {
        var server = new TestServer(mockConfig, mockChannelFuture);
        assertDoesNotThrow(() -> server.stop(1L, 2L, TimeUnit.SECONDS));
    }
}
