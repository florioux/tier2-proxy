package eu.europa.ec.simpl.tier2proxy;

public record ServerConfig(boolean enabled, String bindAddr, int bindPort) {
    @Override
    public String toString() {
        return String.format("%s:%d", bindAddr, bindPort);
    }
}
