

package org.mindignited.structures.sql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication()
@EnableConfigurationProperties
public class StructuresSqlTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(StructuresSqlTestApplication.class, args);
    }
}
