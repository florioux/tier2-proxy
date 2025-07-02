package eu.europa.ec.simpl.tier2proxy.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class PropertyLoaderTest {

    @SystemStub
    private EnvironmentVariables environmentVariables;

    @Test
    void testLoadPropertiesFromClasspath() {
        var properties = PropertyLoader.loadPropertiesFromClasspath("application.properties");
        Assertions.assertThat(properties).isNotNull().isNotEmpty();
    }

    @Test
    void testLoadPropertiesFromFileWhenFileDoesNotExistShouldReturnEmptyProperties() {
        var properties = PropertyLoader.loadPropertiesFromFile("not-existing-file.properties");
        Assertions.assertThat(properties).isNotNull().isEmpty();
    }

    @Test
    void testLoadPropertiesFromClasspathShouldOverridePropertyWithEnvVariables() {
        var proxyOptionsThreadNumber = "10";
        environmentVariables.set("PROXY_OPTIONS_THREAD_NUMBER", proxyOptionsThreadNumber);
        var properties = PropertyLoader.loadPropertiesFromClasspath("application.properties");

        Assertions.assertThat(properties)
                .isNotNull()
                .isNotEmpty()
                .containsEntry("proxy-options.thread-number", proxyOptionsThreadNumber);
    }

    @Test
    void testLoadPropertiesFromClasspathShouldSubstituteValueWithEnvironmentIfDeclared() {
        var certFolderValue = "/cert-folder";
        environmentVariables.set("CERTS_FOLDER", certFolderValue);
        var properties = PropertyLoader.loadPropertiesFromClasspath("with-env-value.properties");

        Assertions.assertThat(properties)
                .isNotNull()
                .isNotEmpty()
                .containsEntry("proxy.certificate.ca.location", certFolderValue);
    }

    @Test
    void testLoadPropertiesFromClasspathShouldSubstituteValueWithPreviousDefinedVariablesIfDeclared() {
        environmentVariables.set("SIMPL_AUTHENTICATION_PROVIDER_BASEURL", "http://auth-provider");
        var properties = PropertyLoader.loadPropertiesFromClasspath("with-env-value.properties");

        Assertions.assertThat(properties)
                .isNotNull()
                .isNotEmpty()
                .containsEntry("simpl.authentication-provider.baseurl", "http://auth-provider")
                .containsEntry(
                        "simpl.authentication-provider.get-credentials-url", "http://auth-provider/v1/credentials");
    }
}
