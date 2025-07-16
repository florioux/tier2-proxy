package eu.europa.ec.simpl.tier2proxy.certificate.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import eu.europa.ec.simpl.tier2proxy.certificate.CaEndpoint;
import eu.europa.ec.simpl.tier2proxy.certificate.CertificateOptions;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificateServerHandlerTest {

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelPipeline pipeline;

    @Mock
    private Channel channel;

    @Mock
    private ChannelFuture channelFuture;

    @Mock
    private X509Certificate certificate;

    private CertificateServerHandler handler;
    private final String testEndpointUri = "/ca.crt";
    private final byte[] testCertificateBytes = "TEST_CERTIFICATE_CONTENT".getBytes(CharsetUtil.UTF_8);

    @BeforeEach
    void setUp() throws IOException {
        // Mock certificate
        when(certificate.getSubjectX500Principal())
                .thenReturn(new javax.security.auth.x500.X500Principal("CN=Test CA"));

        var serverConfig = new ServerConfig(true, "localhost", 8080);
        var caEndpoint = new CaEndpoint(testEndpointUri, HttpMethod.GET);
        var certificateOptions = mock(CertificateOptions.class);
        when(certificateOptions.endpoint()).thenReturn(caEndpoint);

        var certificateServerOptions =
                new CertificateServerOptions(serverConfig, 65536, HttpMethod.GET, certificateOptions);

        try (MockedStatic<Certificates> certificatesMock = mockStatic(Certificates.class)) {
            certificatesMock.when(() -> Certificates.toPem(certificate)).thenReturn(testCertificateBytes);

            handler = new CertificateServerHandler(certificateServerOptions, certificate);
        }

        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    void testHandlerAdded() {
        when(ctx.pipeline()).thenReturn(pipeline);
        when(ctx.name()).thenReturn("testHandler");
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(pipeline.addBefore(any(), any(), any(ChannelHandler.class))).thenReturn(pipeline);

        // When
        handler.handlerAdded(ctx);

        // Then
        verify(pipeline)
                .addBefore(eq("testHandler"), eq(HttpServerCodec.class.getCanonicalName()), any(HttpServerCodec.class));
        verify(pipeline)
                .addBefore(
                        eq("testHandler"),
                        eq(HttpObjectAggregator.class.getCanonicalName()),
                        any(HttpObjectAggregator.class));
        verify(pipeline)
                .addBefore(
                        eq("testHandler"),
                        eq(HttpServerExpectContinueHandler.class.getCanonicalName()),
                        any(HttpServerExpectContinueHandler.class));
    }

    @Test
    void testHandlerRemoved() {
        when(ctx.pipeline()).thenReturn(pipeline);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));

        // When
        handler.handlerRemoved(ctx);

        // Then
        verify(pipeline).remove(any(HttpServerCodec.class));
        verify(pipeline).remove(any(HttpServerExpectContinueHandler.class));
    }

    @Test
    void testChannelRead0WithValidCertificateRequest() {
        // Given
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, testEndpointUri);

        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);

        // When
        handler.channelRead0(ctx, request);

        // Then
        var responseCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());

        var response = responseCaptor.getValue();
        assert response.status().equals(HttpResponseStatus.OK);
        assert response.headers()
                .get(HttpHeaderNames.CONTENT_TYPE)
                .equals(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString());

        var content = new byte[response.content().readableBytes()];
        response.content().getBytes(0, content);
        assert java.util.Arrays.equals(content, testCertificateBytes);
    }

    @Test
    void testChannelRead0WithInvalidMethod() {
        // Given
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, testEndpointUri);

        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);

        // When
        handler.channelRead0(ctx, request);

        // Then
        var responseCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());

        var response = responseCaptor.getValue();
        assert response.status().equals(HttpResponseStatus.NOT_FOUND);
    }

    @Test
    void testChannelRead0WithInvalidUri() {
        // Given
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/wrong-path"); // Wrong URI

        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);

        // When
        handler.channelRead0(ctx, request);

        // Then
        var responseCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());

        var response = responseCaptor.getValue();
        assert response.status().equals(HttpResponseStatus.NOT_FOUND);
    }

    @Test
    void testChannelRead0WithKeepAliveTrue() {
        // Given
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, testEndpointUri);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);

        // When
        handler.channelRead0(ctx, request);

        // Then
        var responseCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());

        var response = responseCaptor.getValue();
        verify(channelFuture, times(0)).addListener(any()); // No close listener should be added
    }

    @Test
    void testChannelRead0WithKeepAliveFalse() {
        // Given
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, testEndpointUri);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);

        // When
        handler.channelRead0(ctx, request);

        // Then
        verify(channelFuture).addListener(any()); // Close listener should be added
    }

    @Test
    void testChannelInactive() throws Exception {
        // When
        handler.channelInactive(ctx);

        // Then
        verify(ctx).close();
    }

    @Test
    void testExceptionCaught() throws Exception {
        // Given
        var exception = new RuntimeException("Test exception");

        // When
        handler.exceptionCaught(ctx, exception);

        // Then
        verify(ctx).close();
    }
}
