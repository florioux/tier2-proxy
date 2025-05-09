package eu.europa.ec.simpl.tier2proxy.proxy.transparent;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class OsIntegration {
    interface LibC extends Library {
        int SOL_IP = 0;
        int SO_ORIGINAL_DST = 80;

        int getsockopt(int fd, int level, int optname, ByteBuffer optval, IntByReference optlen);
    }

    private static final LibC INSTANCE = Native.load("c", LibC.class);

    private static int socketFileDescriptor(SocketChannel channel) {
        if (channel instanceof EpollSocketChannel) {
            return ((EpollSocketChannel) channel).fd().intValue();
        } else {
            throw new IllegalStateException("no other socket channel are supported");
        }
    }

    private static InetSocketAddress destinationAddr(int fd) throws UnknownHostException {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
        IntByReference size = new IntByReference(16);

        int result = INSTANCE.getsockopt(fd, LibC.SOL_IP, LibC.SO_ORIGINAL_DST, buffer, size);
        if (result != 0) {
            throw new IllegalStateException("failed to load ");
        }

        int port = Short.toUnsignedInt(buffer.order(ByteOrder.BIG_ENDIAN).getShort(2));

        byte[] ipBytes = new byte[4];
        buffer.order(ByteOrder.nativeOrder()).position(4); // native order for IP addr
        buffer.get(ipBytes);

        return new InetSocketAddress(InetAddress.getByAddress(ipBytes), port);
    }

    public static Addr getDestAddr(SocketChannel nettyChannel) throws UnknownHostException {
        int fd = socketFileDescriptor(nettyChannel);
        InetSocketAddress inetSocketAddress = destinationAddr(fd);

        String hostAddress = inetSocketAddress.getAddress().getHostAddress();
        return new Addr(hostAddress, inetSocketAddress.getPort());
    }
}
