package eu.europa.ec.simpl.tier2proxy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

import io.netty.channel.EventLoopGroup;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TeardownPolicyTest {

    @Test
    void testShouldStopServersAndShutdownBossGroup() {
        // given
        var policy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var bossGroup = mock(EventLoopGroup.class);
        var server1 = mock(Server.class);
        var server2 = mock(Server.class);

        given(server1.name()).willReturn("server1");
        given(server2.name()).willReturn("server2");

        // when
        var job = TeardownPolicy.tearDownJob(policy, bossGroup, server1, server2);

        // then
        assertThatCode(job::run).doesNotThrowAnyException();

        then(server1).should().stop(1, 2, TimeUnit.SECONDS);
        then(server2).should().stop(1, 2, TimeUnit.SECONDS);
        then(bossGroup).should().shutdownGracefully(1, 2, TimeUnit.SECONDS);
    }

    @Test
    void testShouldHandleNullServerArray() {
        // given
        var policy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var bossGroup = mock(EventLoopGroup.class);

        // when
        var job = TeardownPolicy.tearDownJob(policy, bossGroup, (Server[]) null);

        // then
        assertThatCode(job::run).doesNotThrowAnyException();
        then(bossGroup).should().shutdownGracefully(1, 2, TimeUnit.SECONDS);
    }

    @Test
    void testShouldHandleNullBossGroup() {
        // given
        var policy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var server = mock(Server.class);
        given(server.name()).willReturn("server");

        // when
        var job = TeardownPolicy.tearDownJob(policy, null, server);

        // then
        assertThatCode(job::run).doesNotThrowAnyException();
        then(server).should().stop(1, 2, TimeUnit.SECONDS);
    }

    @Test
    void testShouldSkipNullServerInstance() {
        // given
        var policy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var bossGroup = mock(EventLoopGroup.class);
        var server1 = mock(Server.class);
        given(server1.name()).willReturn("server1");

        // when
        var job = TeardownPolicy.tearDownJob(policy, bossGroup, server1, null);

        // then
        assertThatCode(job::run).doesNotThrowAnyException();

        then(server1).should().stop(1, 2, TimeUnit.SECONDS);
        then(bossGroup).should().shutdownGracefully(1, 2, TimeUnit.SECONDS);
    }
}
