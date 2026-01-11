package org.mindignited.structures;

import org.mindignited.structures.cluster.TestClusterProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Profile("test")
@ConfigurationProperties(prefix = "structures.test")
@Component
@NoArgsConstructor
@Getter
@Setter
@Validated
public class TestProperties {

    @Valid
    private TestClusterProperties cluster = new TestClusterProperties();
    
}
