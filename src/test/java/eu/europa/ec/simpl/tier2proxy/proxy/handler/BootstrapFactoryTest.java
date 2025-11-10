package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BootstrapFactoryTest {

    @Mock
    ChannelHandlerContext ctx;

    @Mock
    Channel channel;

    @Mock
    EventLoop eventLoop;

    @InjectMocks
    BootstrapFactory bootstrapFactory;

    @Test
    void testGetReturnsBootstrapWithCorrectGroupAndChannel() {
        given(ctx.channel()).willReturn(channel);
        given(channel.eventLoop()).willReturn(eventLoop);

        var bootstrap = bootstrapFactory.get(ctx);

        assertThat(bootstrap.config().group()).isEqualTo(eventLoop);
    }
}
