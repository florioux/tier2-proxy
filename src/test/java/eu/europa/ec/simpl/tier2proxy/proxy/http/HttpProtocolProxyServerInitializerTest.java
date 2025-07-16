package eu.europa.ec.simpl.tier2proxy.proxy.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpProtocolProxyServerInitializerTest {

    @Mock
    HttpProtocolServerOptions httpProtocolServerOptions;

    @Mock
    Certificates certificates;

    @Mock
    Channel channel;

    @Mock
    ChannelPipeline pipeline;

    @InjectMocks
    HttpProtocolProxyServerInitializer initializer;

    @BeforeEach
    void setUp() {
        when(channel.pipeline()).thenReturn(pipeline);
        initializer = new HttpProtocolProxyServerInitializer(httpProtocolServerOptions, certificates);
    }

    @Test
    void testInitChannelAddsHttpProxyHandlerToPipeline() {
        // Act
        initializer.initChannel(channel);

        // Assert
        var handlerCaptor = ArgumentCaptor.forClass(HttpProxyHandler.class);
        verify(pipeline).addLast(eq(HttpProxyHandler.class.getCanonicalName()), handlerCaptor.capture());
        var handler = handlerCaptor.getValue();
        assertNotNull(handler);
        assertTrue(handler instanceof HttpProxyHandler);
    }
}
