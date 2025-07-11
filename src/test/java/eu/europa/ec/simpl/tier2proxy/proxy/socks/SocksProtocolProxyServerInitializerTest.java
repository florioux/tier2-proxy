package eu.europa.ec.simpl.tier2proxy.proxy.socks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocksProtocolProxyServerInitializerTest {
    @Mock
    Certificates certificates;

    @Mock
    SocksProtocolServerOptions socksProtocolServerOptions;

    @Mock
    Channel channel;

    @Mock
    ChannelPipeline pipeline;

    @Test
    void testInitChannelAddsHandlerToPipeline() {
        // Arrange
        when(channel.pipeline()).thenReturn(pipeline);
        var initializer = new SocksProtocolProxyServerInitializer(certificates, socksProtocolServerOptions);

        // Act
        initializer.initChannel(channel);

        // Assert
        var handlerCaptor = ArgumentCaptor.forClass(SocksProtocolProxyHandler.class);
        verify(pipeline).addLast(eq(SocksProtocolProxyHandler.class.getCanonicalName()), handlerCaptor.capture());
        var handler = handlerCaptor.getValue();
        assertNotNull(handler);
        assertTrue(handler instanceof SocksProtocolProxyHandler);
    }
}
