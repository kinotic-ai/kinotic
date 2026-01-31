package org.mindignited.structures;

import org.mindignited.structures.internal.api.domain.DefaultEntityContext;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.internal.sample.DummyParticipant;
import org.mindignited.structures.support.StructureAndPersonHolder;
import org.mindignited.structures.support.TestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import reactor.test.StepVerifier;


@SpringBootTest
public abstract class ElasticsearchTestBase {

    public static final ElasticsearchContainer ELASTICSEARCH_CONTAINER;

    @Autowired
    protected TestHelper testHelper;

    protected static boolean useExternalElasticsearch;

    protected static String elasticsearchHost;

    protected static int elasticsearchPort;

    protected static String elasticsearchClusterNodes;

    static {
        // 
        // NOTE: STRUCTURES_TEST_USE_EXTERNAL_ELASTICSEARCH is set in the build.gradle
        //       it is done this way so that when testing locally we can have more control
        //       over where elasticsearch is running. 
        //
        System.out.println("Bootstrap Elasticsearch: " + System.getProperty("STRUCTURES_TEST_USE_EXTERNAL_ELASTICSEARCH"));
        String bootstrapElasticsearch = System.getProperty("STRUCTURES_TEST_USE_EXTERNAL_ELASTICSEARCH");
        useExternalElasticsearch = Boolean.parseBoolean(bootstrapElasticsearch);
        if(!useExternalElasticsearch){
            System.out.println("Starting Elasticsearch Test Container...");

            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
    
            ELASTICSEARCH_CONTAINER = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.2.4");
            ELASTICSEARCH_CONTAINER.withEnv("discovery.type", "single-node")
                                   .withEnv("xpack.security.enabled", "false");
    
            // We need this until this is resolved https://github.com/elastic/elasticsearch/issues/118583
            if(osName != null && osName.startsWith("Mac") && osArch != null && osArch.equals("aarch64")){
                ELASTICSEARCH_CONTAINER.withEnv("_JAVA_OPTIONS", "-XX:UseSVE=0");
            }
    
            ELASTICSEARCH_CONTAINER.start();

            elasticsearchHost = useExternalElasticsearch ? "127.0.0.1" : ELASTICSEARCH_CONTAINER.getHost();
            elasticsearchPort = useExternalElasticsearch ? 9200 : ELASTICSEARCH_CONTAINER.getMappedPort(9200);
            elasticsearchClusterNodes = useExternalElasticsearch ? "127.0.0.1:9200" : ELASTICSEARCH_CONTAINER.getHttpHostAddress();

            System.out.println("Elasticsearch Test Container Started");

        }else {
            System.out.println("Using External Elasticsearch For Testing");
            ELASTICSEARCH_CONTAINER = null;
        }
    }


    @DynamicPropertySource
    static void registerElasticProperties(DynamicPropertyRegistry registry) {


        registry.add("spring.data.elasticsearch.cluster-nodes", () -> elasticsearchClusterNodes);
        registry.add("structures.elastic-connections[0].host", () -> elasticsearchHost);
        registry.add("structures.elastic-connections[0].port", () -> elasticsearchPort);
        registry.add("structures.elastic-connections[0].scheme", () -> "http");
        registry.add("elasticsearch.test.hostname", () -> elasticsearchHost);
        registry.add("elasticsearch.test.port", () -> elasticsearchPort);

    }

    protected StructureAndPersonHolder createAndVerify(){
        return createAndVerify(1,
                               true,
                               new DefaultEntityContext(new DummyParticipant()),
                               "_" + System.currentTimeMillis());
    }

    protected StructureAndPersonHolder createAndVerify(int numberOfPeopleToCreate,
                                                       boolean randomPeople,
                                                       EntityContext entityContext,
                                                       String structureSuffix){
        StructureAndPersonHolder ret = new StructureAndPersonHolder();

        StepVerifier.create(testHelper.createPersonStructureAndEntities(numberOfPeopleToCreate,
                                                                        randomPeople,
                                                                        entityContext,
                                                                        structureSuffix))
                    .expectNextMatches(structureAndPersonHolder -> {
                        boolean matches = structureAndPersonHolder.getStructure() != null &&
                                structureAndPersonHolder.getStructure().getId() != null &&
                                structureAndPersonHolder.getPersons().size() == numberOfPeopleToCreate;
                        if(matches){
                            ret.setStructure(structureAndPersonHolder.getStructure());
                            ret.setPersons(structureAndPersonHolder.getPersons());
                        }
                        return matches;
                    })
                    .verifyComplete();
        return ret;
    }
}
