package eu.europa.ec.simpl.tier2proxy.certificate;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Slf4j
public final class Certificates {
	private final CertificateFactory certificateFactory;
	@Getter
	private final CertificateInfo caCertificate;
	private final LoadingCache<String, CertificateInfo> certsCache;

	public Certificates(
			CertificateOptions co,
			CertificateAuthorityRepository certificateAuthorityRepository) throws NoSuchAlgorithmException {
		if (log.isDebugEnabled()) {
			log.info("loading bouncycastle provider");
		}
		Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
		Security.addProvider(new BouncyCastleProvider());

		if (log.isInfoEnabled()) {
			log.info("loading certificate authority repository");
		}

		try {
			this.certificateFactory = new CertificateFactory(
					co.caSubject(),
					co.privateKey(),
					co.certificateValidity(),
					co.signatureAlgo(),
					certificateAuthorityRepository);
		} catch (NoSuchAlgorithmException e) {
			if (log.isErrorEnabled()) {
				log.error("error while loading certificate factory", e);
			}
			throw new NoSuchAlgorithmException("error while loading certificate factory", e);
		}

		this.caCertificate = this.certificateFactory.getCACertificate();

		this.certsCache = Caffeine
				.newBuilder()
				.expireAfterWrite(
						co.certificateCache().certificateCacheExpirationDuration(),
						co.certificateCache().certificateCacheExpirationDurationTimeUnit()
				)
				.maximumSize(co.certificateCache().certificatesCacheSize())
				.build(host -> {
					if (log.isDebugEnabled()) {
						log.debug("getting certificate for {}", host);
					}
					return certificateFactory.getCertificate(caCertificate, host);
				});
	}

	public CertificateInfo certificateFor(String host) {
		if (log.isDebugEnabled()) {
			log.debug("certificate for {} host", host);
		}
		return this.certsCache.get(host);
	}

	public static byte[] toPem(Object object) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		try (var writer = new JcaPEMWriter(new OutputStreamWriter(outputStream))) {
			writer.writeObject(object);
			writer.flush();
			return outputStream.toByteArray();
		}
	}

	public static PrivateKey privateKeyFromPem(byte[] privateKeyBytes) throws IOException {
		try (PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(privateKeyBytes)))) {

			Object object = pemParser.readObject();
			if (object instanceof PEMKeyPair keypair) {
				JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
				var kp = converter.getKeyPair(keypair);

				return kp.getPrivate();
			} else {

				throw new IOException(String.format("read file %s is not a PEMKeyPair", object));
			}
		} catch (IOException e) {
			if (log.isErrorEnabled()) {
				log.error("error while reading private key from byte[]: {}", privateKeyBytes, e);
			}
			throw e;
		}
	}

	public static X509Certificate certificateFromPem(byte[] certBytes) throws CertificateException {
		java.security.cert.CertificateFactory certFactory = java.security.cert.CertificateFactory.getInstance("X.509");
		try (ByteArrayInputStream inputStream = new ByteArrayInputStream(certBytes)) {
			return (X509Certificate) certFactory.generateCertificate(inputStream);
		} catch (IOException exception) {
			throw new CertificateException(exception);
		}
	}
}
