package eu.europa.ec.simpl.tier2proxy.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeypairDownloadExceptionTest {

    @Test
    void testKeypairDownloadExceptionShouldCreateExceptionWithMessage() {
        var errorMessage = "Failed to download keypair";
        var exception = new KeypairDownloadException(errorMessage);

        assertThat(exception)
                .as("the exception should not be null")
                .isNotNull()
                .isInstanceOf(KeypairDownloadException.class)
                .hasMessage(errorMessage);
    }
}
