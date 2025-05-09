package eu.europa.ec.simpl.tier2proxy.proxy;

public record Addr(String addr, int port) {
	@Override
	public String toString() {
		return String.format("%s:%d", addr, port);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Addr(String otherAddr, int otherPort))) {
			return false;
		}

		return addr.equals(otherAddr) && port == otherPort;
	}
}
