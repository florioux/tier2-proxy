package eu.europa.ec.simpl.tier2proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OsTypeTest {

    @Test
    void testFromStrLinux() throws Exception {
        var method = OsType.class.getDeclaredMethod("fromStr", String.class);
        method.setAccessible(true);
        var result = method.invoke(null, "linux");
        assertEquals(OsType.LINUX, result);
    }

    @Test
    void testFromStrWindows() throws Exception {
        var method = OsType.class.getDeclaredMethod("fromStr", String.class);
        method.setAccessible(true);
        var result = method.invoke(null, "windows");
        assertEquals(OsType.WINDOWS, result);
    }

    @Test
    void testFromStrOther() throws Exception {
        var method = OsType.class.getDeclaredMethod("fromStr", String.class);
        method.setAccessible(true);
        var result = method.invoke(null, "macos");
        assertEquals(OsType.OTHER, result);
    }

    @Test
    void testFromStrNull() throws Exception {
        var method = OsType.class.getDeclaredMethod("fromStr", String.class);
        method.setAccessible(true);
        var result = method.invoke(null, (Object) null);
        assertEquals(OsType.UNKNOWN, result);
    }

    @Test
    void testFromStrEmpty() throws Exception {
        var method = OsType.class.getDeclaredMethod("fromStr", String.class);
        method.setAccessible(true);
        var result = method.invoke(null, "");
        assertEquals(OsType.OTHER, result);
    }

    @Test
    void testEventLoopGroupSupplierWindows() {
        var os = OsType.WINDOWS;
        var group = os.eventLoopGroupSupplier();
        assertTrue(group instanceof NioEventLoopGroup);
        group.shutdownGracefully();
    }

    @Test
    void testClientBootstrapSupplierWindows() throws Exception {
        var os = OsType.WINDOWS;
        EventLoopGroup group = new NioEventLoopGroup();
        var bootstrap = os.clientBootstrapSupplier(group).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                // No-op for test
            }
        });
        var channel = bootstrap.register().sync().channel();
        assertEquals(NioSocketChannel.class, channel.getClass());
        channel.close();
        group.shutdownGracefully();
    }

    @Test
    void testServerBootstrapSupplierWindows() throws Exception {
        var os = OsType.WINDOWS;
        EventLoopGroup group = new NioEventLoopGroup();
        var bootstrap = os.serverBootstrapSupplier(false).group(group).childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                // No-op for test
            }
        });
        var channel = bootstrap.register().sync().channel();
        assertEquals(NioServerSocketChannel.class, channel.getClass());
        channel.close();
        group.shutdownGracefully();
    }
}
