package eu.europa.ec.simpl.tier2proxy.configurations;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigurationTest {

    @Test
    void getInstance() {
        var conf = Configuration.getInstance();
        Assertions.assertThat(conf).hasNoNullFieldsOrProperties();
    }

    @Test
    void getInstanceShouldReturnSameInstance() {
        var conf1 = Configuration.getInstance();
        var conf2 = Configuration.getInstance();
        Assertions.assertThat(conf1).isSameAs(conf2);
    }

    @Test
    void getSubjectCA() {
        var conf = Configuration.getInstance();
        var subject = conf.getCertificateServerOptions().certificateOptions().caSubject();
        System.out.println(subject.toString());
        Assertions.assertThat(subject).isNotNull();
    }
}
