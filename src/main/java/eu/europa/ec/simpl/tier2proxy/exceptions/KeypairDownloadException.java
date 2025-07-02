package eu.europa.ec.simpl.tier2proxy.exceptions;

public class KeypairDownloadException extends InterruptedException {
    public KeypairDownloadException(String message) {
        super(message);
    }
}
