package eu.europa.ec.simpl.tier2proxy.proxy;

public record Addr(String addr, int port) {
    @Override
    public String toString() {
        return String.format("%s:%d", addr, port);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Addr other)) {
            return false;
        }
        return addr.equals(other.addr()) && port == other.port();
    }
}
