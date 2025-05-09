package eu.europa.ec.simpl.tier2proxy.certificate.authority;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;

import java.io.IOException;
import java.util.NoSuchElementException;

public interface CertificateAuthorityRepository {
	CertificateInfo getStoredCertificate(String host) throws NoSuchElementException;

	void storeCertificate(String host, CertificateInfo certificateInfo) throws IOException;
}
