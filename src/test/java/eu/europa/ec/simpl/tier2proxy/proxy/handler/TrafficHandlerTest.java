package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrafficHandlerTest {

    @Mock
    Certificates certificates;

    @Mock
    Addr dest;

    @Mock
    ChannelHandlerContext ctx;

    @Mock
    ChannelPipeline pipeline;

    final int maxContentLength = 1024;

    @Test
    void testIsTLSReturnsTrueForTLSBytes() {
        var buf = Unpooled.buffer();
        buf.writeByte(TrafficHandler.EXPECTED_FIRST_TLS_BYTE);
        buf.writeByte(TrafficHandler.EXPECTED_SECOND_TLS_BYTE);
        buf.writeByte(0x01);
        assertTrue(invokeIsTLS(buf), "isTLS deve restituire true per i byte TLS validi");
    }

    @Test
    void testIsTLSReturnsFalseForNonTLSBytes() {
        var buf = Unpooled.buffer();
        buf.writeByte(0x01);
        buf.writeByte(0x02);
        assertFalse(invokeIsTLS(buf), "isTLS deve restituire false per byte non TLS");
    }

    private static boolean invokeIsTLS(ByteBuf buf) {
        try {
            var m = TrafficHandler.class.getDeclaredMethod("isTLS", ByteBuf.class);
            m.setAccessible(true);
            return (boolean) m.invoke(null, buf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
