package eu.europa.ec.simpl.tier2proxy;

import eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient;
import eu.europa.ec.simpl.tier2proxy.authprovider.CredentialHolder;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import eu.europa.ec.simpl.tier2proxy.certificate.authority.impl.FileSystemCertificateAuthorityImpl;
import eu.europa.ec.simpl.tier2proxy.certificate.http.CertificateServer;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HttpProtocolProxyServer;
import eu.europa.ec.simpl.tier2proxy.proxy.socks.SocksProtocolProxyServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.PromiseCombiner;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Proxy {

    private static final Configuration configuration = Configuration.getInstance();

    public static void main(String[] args) {
        log.info("starting");

        var certificateAuthorityRepository = fsCertificateAuthorityRepository();

        Certificates certificates;
        try {
            certificates = new Certificates(
                    configuration.getCertificateServerOptions().certificateOptions(), certificateAuthorityRepository);
        } catch (NoSuchAlgorithmException e) {
            log.error(
                    "error initializing dynamic certificate authority with options {} and repository {}",
                    configuration.getCertificateServerOptions().certificateOptions(),
                    certificateAuthorityRepository,
                    e);
            System.exit(1);
            return;
        }

        var caCertificateInfo = certificates.getCaCertificate();
        log.debug("certificate authority {}", caCertificateInfo);

        var caCertificate = caCertificateInfo.certificate();
        try (EventExecutor executor = new DefaultEventLoop()) {
            var osType = OsType.fromOS();
            EventLoopGroup bossGroup =
                    osType.bossGroup(configuration.getProxyOptions().threadNum(), executor);

            var bootstrap = osType.clientBootstrapSupplier(bossGroup);

            initializeSimplCredentials(bootstrap);

            var caServer = new CertificateServer(
                    osType, bossGroup, configuration.getCertificateServerOptions(), caCertificate);
            var httpProtocolProxyServer = new HttpProtocolProxyServer(
                    osType, bossGroup, configuration.getHttpProtocolServerOptions(), certificates);
            var socksProtocolProxyServer = new SocksProtocolProxyServer(
                    osType, bossGroup, configuration.getSocksProtocolServerOptions(), certificates);

            var caFuture = caServer.start();
            var httpProtocolFuture = httpProtocolProxyServer.start();
            var socksProtocolFuture = socksProtocolProxyServer.start();

            var tearDownJob = TeardownPolicy.tearDownJob(
                    configuration.getTeardownPolicy(),
                    bossGroup,
                    caServer,
                    httpProtocolProxyServer,
                    socksProtocolProxyServer);
            var tearDownPolicy = new Thread(tearDownJob);
            Runtime.getRuntime().addShutdownHook(tearDownPolicy);

            var endingPromise = executor.<Void>newPromise();
            var all = new PromiseCombiner(executor);
            all.finish(endingPromise);

            all.add(caFuture);
            all.add(httpProtocolFuture);
            all.add(socksProtocolFuture);

            endingPromise.sync();
        } catch (InterruptedException e) {
            log.error("proxy is interrupted", e);
            Thread.currentThread().interrupt();
            System.exit(1);
            return;
        }

        System.exit(0);
    }

    private static void initializeSimplCredentials(Bootstrap bootstrap) {
        var authProviderClient = new AuthProviderClient(bootstrap);

        CredentialHolder.getInstance()
                .initCredentials(
                        authProviderClient.getCredential(),
                        authProviderClient.getInstalledKeypair().getPrivateKey());
    }

    private static CertificateAuthorityRepository fsCertificateAuthorityRepository() {
        try {
            return new FileSystemCertificateAuthorityImpl(Path.of(configuration
                    .getCertificateServerOptions()
                    .certificateOptions()
                    .location()));
        } catch (IOException e) {
            throw new IllegalStateException("error while loading certificate authority repository", e);
        }
    }
}
