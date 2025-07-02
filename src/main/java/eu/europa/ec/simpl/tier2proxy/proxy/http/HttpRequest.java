package eu.europa.ec.simpl.tier2proxy.proxy.http;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpRequest {

    public static final int HTTPS_PORT = 443;
    public static final int HTTP_PORT = 80;
    private URI url;
    private HttpMethod method;
    private String contentType;
    private Object body;
    private Map<String, String> headers = new HashMap<>();

    public FullHttpRequest build() {
        if (url == null || method == null) {
            throw new IllegalStateException("FullPath and HttpMethod must be set before building the request.");
        }

        var bodyBytes = Optional.ofNullable(body).map(b -> b.toString().getBytes(StandardCharsets.UTF_8));

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                method,
                Objects.requireNonNullElse(url.getPath(), "/"),
                bodyBytes.map(Unpooled::copiedBuffer).orElse(Unpooled.EMPTY_BUFFER));

        request.headers()
                .set(
                        HttpHeaderNames.CONTENT_LENGTH,
                        bodyBytes.map(bytes -> bytes.length).orElse(0));
        request.headers().set(HttpHeaderNames.HOST, url.getHost());
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, getContentType());
        request.headers().set(HttpHeaderNames.ACCEPT, "*/*");

        headers.forEach((key, value) -> request.headers().set(AsciiString.of(key), value));

        return request;
    }

    public HttpRequest setUrl(String url) {
        this.url = URI.create(url);
        return this;
    }

    public String getHost() {
        return Optional.of(url)
                .map(URI::getHost)
                .orElseThrow(() -> new IllegalStateException("URL must be set before getting the host"));
    }

    public int getPort() {
        return Optional.of(url)
                .map(URI::getPort)
                .filter(port -> port != -1)
                .orElse(Objects.equals(url.getScheme(), "https") ? HTTPS_PORT : HTTP_PORT);
    }

    private AsciiString getContentType() {
        return Optional.ofNullable(contentType).map(AsciiString::of).orElse(HttpHeaderValues.APPLICATION_JSON);
    }
}
