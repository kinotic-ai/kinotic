package org.kinotic.test.support.elastic;


import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = ElasticsearchTestContextInitializer.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ElasticTestBase {


}
