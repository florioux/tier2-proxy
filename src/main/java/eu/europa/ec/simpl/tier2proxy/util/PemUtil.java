package eu.europa.ec.simpl.tier2proxy.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

@Slf4j
@UtilityClass
public class PemUtil {

    public static byte[] writePem(List<X509Certificate> certificates) {
        var outputStream = new ByteArrayOutputStream();
        writePem(certificates, outputStream);
        return outputStream.toByteArray();
    }

    @SneakyThrows
    public static void writePem(List<X509Certificate> certificates, OutputStream outputStream) {
        try (var pemWriter = new JcaPEMWriter(new OutputStreamWriter(outputStream))) {
            for (var x509Certificate : certificates) {
                pemWriter.writeObject(x509Certificate);
            }
        }
    }

    @SneakyThrows
    public static List<InputStream> loadPemObjects(InputStream inputStream) {
        List<InputStream> pemStreams = new ArrayList<>();
        try (var pemReader = new PemReader(new InputStreamReader(inputStream))) {
            PemObject pemObject;
            while ((pemObject = pemReader.readPemObject()) != null) {
                pemStreams.add(new ByteArrayInputStream(pemObject.getContent()));
            }
        }
        return pemStreams;
    }

    @SneakyThrows
    public static PrivateKey loadPrivateKey(InputStream inputStream, String algorithm) {
        var pemContent = PemUtil.loadPemObjects(inputStream).getFirst().readAllBytes();
        var keySpec = new PKCS8EncodedKeySpec(pemContent);
        var keyFactory = KeyFactory.getInstance(algorithm);
        return keyFactory.generatePrivate(keySpec);
    }
}
