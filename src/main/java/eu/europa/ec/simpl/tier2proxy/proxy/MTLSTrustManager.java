package eu.europa.ec.simpl.tier2proxy.proxy;

import eu.europa.ec.simpl.client.util.CertificateRevocation;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class MTLSTrustManager implements X509TrustManager {
    private final CertificateRevocation certificateRevocation;

    private final X509TrustManager delegate;

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        delegate.checkClientTrusted(x509Certificates, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
        var cert = x509Certificates[0];
        log.debug("checking server trust for {}, serial: {}", cert.getSubjectX500Principal(), cert.getSerialNumber());
        certificateRevocation.verify(cert);
        log.debug("server trust for {} is valid", cert.getSubjectX500Principal());
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }
}
