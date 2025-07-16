package eu.europa.ec.simpl.tier2proxy.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CredentialDownloadExceptionTest {

    @Test
    void testCredentialDownloadExceptionShouldCreateExceptionWithMessage() {
        var errorMessage = "Failed to download credentials";
        var exception = new CredentialDownloadException(errorMessage);

        assertThat(exception)
                .as("the exception should not be null")
                .isNotNull()
                .isInstanceOf(CredentialDownloadException.class)
                .hasMessage(errorMessage);
    }
}
