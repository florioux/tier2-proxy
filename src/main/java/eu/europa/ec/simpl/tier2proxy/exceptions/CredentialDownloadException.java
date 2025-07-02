package eu.europa.ec.simpl.tier2proxy.exceptions;

public class CredentialDownloadException extends InterruptedException {
    public CredentialDownloadException(String message) {
        super(message);
    }
}
