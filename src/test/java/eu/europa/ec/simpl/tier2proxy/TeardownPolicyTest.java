package eu.europa.ec.simpl.tier2proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.EventLoopGroup;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeardownPolicyTest {

    @Test
    void testRecordFields() {
        var quietPeriod = 1;
        var timeout = 2;
        var unit = TimeUnit.SECONDS;
        var teardownPolicy = new TeardownPolicy(quietPeriod, timeout, unit);

        assertEquals(quietPeriod, teardownPolicy.quietPeriod());
        assertEquals(timeout, teardownPolicy.timeout());
        assertEquals(unit, teardownPolicy.unit());
    }

    @Test
    void testEqualsAndHashCode() {
        var teardownPolicy1 = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var teardownPolicy2 = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var teardownPolicy3 = new TeardownPolicy(2, 3, TimeUnit.MINUTES);

        assertEquals(teardownPolicy1, teardownPolicy2);
        assertEquals(teardownPolicy1.hashCode(), teardownPolicy2.hashCode());
        assertNotEquals(teardownPolicy1, teardownPolicy3);
    }

    @Test
    void testToString() {
        var teardownPolicy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var str = teardownPolicy.toString();
        assertTrue(str.contains("1"));
        assertTrue(str.contains("2"));
        assertTrue(str.contains("SECONDS"));
    }

    @Test
    void testDoTearDownInvokesServerAndBossGroup() throws Exception {
        var teardownPolicy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var bossGroup = mock(EventLoopGroup.class);
        var server = mock(Server.class);
        when(server.name()).thenReturn("mockServer");

        // Use reflection to invoke private static method
        var method = TeardownPolicy.class.getDeclaredMethod(
                "doTearDown", TeardownPolicy.class, EventLoopGroup.class, Server[].class);
        method.setAccessible(true);
        method.invoke(null, teardownPolicy, bossGroup, new Server[] {server});

        verify(server).stop(1, 2, TimeUnit.SECONDS);
        verify(bossGroup).shutdownGracefully(1, 2, TimeUnit.SECONDS);
    }

    @Test
    void testDoTearDownWithNullServersLogsWarning() throws Exception {
        var teardownPolicy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var bossGroup = mock(EventLoopGroup.class);
        // servers = null
        var method = TeardownPolicy.class.getDeclaredMethod(
                "doTearDown", TeardownPolicy.class, EventLoopGroup.class, Server[].class);
        method.setAccessible(true);
        method.invoke(null, teardownPolicy, bossGroup, (Object) null);
        // No exception = pass. Log warning is not asserted here.
        verify(bossGroup).shutdownGracefully(1, 2, TimeUnit.SECONDS);
    }

    @Test
    void testDoTearDownWithNullBossGroupLogsWarning() throws Exception {
        var teardownPolicy = new TeardownPolicy(1, 2, TimeUnit.SECONDS);
        var server = mock(Server.class);
        when(server.name()).thenReturn("mockServer");
        var method = TeardownPolicy.class.getDeclaredMethod(
                "doTearDown", TeardownPolicy.class, EventLoopGroup.class, Server[].class);
        method.setAccessible(true);
        method.invoke(null, teardownPolicy, null, new Server[] {server});
        verify(server).stop(1, 2, TimeUnit.SECONDS);
        // No exception = pass. Log warning is not asserted here.
    }
}
