package eu.europa.ec.simpl.tier2proxy.certificate;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public record CertificateInfo(PrivateKey privateKey, X509Certificate certificate) {
	@Override
	public String toString() {
		return certificate.getSubjectX500Principal().toString();
	}
}
