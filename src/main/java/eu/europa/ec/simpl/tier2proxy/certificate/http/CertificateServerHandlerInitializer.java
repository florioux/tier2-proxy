package eu.europa.ec.simpl.tier2proxy.certificate.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.cert.X509Certificate;

@Slf4j
final class CertificateServerHandlerInitializer extends ChannelInitializer<SocketChannel> {
	private final CertificateServerOptions certificateServerOptions;
	private final X509Certificate caCertificate;

	public CertificateServerHandlerInitializer(CertificateServerOptions certificateServerOptions, X509Certificate caCertificate) {
		if (log.isDebugEnabled()) {
			log.debug("initializing certificate handler for certificate {}",
					caCertificate.getSubjectX500Principal().getName());
		}
		this.certificateServerOptions = certificateServerOptions;
		this.caCertificate = caCertificate;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("initializing channel for certificate {} from {}",
					caCertificate.getSubjectX500Principal(), ch.remoteAddress());
		}

		CertificateServerHandler handler = new CertificateServerHandler(this.certificateServerOptions, this.caCertificate);
		ch.pipeline()
		  .replace(this,
				  CertificateServerHandler.class.getCanonicalName(), handler);
	}
}
