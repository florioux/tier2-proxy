package eu.europa.ec.simpl.tier2proxy.certificate;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import org.bouncycastle.asn1.x500.X500Name;

public record CertificateOptions(
        X500Name caSubject,
        PrivateKey privateKey,
        String signatureAlgo,
        CertificateCache certificateCache,
        CertificateValidity certificateValidity) {

    public record PrivateKey(String algo, AlgorithmParameterSpec algoParameterSpec, SecureRandom random) {
        KeyPairGenerator of() throws NoSuchAlgorithmException {
            KeyPairGenerator toReturn = null;
            try {
                toReturn = KeyPairGenerator.getInstance(algo);
                toReturn.initialize(algoParameterSpec, random);

                return toReturn;
            } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
                throw new NoSuchAlgorithmException(e);
            }
        }
    }

    public record CertificateValidity(
            int beforeDateAmountToSubtract,
            int afterDateAmountToAdd,
            @NonNull ChronoUnit beforeDateAmountToSubtractUnit,
            @NonNull ChronoUnit afterDateAmountToAddUnit) {
        ValidityPeriod of() {
            return new ValidityPeriod(
                    beforeDateAmountToSubtract,
                    afterDateAmountToAdd,
                    beforeDateAmountToSubtractUnit,
                    afterDateAmountToAddUnit);
        }
    }

    public record CertificateCache(
            int certificatesCacheSize,
            int certificateCacheExpirationDuration,
            TimeUnit certificateCacheExpirationDurationTimeUnit) {}
}
