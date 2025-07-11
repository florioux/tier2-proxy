package eu.europa.ec.simpl.tier2proxy.proxy.http;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpRequestTest {

    @Test
    void testBuildWithBodyAndHeaders() {
        var httpRequest = new HttpRequest()
                .setUrl("http://localhost:8080/test")
                .setMethod(HttpMethod.POST)
                .setContentType("text/plain")
                .setBody("ciao")
                .setHeaders(Map.of("X-Test", "123"));

        var fullRequest = httpRequest.build();
        assertEquals("/test", fullRequest.uri());
        assertEquals(HttpMethod.POST, fullRequest.method());
        assertEquals("localhost", fullRequest.headers().get(HttpHeaderNames.HOST));
        assertEquals("text/plain", fullRequest.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("123", fullRequest.headers().get("X-Test"));
        assertEquals("ciao", fullRequest.content().toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void testBuildWithoutBody() {
        var httpRequest =
                new HttpRequest().setUrl("http://localhost:8080/test2").setMethod(HttpMethod.GET);

        var fullRequest = httpRequest.build();
        assertEquals("/test2", fullRequest.uri());
        assertEquals(HttpMethod.GET, fullRequest.method());
        assertEquals("localhost", fullRequest.headers().get(HttpHeaderNames.HOST));
        assertEquals(
                HttpHeaderValues.APPLICATION_JSON.toString(),
                fullRequest.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("*/*", fullRequest.headers().get(HttpHeaderNames.ACCEPT));
        assertEquals(0, fullRequest.content().readableBytes());
    }

    @Test
    void testSetUrlParsesString() {
        var httpRequest = new HttpRequest().setUrl("https://example.com:8443/foo");
        assertEquals(URI.create("https://example.com:8443/foo"), httpRequest.getUrl());
    }

    @Test
    void testGetHost() {
        var httpRequest = new HttpRequest().setUrl("https://example.com:8443/foo");
        assertEquals("example.com", httpRequest.getHost());
    }

    @Test
    void testGetPortWithExplicitPort() {
        var httpRequest = new HttpRequest().setUrl("https://example.com:8443/foo");
        assertEquals(8443, httpRequest.getPort());
    }

    @Test
    void testGetPortWithDefaultHttps() {
        var httpRequest = new HttpRequest().setUrl("https://example.com/foo");
        assertEquals(HttpRequest.HTTPS_PORT, httpRequest.getPort());
    }

    @Test
    void testGetPortWithDefaultHttp() {
        var httpRequest = new HttpRequest().setUrl("http://example.com/foo");
        assertEquals(HttpRequest.HTTP_PORT, httpRequest.getPort());
    }

    @Test
    void testBuildThrowsIfMissingUrlOrMethod() {
        var httpRequest = new HttpRequest();
        var ex = assertThrows(IllegalStateException.class, httpRequest::build);
        assertTrue(ex.getMessage().contains("FullPath and HttpMethod must be set"));
    }

    @Test
    void testGetHostThrowsIfMissingUrl() {
        var httpRequest = new HttpRequest().setUrl("");
        var ex = assertThrows(IllegalStateException.class, httpRequest::getHost);
        assertTrue(ex.getMessage().contains("URL must be set before getting the host"));
    }
}
