package org.mindignited.structuresserver;

import org.mindignited.structuresserver.config.ElasticsearchTestContextInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ElasticsearchTestContextInitializer.class)
@EnableAutoConfiguration()
public class StructuresServerTestApplication {
    public static void main(String[] args) {
		SpringApplication.run(StructuresServerTestApplication.class, args);
	}

}
