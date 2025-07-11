package eu.europa.ec.simpl.tier2proxy.proxy.handler;

import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import eu.europa.ec.simpl.tier2proxy.proxy.TLS;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.security.PrivateKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MitmHandlerTest {

    private Addr dest;
    private Certificates certificates;
    private ChannelHandlerContext ctx;
    private ChannelPipeline pipeline;
    private Channel channel;
    private FullHttpRequest request;
    private SslContext sslContext;

    @BeforeEach
    void setUp() {
        dest = mock(Addr.class);
        certificates = mock(Certificates.class);
        ctx = mock(ChannelHandlerContext.class);
        pipeline = mock(ChannelPipeline.class);
        channel = mock(Channel.class);
        request = mock(FullHttpRequest.class);
        sslContext = mock(SslContext.class);
    }

    @Test
    void testHandlerAddedWithTls() throws IOException {
        when(ctx.pipeline()).thenReturn(pipeline);
        when(ctx.channel()).thenReturn(channel);
        when(dest.addr()).thenReturn("localhost");
        when(sslContext.newEngine(any())).thenReturn(mock(javax.net.ssl.SSLEngine.class));
        when(pipeline.addBefore(any(), any(), any())).thenReturn(pipeline);

        CertificateInfo certInfo = mock(CertificateInfo.class);
        when(certificates.certificateFor(anyString())).thenReturn(certInfo);
        when(certInfo.privateKey()).thenReturn(mock(PrivateKey.class));
        when(sslContext.newEngine(any())).thenReturn(mock(javax.net.ssl.SSLEngine.class));

        try (MockedStatic<TLS> tlsMock = mockStatic(TLS.class)) {
            tlsMock.when(() -> TLS.getServerSslContext(any(), any())).thenReturn(sslContext);

            MitmHandler handler = new MitmHandler(certificates, dest, 1024);
            handler.handlerAdded(ctx);

            verify(pipeline, atLeastOnce()).addBefore(any(), eq("io.netty.handler.ssl.SslHandler"), any());
            verify(pipeline, atLeastOnce())
                    .addBefore(any(), eq(io.netty.handler.codec.http.HttpServerCodec.class.getName()), any());
            verify(pipeline, atLeastOnce())
                    .addBefore(any(), eq(io.netty.handler.codec.http.HttpObjectAggregator.class.getName()), any());
        }
    }

    @Test
    void testHandlerAddedWithoutTls() {

        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.addBefore(any(), any(), any())).thenReturn(pipeline);

        MitmHandler handler = new MitmHandler(dest, 1024);
        handler.handlerAdded(ctx);

        verify(pipeline, never()).addBefore(any(), eq("io.netty.handler.ssl.SslHandler"), any());
        verify(pipeline, atLeastOnce())
                .addBefore(any(), eq(io.netty.handler.codec.http.HttpServerCodec.class.getName()), any());
        verify(pipeline, atLeastOnce())
                .addBefore(any(), eq(io.netty.handler.codec.http.HttpObjectAggregator.class.getName()), any());
    }

    @Test
    void testHandlerRemovedWithoutTls() {
        when(pipeline.remove(any(String.class))).thenReturn(mock(ChannelHandler.class));
        when(pipeline.get(anyString())).thenReturn(mock(ChannelHandler.class));
        when(ctx.pipeline()).thenReturn(pipeline);

        MitmHandler handler = new MitmHandler(dest, 1024);
        handler.handlerRemoved(ctx);

        verify(pipeline, never()).remove(SslHandler.class);
        verify(pipeline).get("io.netty.handler.codec.http.HttpServerCodec");
        verify(pipeline).get("io.netty.handler.codec.http.HttpObjectAggregator");
    }
}
