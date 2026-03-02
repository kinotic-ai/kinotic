package org.kinotic.test.support.elastic;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.kinotic.sql.SystemMigrator;
import org.kinotic.sql.executor.MigrationExecutor;
import org.kinotic.sql.parsers.MigrationParser;

@ContextConfiguration(initializers = ElasticsearchTestContextInitializer.class)
@SpringBootTest
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
