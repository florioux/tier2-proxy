package eu.europa.ec.simpl.tier2proxy.proxy.http;

import eu.europa.ec.simpl.tier2proxy.proxy.Addr;
import io.netty.util.internal.StringUtil;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import lombok.Getter;

@Getter
final class FullPath {

    private static final Pattern PATH_PATTERN = Pattern.compile("(https?)://([a-zA-Z0-9\\-]+)(:(\\d+))?([/.]*)");
    private static final Pattern CONNECT_ADDR_PATTERN = Pattern.compile("^([a-zA-Z0-9.\\-_]+):(\\d+)");
    public static final int HTTPS_PORT = 443;
    public static final int HTTP_PORT = 80;

    private final String scheme;
    private final String host;
    private final int port;
    private final String path;

    FullPath(String fullPath) {
        var matcher = PATH_PATTERN.matcher(fullPath);
        if (matcher.find()) {
            var i = new AtomicInteger(0);
            this.scheme = matcher.group(i.incrementAndGet());
            this.host = matcher.group(i.incrementAndGet());
            this.port = resolvePort(scheme, matcher.group(i.incrementAndGet()));
            this.path = matcher.group(i.incrementAndGet());
        } else {
            throw new IllegalStateException("Illegal http proxy path: " + fullPath);
        }
    }

    Addr toAddr() {
        return new Addr(this.host, this.port);
    }

    static Addr resolveAddrInConnect(String addr) {
        var matcher = CONNECT_ADDR_PATTERN.matcher(addr);
        var i = new AtomicInteger(1);
        if (matcher.find()) {
            return new Addr(matcher.group(i.getAndIncrement()), Integer.parseInt(matcher.group(i.get())));
        } else {
            throw new IllegalStateException("Illegal tunnel addr: " + addr);
        }
    }

    private static int resolvePort(String scheme, String port) {
        if (StringUtil.isNullOrEmpty(port)) {
            return "https".equals(scheme) ? HTTPS_PORT : HTTP_PORT;
        }
        return Integer.parseInt(port);
    }

    @Override
    public String toString() {
        return "FullPath{" + "scheme='"
                + scheme + '\'' + ", host='"
                + host + '\'' + ", port="
                + port + ", path='"
                + path + '\'' + '}';
    }
}
