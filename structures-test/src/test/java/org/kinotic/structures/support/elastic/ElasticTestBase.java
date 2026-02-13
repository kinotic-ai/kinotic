package org.kinotic.structures.support.elastic;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.kinotic.structures.api.annotations.EnableStructures;
import org.kinotic.structures.sql.SystemMigrator;
import org.kinotic.structures.sql.executor.MigrationExecutor;
import org.kinotic.structures.sql.parsers.MigrationParser;

@ContextConfiguration(initializers = ElasticsearchTestContextInitializer.class)
@SpringBootTest
@EnableStructures
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ElasticTestBase {

    @Autowired
    private MigrationExecutor migrationExecutor;
    @Autowired
    private MigrationParser migrationParser;
    
    @BeforeEach
    public void beforeEach() {
        SystemMigrator systemMigrator = new SystemMigrator(this.migrationExecutor, this.migrationParser);
        systemMigrator.execute();
    }
}
