package eu.europa.ec.simpl.tier2proxy;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;
import eu.europa.ec.simpl.tier2proxy.certificate.CertificateOptions;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import eu.europa.ec.simpl.tier2proxy.certificate.authority.impl.FileSystemCertificateAuthorityImpl;
import eu.europa.ec.simpl.tier2proxy.certificate.http.CertificateServer;
import eu.europa.ec.simpl.tier2proxy.certificate.http.CertificateServerOptions;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HttpProtocolProxyServer;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HttpProtocolServerOptions;
import eu.europa.ec.simpl.tier2proxy.proxy.transparent.TransparentProxyServer;
import eu.europa.ec.simpl.tier2proxy.proxy.socks.SocksProtocolProxyServer;
import eu.europa.ec.simpl.tier2proxy.proxy.socks.SocksProtocolServerOptions;
import eu.europa.ec.simpl.tier2proxy.proxy.transparent.TransparentProxyServerOptions;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class Proxy {
	public static void main(String[] args) {
		if (log.isInfoEnabled()) {
			log.info("starting");
		}
		OsType osType = OsType.fromOS();
		ProxyOptions proxyOptions = new ProxyOptions(1);
		TeardownPolicy teardownPolicy = new TeardownPolicy(2, 10, TimeUnit.SECONDS);

		CertificateOptions             options                        = defaultCertificateOptions();
		CertificateAuthorityRepository certificateAuthorityRepository = fsCertificateAuthorityRepository();

		CertificateServerOptions   certificateServerOptions   = defaultCertificateServerOptions();
		HttpProtocolServerOptions  httpProtocolServerOptions  = defaultHttpProtocolServerOptions();
		SocksProtocolServerOptions socksProtocolServerOptions = defaultSocksProtocolServerOptions();
		TransparentProxyServerOptions transparentProxyServerOptions = defaultTransparentProxyServerOptions();

		Certificates certificates;
		try {
			certificates = new Certificates(options, certificateAuthorityRepository);
		} catch(NoSuchAlgorithmException e) {
			if (log.isErrorEnabled()) {
				log.error("error initializing dynamic certificate authority with options {} and repository {}", options, certificateAuthorityRepository, e);
			}
			System.exit(1);
			return;
		}

		CertificateInfo caCertificateInfo = certificates.getCaCertificate();
		if (log.isDebugEnabled()) {
			log.debug("certificate authority {}", caCertificateInfo);
		}

		var caCertificate = caCertificateInfo.certificate();
		try (EventExecutor executor = new DefaultEventLoop()) {
			EventLoopGroup bossGroup = osType.bossGroup(proxyOptions.threadNum(), executor);

			CertificateServer caServer = new CertificateServer(osType, bossGroup,
					certificateServerOptions, caCertificate);
			Server httpProtocolProxyServer = new HttpProtocolProxyServer(osType, bossGroup,
					httpProtocolServerOptions, certificates);
			Server socksProtocolProxyServer = new SocksProtocolProxyServer(osType, bossGroup,
					socksProtocolServerOptions, certificates);
			Server transparentProxyServer = new TransparentProxyServer(osType, bossGroup,
					transparentProxyServerOptions, certificates);

			ChannelFuture caFuture = caServer.start();
			ChannelFuture httpProtocolFuture = httpProtocolProxyServer.start();
			ChannelFuture socksProtocolFuture = socksProtocolProxyServer.start();
			ChannelFuture transparentFuture = transparentProxyServer.start();

			Runnable tearDownJob = TeardownPolicy.tearDownJob(
					teardownPolicy,
					bossGroup, caServer,
					httpProtocolProxyServer, socksProtocolProxyServer, transparentProxyServer
			);
			Thread tearDownPolicy = new Thread(tearDownJob);
			Runtime.getRuntime().addShutdownHook(tearDownPolicy);

			Promise<Void> endingPromise = executor.<Void>newPromise();
			PromiseCombiner all = new PromiseCombiner(executor);
			all.finish(endingPromise);

			all.add(caFuture);
			all.add(httpProtocolFuture);
			all.add(socksProtocolFuture);
			all.add(transparentFuture);

			endingPromise.sync();
		} catch (InterruptedException e) {
			if (log.isErrorEnabled()) {
				log.error("proxy is interrupted", e);
			}
			System.exit(1);
			return;
		}

		System.exit(0);
	}

	private static TransparentProxyServerOptions defaultTransparentProxyServerOptions() {
		return new TransparentProxyServerOptions(
				new ServerConfig("0.0.0.0", 3003),
				65536
		);
	}

	private static SocksProtocolServerOptions defaultSocksProtocolServerOptions() {
		return new SocksProtocolServerOptions(
				new ServerConfig("0.0.0.0", 3002),
				65536
		);
	}

	private static HttpProtocolServerOptions defaultHttpProtocolServerOptions() {
		return new HttpProtocolServerOptions(
				new ServerConfig("0.0.0.0", 3001),
				65536
		);
	}

	private static CertificateServerOptions defaultCertificateServerOptions() {
		return new CertificateServerOptions(
				new ServerConfig("0.0.0.0", 3000),
				65536,
				HttpMethod.GET,
				"/cert"
		);
	}

	private static CertificateAuthorityRepository fsCertificateAuthorityRepository() {
		try {
			return new FileSystemCertificateAuthorityImpl(Path.of("_managed_ca"));
		} catch (IOException e) {
			throw new IllegalStateException("error while loading certificate authority repository", e);
		}
	}

	private static CertificateOptions defaultCertificateOptions() {
		X500Name x500NameConf = new X500Name("C=EU, ST=IT, L=Rome, O=Mitm Proxy, OU=Mitm Proxy, CN=Mitm Proxy CA");
		CertificateOptions.PrivateKey privateKeyConf = new CertificateOptions
				.PrivateKey("ECDSA", new ECGenParameterSpec("secp521r1"), new SecureRandom());
		String signatureAlgoConf = "SHA256withECDSA";

		CertificateOptions.CertificateCache certificateCacheConf = new CertificateOptions.CertificateCache(
				2_000,
				1,
				TimeUnit.DAYS
		);

		CertificateOptions.CertificateValidity certificateValidityConf = new CertificateOptions
				.CertificateValidity(6, 1_000,
				ChronoUnit.MONTHS, ChronoUnit.YEARS
		);

		return new CertificateOptions(x500NameConf, privateKeyConf,
				signatureAlgoConf, certificateCacheConf, certificateValidityConf);
	}
}
