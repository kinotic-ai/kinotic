package org.kinotic.test.tests.core.authz;

import dev.openfga.sdk.api.model.AuthorizationModel;
import dev.openfga.sdk.api.model.CheckRequest;
import dev.openfga.sdk.api.model.CheckRequestTupleKey;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.api.model.ReadRequestTupleKey;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.kinotic.app.api.services.ApplicationServiceDirectory;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AccessCheckService;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.authz.api.services.RolePermissionsService;
import org.kinotic.authz.internal.api.services.DefaultAccessCheckService;
import org.kinotic.authz.internal.api.services.DefaultAuthzStoreService;
import org.kinotic.authz.internal.api.services.DefaultObjectLockService;
import org.kinotic.authz.internal.api.services.DefaultRelationshipService;
import org.kinotic.authz.internal.api.services.DefaultRolePermissionsService;
import org.kinotic.authz.internal.api.services.OpenFgaService;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.RequestAuthorizer;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.model.persistence.idl.decorators.QueryDecorator;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.domain.api.model.security.DelegateType;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.NamedQueriesDefinitionService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.management.api.services.security.PermissionService;
import org.kinotic.management.internal.api.services.security.CallerPermissions;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.NamedQueryAuthorizer;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.parsers.MigrationParser;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Measures the authorization checks as the gateway, the MCP endpoint, the listings and the named query executor make
 * them, against the stack kinotic-test runs: the directory's contracts, the system store's reconciled model and
 * OpenFGA on Postgres. The graph is grown in stages around fixtures made through the services, and each stage
 * reports latency percentiles, throughput and the engine's own time, datastore queries and dispatches per call to the
 * directory {@code KINOTIC_AUTHZ_BENCHMARK_OUT} names. Runs only when {@code KINOTIC_AUTHZ_BENCHMARK} is {@code true}.
 * The engine runs with its caches as {@link org.kinotic.test.support.kinotic.KinoticTestComposeBoostrap} sets them.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "KINOTIC_AUTHZ_BENCHMARK", matches = "true")
public class AuthzPerformanceBenchmark extends KinoticTestBase {

    private static final String PROJECT_SERVICE = "management-api~org.kinotic.management.api.services.ProjectService";
    private static final String MEMBER_SERVICE = "management-api~org.kinotic.management.api.services.security.MemberService";
    private static final String ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.JsonEntitiesRepository";
    private static final String FIND_QUERY = "findPeople";

    private static final int WARMUP = intEnv("KINOTIC_AUTHZ_BENCHMARK_WARMUP", 200);
    private static final int ITERATIONS = intEnv("KINOTIC_AUTHZ_BENCHMARK_ITERATIONS", 1000);
    private static final List<Integer> ORGANIZATION_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_ORGS", "200,2000");
    private static final List<Integer> FAN_IN_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_FAN_IN", "100,1000");
    private static final List<Integer> LISTING_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_PROJECTS", "100,1000,5000");
    private static final List<Integer> TENANT_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_TENANTS", "1000");
    private static final List<Integer> TENANT_FAN_IN_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_TENANT_FAN_IN", "10,100,1000");
    private static final List<Integer> APPLICATION_FAN_IN_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_APPLICATION_FAN_IN", "100");
    private static final List<Integer> ENTITY_ROLE_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_ENTITY_ROLES", "1,100,1000,10000");
    private static final List<Integer> TENANT_ROLE_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_TENANT_ROLES", "2,10,25,50");
    private static final List<Integer> SERVICE_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_SERVICES", "10,1000,5000");
    private static final List<Integer> DELETION_STAGES = intsEnv("KINOTIC_AUTHZ_BENCHMARK_DELETION_TENANTS", "100,1000,5000");
    private static final String READING_ROWS = AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ);
    private static final String REPORT_NAMESPACE = "org.kinotic.benchmark";
    private static final String REPORT_SERVICE_NAME = REPORT_NAMESPACE + ".ReportService";
    private static final List<Integer> CONCURRENCY =intsEnv("KINOTIC_AUTHZ_BENCHMARK_CONCURRENCY", "1,8,32,64");
    // a measurement stops issuing requests once this much time has passed, after at least MINIMUM_ITERATIONS
    private static final long BUDGET_NANOS = TimeUnit.SECONDS.toNanos(intEnv("KINOTIC_AUTHZ_BENCHMARK_BUDGET_SECONDS", 30));
    private static final int MINIMUM_ITERATIONS = 20;

    @Autowired
    private RequestAuthorizer requestAuthorizer;
    @Autowired
    private NamedQueryAuthorizer namedQueryAuthorizer;
    @Autowired
    private MigrationParser migrationParser;
    @Autowired
    private RelationshipService relationships;
    @Autowired
    private AccessCheckService accessChecks;
    @Autowired
    private RolePermissionsService rolePermissions;
    @Autowired
    private OpenFgaService fga;
    @Autowired
    private KinoticAuthzProperties authzProperties;
    @Autowired
    private AuthzStoreRepository storeRecords;
    @Autowired
    private AuthzModelGenerator generator;
    @Autowired
    private CallerPermissions callerPermissions;
    @Autowired
    private PermissionService permissions;
    @Autowired
    private ApplicationAccessService access;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private EntityDefinitionService entityDefinitionService;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private NamedQueriesDefinitionService namedQueriesDefinitionService;
    @Autowired
    private ParticipantIdentityService identityService;
    @Autowired
    private TestDataService testDataService;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private ApplicationServiceDirectory applicationServiceDirectory;
    @Autowired
    private AuthzStoreService authzStores;
    @Autowired
    private AuthzModelService authzModels;

    private final StringBuilder report = new StringBuilder();
    private Path out;

    // fixtures made through the services
    private Application application;
    private Project hot;
    private Participant alice;      // project.editor on hot
    private Participant carol;      // a member with no grant
    private Participant dave;       // project.viewer on the organization
    private Participant aliceCli;   // alice's CLI delegate
    private ScopedParticipant bob;  // an application's user, entity_definition.viewer within its tenant
    private EntityDefinition person;
    private Statement select;
    private byte[] saveHot;
    private byte[] hotId;
    private String systemStoreId;
    private String systemModelId;
    private int organizationsSeeded;

    // application tier fixtures
    private String tenant;
    private String appStore;
    private String reportService;
    private ScopedParticipant erin;   // the definitions' viewer and report.viewer on the tenant
    private ScopedParticipant frank;  // a user of the tenant with no grant
    private int tenantUsersSeeded;

    @Test
    @Timeout(value = 4, unit = TimeUnit.HOURS)
    public void measure() throws Exception {
        out = Path.of(System.getenv().getOrDefault("KINOTIC_AUTHZ_BENCHMARK_OUT", "build/authz-benchmark"));
        Files.createDirectories(out);
        line("# Authorization benchmark " + Instant.now());
        line("");
        line("warmup " + WARMUP + ", iterations " + ITERATIONS + ", cpus " + Runtime.getRuntime().availableProcessors());
        fixtures();
        metrics("fixtures");

        stage("G0 fixtures only");
        coldAndRetryPaths();
        retentionRun();

        int organizations = 0;
        for (int target : ORGANIZATION_STAGES) {
            seedOrganizations(organizations, target);
            organizations = target;
            stage("G" + target + " bulk organizations (" + organizations * 62 + " tuples)");
        }
        throughput("after organizations");

        int fanIn = 0;
        for (int target : FAN_IN_STAGES) {
            seedFanIn(fanIn, target);
            fanIn = target;
            fanInStage(target);
        }

        int projects = 0;
        for (int target : LISTING_STAGES) {
            seedProjects(projects, target);
            projects = target;
            listingStage(target);
        }

        int tenants = 0;
        for (int target : TENANT_STAGES) {
            seedTenants(tenants, target);
            tenants = target;
            applicationStage(target);
        }

        throughput("final graph");
        metrics("final");
        // keeps the stack up for inspecting the engine by hand
        Thread.sleep(TimeUnit.SECONDS.toMillis(intEnv("KINOTIC_AUTHZ_BENCHMARK_HOLD_SECONDS", 0)));
    }

    /**
     * Measures the checks an application's users meet calling the application's services: the entities repository
     * and its named queries, and a service the application's runtime registered with a resource type of its own,
     * as one tenant's users each granted a role on the tenant grow in number.
     */
    @Test
    @Timeout(value = 4, unit = TimeUnit.HOURS)
    public void measureApplicationTier() throws Exception {
        out = Path.of(System.getenv().getOrDefault("KINOTIC_AUTHZ_BENCHMARK_OUT", "build/authz-benchmark"));
        Files.createDirectories(out);
        line("# Application tier benchmark " + Instant.now());
        line("");
        line("warmup " + WARMUP + ", iterations " + ITERATIONS + ", cpus " + Runtime.getRuntime().availableProcessors());
        applicationTierFixtures();
        metrics("app-fixtures");

        applicationTierStage("One tenant, two users granted on it");
        int granted = 0;
        for (int target : TENANT_FAN_IN_STAGES) {
            seedTenantGrants(granted, target);
            granted = target;
            applicationTierStage("One tenant, " + target + " more users each granted a role on it");
        }
        for (int target : APPLICATION_FAN_IN_STAGES) {
            seedApplicationGrants(target);
            applicationTierStage("Also " + target + " users of other tenants granted a role on the application");
        }
        applicationTierThroughput();
        metrics("app-final");
    }

    /**
     * Measures grants written as a bulk import writes them: one role on one resource to a different user each time,
     * which wait their turn on the one binding they join, beside the same grants made on a different resource each.
     */
    @Test
    @Timeout(value = 1, unit = TimeUnit.HOURS)
    public void measureGrants() throws Exception {
        out = Path.of(System.getenv().getOrDefault("KINOTIC_AUTHZ_BENCHMARK_OUT", "build/authz-benchmark"));
        Files.createDirectories(out);
        line("# Grant benchmark " + Instant.now());
        line("");
        line("warmup " + WARMUP + ", iterations " + ITERATIONS + ", cpus " + Runtime.getRuntime().availableProcessors());
        appStore = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);
        String role = AuthzNameUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER);
        String run = suffix();
        // creating the sample definition provisions the sample application's store
        personDefinition();
        section("Grants of one role");
        header();
        for (int concurrency : CONCURRENCY) {
            String onTenant = AuthzNameUtil.object(AuthzConstants.TENANT_TYPE, "bulk-" + run + "-c" + concurrency);
            AtomicInteger next = new AtomicInteger();
            row(measure("grant: on one tenant, a different user each time c=" + concurrency, concurrency,
                        () -> relationships.bind(appStore, role, AuthzGraphUtil.authzUser("bg" + next.getAndIncrement()), onTenant)));
        }
        for (int concurrency : CONCURRENCY) {
            AtomicInteger next = new AtomicInteger();
            row(measure("grant: on a different tenant each time c=" + concurrency, concurrency,
                        () -> relationships.bind(appStore, role, AuthzGraphUtil.authzUser("bg-" + run),
                                                 AuthzNameUtil.object(AuthzConstants.TENANT_TYPE, "bulk-" + run + "-c" + concurrency + "-" + next.getAndIncrement()))));
        }
        metrics("grants");
    }

    /**
     * Measures the check an application's user meets reading an entity's rows through a role of the application's
     * own: as the entities one role names grow in number, where one role names the entity read among the others and
     * one names only the others, and as the roles granted on one tenant grow in number.
     */
    @Test
    @Timeout(value = 1, unit = TimeUnit.HOURS)
    public void measureEntityRoles() throws Exception {
        out = Path.of(System.getenv().getOrDefault("KINOTIC_AUTHZ_BENCHMARK_OUT", "build/authz-benchmark"));
        Files.createDirectories(out);
        line("# Entity role benchmark " + Instant.now());
        line("");
        line("warmup " + WARMUP + ", iterations " + ITERATIONS + ", cpus " + Runtime.getRuntime().availableProcessors());
        person = personDefinition();
        appStore = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);
        for (int entities : ENTITY_ROLE_STAGES) {
            // a tenant of its own, so the roles of the other stages are not on the path of this one's checks
            tenant = createTenant(TEST_APP_ID);
            Map<String, Set<String>> others = namedEntities(entities);
            Map<String, Set<String>> naming = new HashMap<>(others);
            naming.remove(TEST_APP_ID + ".benchmarkentity0");
            naming.put(person.getId(), Set.of(READING_ROWS));
            String namingRole = AuthzNameUtil.roleId(AuthzConstants.TENANT_TYPE, "naming-" + entities + "-" + suffix());
            String otherRole = AuthzNameUtil.roleId(AuthzConstants.TENANT_TYPE, "others-" + entities + "-" + suffix());

            section("A role naming " + entities + " entities");
            long start = System.nanoTime();
            await(rolePermissions.ensureRole(appStore, namingRole, new RolePermissions(Set.of(), naming, Set.of())));
            line("role written in " + ms(System.nanoTime() - start) + " ms");
            await(rolePermissions.ensureRole(appStore, otherRole, new RolePermissions(Set.of(), others, Set.of())));
            start = System.nanoTime();
            require(await(rolePermissions.findRolePermissions(appStore, namingRole)).definitionPermissions().size() == entities,
                    "the role was not read back naming " + entities + " entities");
            line("role read back in " + ms(System.nanoTime() - start) + " ms");
            line("");
            entityRoleRows(holderOf(namingRole), holderOf(otherRole));
        }

        tenant = createTenant(TEST_APP_ID);
        String namingRole = AuthzNameUtil.roleId(AuthzConstants.TENANT_TYPE, "naming-" + suffix());
        String otherRole = AuthzNameUtil.roleId(AuthzConstants.TENANT_TYPE, "others-" + suffix());
        Map<String, Set<String>> naming = new HashMap<>(namedEntities(100));
        naming.put(person.getId(), Set.of(READING_ROWS));
        await(rolePermissions.ensureRole(appStore, namingRole, new RolePermissions(Set.of(), naming, Set.of())));
        await(rolePermissions.ensureRole(appStore, otherRole, new RolePermissions(Set.of(), namedEntities(100), Set.of())));
        ScopedParticipant holder = holderOf(namingRole);
        ScopedParticipant refused = holderOf(otherRole);
        int roles = 2;
        for (int target : TENANT_ROLE_STAGES) {
            for (; roles < target; roles++) {
                // each held by a user of the tenant other than the two measured
                holderOf(roleNamingOthers());
            }
            section(roles + " roles granted on one tenant, each naming 100 entities");
            entityRoleRows(holder, refused);
        }
        metrics("entity-roles");
    }

    /**
     * Measures deleting an entity definition and deleting a tenant as the tenants placed in the application's store
     * grow in number, with the definition's rows granted on in three of them, beside one read of the store for each
     * placed tenant.
     */
    @Test
    @Timeout(value = 1, unit = TimeUnit.HOURS)
    public void measureDeletions() throws Exception {
        out = Path.of(System.getenv().getOrDefault("KINOTIC_AUTHZ_BENCHMARK_OUT", "build/authz-benchmark"));
        Files.createDirectories(out);
        line("# Deletion benchmark " + Instant.now());
        line("");
        appStore = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);
        String application = AuthzGraphUtil.authzApplication(TEST_ORG_ID, TEST_APP_ID);
        String viewer = AuthzNameUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER);
        // creating the sample definition provisions the sample application's store
        person = personDefinition();
        String appStoreId = await(authzStores.ensureStore(appStore));
        line("| tenants placed | delete a definition, ms | delete a tenant, ms | one read for each placed tenant, ms |");
        line("|---:|---:|---:|---:|");
        String run = suffix();
        int placed = 0;
        for (int target : DELETION_STAGES) {
            List<RelationshipTuple> containments = new ArrayList<>();
            for (; placed < target; placed++) {
                containments.add(AuthzGraphUtil.tenantContainment(TEST_ORG_ID, TEST_APP_ID, "del-" + run + "-" + placed));
            }
            write(appStore, containments);
            List<String> tenants = await(fga.read(appStoreId, new ReadRequestTupleKey().user(application)._object(AuthzConstants.TENANT_TYPE + ":")))
                    .stream()
                    .filter(tuple -> AuthzConstants.APPLICATION_TYPE.equals(tuple.getKey().getRelation()))
                    .map(tuple -> tuple.getKey().getObject())
                    .toList();
            EntityDefinition car = await(runAsOrganization(() -> testDataService.createCarEntityDefinitionIfNotExists("Del" + run + target))).getLeft();
            for (int i = 0; i < 3; i++) {
                grantOnRows(car.getId(), "del-" + run + "-" + i, viewer);
            }
            await(runAsOrganization(() -> entityDefinitionService.unPublish(car.getId())));

            long start = System.nanoTime();
            awaitLong(runAsOrganization(() -> entityDefinitionService.deleteById(car.getId())));
            long definition = System.nanoTime() - start;

            Tenant tenant = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(TEST_APP_ID, "Deleted " + run + " " + target, "benchmark")));
            grantOnRows(person.getId(), tenant.getId(), viewer);
            start = System.nanoTime();
            awaitLong(runAsOrganization(() -> tenantService.deleteById(TEST_APP_ID, tenant.getId())));
            long deletedTenant = System.nanoTime() - start;

            start = System.nanoTime();
            for (String placedTenant : tenants) {
                await(fga.read(appStoreId, new ReadRequestTupleKey()._object(AuthzNameUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE,
                                                                                               AuthzNameUtil.tenantDefinitionId(car.getId(), AuthzNameUtil.idOf(placedTenant))))));
            }
            line("| " + tenants.size() + " | " + ms(definition) + " | " + ms(deletedTenant) + " | " + ms(System.nanoTime() - start) + " |");
        }
    }

    // A grant on a definition's rows within a tenant, with the edges the store holds for rows granted on
    private void grantOnRows(String definitionId, String tenantId, String role) throws Exception {
        await(relationships.ensure(appStore, AuthzGraphUtil.tenantDefinitionEdges(definitionId, tenantId)));
        await(relationships.bind(appStore, role, AuthzGraphUtil.authzUser("del-" + suffix()),
                                 AuthzNameUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzNameUtil.tenantDefinitionId(definitionId, tenantId))));
    }

    /**
     * Measures an application as the services its runtime registered grow in number, each declaring a type of its
     * own with three permissions: registering one more service, the time until a grant of its role admits a
     * request, generating the application's whole model and the model of one type, a pass of the store's
     * reconcile, the requests of a user to a service and to the entities, and saving and listing the application's
     * roles.
     */
    @Test
    @Timeout(value = 4, unit = TimeUnit.HOURS)
    public void measureServices() throws Exception {
        out = Path.of(System.getenv().getOrDefault("KINOTIC_AUTHZ_BENCHMARK_OUT", "build/authz-benchmark"));
        Files.createDirectories(out);
        line("# Application services benchmark " + Instant.now());
        line("");
        line("warmup " + WARMUP + ", iterations " + ITERATIONS + ", cpus " + Runtime.getRuntime().availableProcessors());
        person = personDefinition();
        appStore = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);
        String appStoreId = await(authzStores.ensureStore(appStore));
        String run = suffix().toLowerCase().replaceAll("[^a-z0-9]", "");
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, TEST_APP_ID);
        Participant runtime = new DefaultOrganizationParticipant(UUID.randomUUID().toString(), TEST_ORG_ID,
                                                                 Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                        ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                                 List.of());
        await(relationships.bind(AuthzStoreService.SYSTEM, AuthzConstants.APPLICATION_RUNTIME_ROLE,
                                 AuthzNameUtil.object(AuthzConstants.USER_TYPE, runtime.getId()),
                                 AuthzGraphUtil.authzApplication(TEST_ORG_ID, TEST_APP_ID)));
        byte[] noArguments = body(List.of());
        byte[] count = body(List.of(person.getId()));

        int registered = 0;
        for (int services : SERVICE_STAGES) {
            section(services + " services registered in the application");
            List<Long> registrations = new ArrayList<>();
            long seeding = System.nanoTime();
            for (; registered < services; registered++) {
                ServiceDirectoryEntry entry = new ServiceDirectoryEntry().setApplicationId(TEST_APP_ID)
                                                                         .setZone(zone)
                                                                         .setVersion("1.0.0")
                                                                         .setServiceDefinition(benchmarkService(run, registered));
                long start = System.nanoTime();
                awaitLong(runAs(runtime, () -> applicationServiceDirectory.register(entry)));
                registrations.add(System.nanoTime() - start);
            }
            long registeredAt = System.nanoTime();
            line("registered up to " + services + " services in " + ms(registeredAt - seeding) + " ms");
            row(Stats.of("register one service, the last " + Math.min(20, registrations.size()), 1,
                         registrations.subList(Math.max(0, registrations.size() - 20), registrations.size()), 0, 0));

            // a tenant of its own, so the roles granted in the other stages are not on the path of this one's checks
            tenant = createTenant(TEST_APP_ID);
            ScopedParticipant none = applicationParticipant(tenant, endUser(tenant).getId());
            // the newest service's viewer, granted through the access service, admits once the store has its role
            String newest = benchmarkType(run, services - 1);
            String newestService = zone + "~" + REPORT_NAMESPACE + "." + benchmarkServiceName(run, services - 1);
            ApplicationParticipantIdentity viewerIdentity = endUser(tenant);
            ScopedParticipant viewer = applicationParticipant(tenant, viewerIdentity.getId());
            Resource onTenant = new Resource(AuthzConstants.TENANT_TYPE, tenant);
            awaitLong(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, viewerIdentity.getId()),
                                                           AuthzNameUtil.roleId(newest, AuthzConstants.VIEWER), onTenant)));
            awaitLong(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, viewerIdentity.getId()),
                                                           AuthzNameUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER), onTenant)));
            criOf(newestService, "findItems", viewer);
            criOf(ENTITIES_SERVICE, "count", viewer);
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
            while (!admitted(newestService, "findItems", viewer, List.of())) {
                require(System.nanoTime() < deadline, "the newest service's viewer was never admitted");
                Thread.sleep(100);
            }
            line("the newest service's viewer was admitted " + ms(System.nanoTime() - registeredAt) + " ms after the last registration");

            long start = System.nanoTime();
            AuthzModel whole = awaitLong(authzModels.findApplicationModel(TEST_ORG_ID, TEST_APP_ID));
            line("the whole model, which the role and permission listings read, generated from the directory in " + ms(System.nanoTime() - start)
                         + " ms: " + whole.roles().size() + " roles, " + whole.servicePermissions().size() + " permissions of services");
            start = System.nanoTime();
            awaitLong(authzModels.findApplicationModelOf(TEST_ORG_ID, TEST_APP_ID, List.of(newest), List.of()));
            line("the model of the newest service's type, which a request and a grant read, generated in " + ms(System.nanoTime() - start) + " ms");
            start = System.nanoTime();
            AuthzModel model = awaitLong(authzModels.findApplicationModelOf(TEST_ORG_ID, TEST_APP_ID, List.of(), List.of()));
            awaitLong(rolePermissions.ensureModelWithRoles(appStore, model));
            line("a reconcile pass with every role in step took " + ms(System.nanoTime() - start) + " ms, for " + model.storedRoles().size()
                         + " roles the store holds");
            AuthorizationModel engineModel = await(fga.readAuthorizationModels(appStoreId, 1, null)).getAuthorizationModels().getFirst();
            line("the engine's model has " + engineModel.getTypeDefinitions().size() + " types and "
                         + jsonMapper.writeValueAsString(engineModel).length() + " bytes");

            // a role of the application's own bundling the reading permission of every service registered
            Set<String> reading = new HashSet<>();
            for (int i = 0; i < services; i++) {
                reading.add(AuthzUtil.permissionName(benchmarkType(run, i), AuthzConstants.CAN_READ));
            }
            start = System.nanoTime();
            RoleDefinition everyService = awaitLong(runAsOrganization(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                    null, "Reads every service " + suffix(), null, false, null, null, reading, Map.of(), false))));
            line("a role bundling " + reading.size() + " permissions of services saved in " + ms(System.nanoTime() - start) + " ms");
            ApplicationParticipantIdentity readerIdentity = endUser(tenant);
            ScopedParticipant reader = applicationParticipant(tenant, readerIdentity.getId());
            awaitLong(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, readerIdentity.getId()), everyService.id(), onTenant)));
            require(awaitUntil(() -> admitted(newestService, "findItems", reader, List.of())), "the reader of every service was never admitted");
            require(!admitted(newestService, "save", reader, List.of(Map.of("id", "i-1"))), "the reader of every service saved an item");
            require(!admitted(newestService, "findItems", none, List.of()), "a user with no grant listed items");
            require(awaitUntil(() -> admitted(ENTITIES_SERVICE, "count", viewer, List.of(person.getId()))), "the viewer never read the person rows");
            line("");
            header();
            row(measure("request: app service findItems, its viewer on the tenant (allow)", 1, () -> request(newestService, "findItems", viewer, noArguments)));
            row(measure("request: app service findItems, a role naming every service (allow)", 1, () -> request(newestService, "findItems", reader, noArguments)));
            row(measure("request: app service findItems, no grant (deny)", 1, () -> refusal(request(newestService, "findItems", none, noArguments))));
            row(measure("request: app service findItems, its viewer (allow) c=32", 32, () -> request(newestService, "findItems", viewer, noArguments)));
            row(measure("request: entities count, granted on the tenant (allow)", 1, () -> request(ENTITIES_SERVICE, "count", viewer, count)));
            row(measure("request: entities count, no grant (deny)", 1, () -> refusal(request(ENTITIES_SERVICE, "count", none, count))));
            Set<String> few = Set.of(AuthzUtil.permissionName(newest, AuthzConstants.CAN_READ), AuthzUtil.permissionName(newest, AuthzConstants.CAN_EDIT));
            row(measure("saveRole: a new role bundling two permissions of a service", 1, 20,
                        () -> runAsOrganization(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                                null, "Edits one service " + suffix(), null, false, null, null, few, Map.of(), false)))));
            row(measure("findRoles of the application", 1, 20, () -> runAsOrganization(() -> access.findRoles(TEST_APP_ID))));
            row(measure("findPermissions of the application", 1, 20, () -> runAsOrganization(() -> access.findPermissions(TEST_APP_ID))));
            metrics("services " + services);
        }
    }

    private static String benchmarkType(String run, int index) {
        return "bench" + run + "n" + index;
    }

    private static String benchmarkServiceName(String run, int index) {
        return "Bench" + run + "N" + index + "Service";
    }

    // A service an application's runtime registers with a type of its own under the tenant, whose functions derive
    // can_read, can_edit and can_delete
    private static ServiceDefinition benchmarkService(String run, int index) {
        ServiceDefinition ret = new ServiceDefinition().setNamespace(REPORT_NAMESPACE).setName(benchmarkServiceName(run, index));
        ret.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType(benchmarkType(run, index)).setParent("tenant")));
        ret.addFunction(new FunctionDefinition().setName("findItems"));
        ret.addFunction(new FunctionDefinition().setName("findById").addParameter("id", new StringC3Type()));
        ret.addFunction(new FunctionDefinition().setName("save").addParameter("entity", new ObjectC3Type().setName("Item").addProperty("id", new StringC3Type())));
        ret.addFunction(new FunctionDefinition().setName("deleteById").addParameter("id", new StringC3Type()));
        return ret;
    }

    private void entityRoleRows(ScopedParticipant holder, ScopedParticipant refused) throws Exception {
        byte[] count = body(List.of(person.getId()));
        criOf(ENTITIES_SERVICE, "count", holder);
        require(awaitUntil(() -> admitted(ENTITIES_SERVICE, "count", holder, List.of(person.getId()))), "the holder never read the rows its role names");
        require(!admitted(ENTITIES_SERVICE, "count", refused, List.of(person.getId())), "a role read rows it does not name");
        header();
        row(measure("request: entities count, named by the role (allow)", 1, () -> request(ENTITIES_SERVICE, "count", holder, count)));
        row(measure("request: entities count, not named by the role (deny)", 1, () -> refusal(request(ENTITIES_SERVICE, "count", refused, count))));
        row(measure("request: entities count, named by the role (allow) c=32", 32, () -> request(ENTITIES_SERVICE, "count", holder, count)));
        row(measure("request: entities count, not named by the role (deny) c=32", 32, () -> refusal(request(ENTITIES_SERVICE, "count", refused, count))));
    }

    // Reading the rows of entities the sample application does not have, as a role names them
    private static Map<String, Set<String>> namedEntities(int count) {
        Map<String, Set<String>> ret = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ret.put(TEST_APP_ID + ".benchmarkentity" + i, Set.of(READING_ROWS));
        }
        return ret;
    }

    private String roleNamingOthers() throws Exception {
        String ret = AuthzNameUtil.roleId(AuthzConstants.TENANT_TYPE, "others-" + suffix());
        await(rolePermissions.ensureRole(appStore, ret, new RolePermissions(Set.of(), namedEntities(100), Set.of())));
        return ret;
    }

    // A user of the tenant granted the role on it
    private ScopedParticipant holderOf(String roleId) throws Exception {
        ApplicationParticipantIdentity identity = endUser(tenant);
        await(relationships.bind(appStore, roleId, AuthzGraphUtil.authzUser(identity.getId()), AuthzNameUtil.object(AuthzConstants.TENANT_TYPE, tenant)));
        return applicationParticipant(tenant, identity.getId());
    }

    // ---------------------------------------------------------------- application tier

    private void applicationTierFixtures() throws Exception {
        person = personDefinition();
        tenant = createTenant(TEST_APP_ID);
        ApplicationParticipantIdentity erinIdentity = endUser(tenant);
        ApplicationParticipantIdentity frankIdentity = endUser(tenant);
        erin = applicationParticipant(tenant, erinIdentity.getId());
        frank = applicationParticipant(tenant, frankIdentity.getId());
        appStore = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);

        // the application's runtime registers its own service, as a deployment's runtime machine does
        Participant runtime = new DefaultOrganizationParticipant(UUID.randomUUID().toString(), TEST_ORG_ID,
                                                                 Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                        ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                                 List.of());
        await(relationships.bind(AuthzStoreService.SYSTEM, AuthzConstants.APPLICATION_RUNTIME_ROLE,
                                 AuthzNameUtil.object(AuthzConstants.USER_TYPE, runtime.getId()),
                                 AuthzGraphUtil.authzApplication(TEST_ORG_ID, TEST_APP_ID)));
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, TEST_APP_ID);
        ServiceDirectoryEntry entry = new ServiceDirectoryEntry().setApplicationId(TEST_APP_ID)
                                                                 .setZone(zone)
                                                                 .setVersion("1.0.0")
                                                                 .setServiceDefinition(registeredReportService());
        require(once(() -> await(runAs(runtime, () -> applicationServiceDirectory.register(entry)))),
                "the runtime never registered the report service");
        reportService = zone + "~" + REPORT_SERVICE_NAME;

        Resource onTenant = new Resource(AuthzConstants.TENANT_TYPE, tenant);
        await(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, erinIdentity.getId()),
                                                   AuthzNameUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER), onTenant)));
        // the application's store takes the report's roles once it reconciles to the model the registration implies
        require(once(() -> await(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, erinIdentity.getId()),
                                                                       "report.viewer", onTenant)))),
                "report.viewer was never grantable in the application's store");

        criOf(ENTITIES_SERVICE, "count", erin);
        criOf(ENTITIES_SERVICE, "namedQuery", erin);
        criOf(reportService, "findReports", erin);
        criOf(reportService, "findById", erin);
        require(awaitUntil(() -> admitted(ENTITIES_SERVICE, "count", erin, List.of(person.getId()))), "erin never read the person rows");
        require(awaitUntil(() -> admitted(reportService, "findReports", erin, List.of())), "erin never listed reports");
        require(!admitted(ENTITIES_SERVICE, "count", frank, List.of(person.getId())), "frank read the person rows");
        require(!admitted(reportService, "findReports", frank, List.of()), "frank listed reports");
        line("");
        line("tenant " + tenant + ", application store " + appStore);
        line("erin: entity_definition.viewer and report.viewer on the tenant; frank: a user of the tenant with no grant");
        line("erin findById(a report id) admitted: " + admitted(reportService, "findById", erin, List.of("r-" + suffix())));
        String appStoreId = await(authzStores.ensureStore(appStore));
        Files.writeString(out.resolve("application-model.json"),
                          jsonMapper.writeValueAsString(await(fga.readAuthorizationModels(appStoreId, 1, null)).getAuthorizationModels().getFirst()));
    }

    private void applicationTierStage(String name) throws Exception {
        section(name);
        header();
        byte[] count = body(List.of(person.getId()));
        byte[] none = body(List.of());
        row(measure("request: entities count, granted on the tenant (allow)", 1, () -> request(ENTITIES_SERVICE, "count", erin, count)));
        row(measure("request: entities count, no grant (deny)", 1, () -> refusal(request(ENTITIES_SERVICE, "count", frank, count))));
        row(measure("request: named query, granted on the tenant (allow)", 1,
                    () -> request(ENTITIES_SERVICE, "namedQuery", erin, body(List.of(person.getId(), FIND_QUERY, List.of())))));
        row(measure("request: app service findReports on the tenant (allow)", 1, () -> request(reportService, "findReports", erin, none)));
        row(measure("request: app service findReports, no grant (deny)", 1, () -> refusal(request(reportService, "findReports", frank, none))));
        if (tenantUsersSeeded > 0) {
            AtomicInteger next = new AtomicInteger();
            row(measure("request: entities count, a different granted user each time (allow)", 1,
                        () -> request(ENTITIES_SERVICE, "count", applicationParticipant(tenant, "at" + (next.getAndIncrement() % tenantUsersSeeded)), count)));
        }
        AtomicInteger stranger = new AtomicInteger();
        row(measure("request: entities count, a different ungranted user each time (deny)", 1,
                    () -> refusal(request(ENTITIES_SERVICE, "count", applicationParticipant(tenant, "nobody-" + suffix() + stranger.getAndIncrement()), count))));
        row(measure("request: entities count, no grant (deny) c=32", 32, () -> refusal(request(ENTITIES_SERVICE, "count", frank, count))));
        metrics("app " + name);
    }

    private void applicationTierThroughput() throws Exception {
        section("Application tier throughput, final graph");
        header();
        byte[] count = body(List.of(person.getId()));
        for (int concurrency : CONCURRENCY) {
            row(measure("request: entities count, granted (allow) c=" + concurrency, concurrency, () -> request(ENTITIES_SERVICE, "count", erin, count)));
        }
        for (int concurrency : CONCURRENCY) {
            AtomicInteger next = new AtomicInteger();
            row(measure("request: entities count, a different granted user each time c=" + concurrency, concurrency,
                        () -> request(ENTITIES_SERVICE, "count", applicationParticipant(tenant, "at" + (next.getAndIncrement() % Math.max(1, tenantUsersSeeded))), count)));
        }
        for (int concurrency : CONCURRENCY) {
            row(measure("request: entities count, no grant (deny) c=" + concurrency, concurrency,
                        () -> refusal(request(ENTITIES_SERVICE, "count", frank, count))));
        }
        metrics("app throughput");
    }

    // Users of the tenant, each granted the definitions' viewer on the tenant as ApplicationAccessService writes a grant
    private void seedTenantGrants(int from, int to) throws Exception {
        long start = System.nanoTime();
        String app = AuthzGraphUtil.authzApplication(TEST_ORG_ID, TEST_APP_ID);
        String onTenant = AuthzNameUtil.object(AuthzConstants.TENANT_TYPE, tenant);
        List<RelationshipTuple> tuples = new ArrayList<>();
        for (int i = from; i < to; i++) {
            String user = AuthzGraphUtil.authzUser("at" + i);
            tuples.add(new RelationshipTuple(user, AuthzConstants.END_USER_RELATION, app));
            tuples.add(new RelationshipTuple(user, AuthzConstants.MEMBER_RELATION, onTenant));
            tuples.addAll(binding(AuthzNameUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER), user, onTenant));
        }
        write(appStore, tuples);
        tenantUsersSeeded = to;
        line("");
        line("seeded tenant users " + from + ".." + to + ": " + tuples.size() + " tuples in " + ms(System.nanoTime() - start) + " ms");
    }

    // Users of other tenants granted the application's admin on the application, such as the application's own staff
    private void seedApplicationGrants(int count) throws Exception {
        long start = System.nanoTime();
        String app = AuthzGraphUtil.authzApplication(TEST_ORG_ID, TEST_APP_ID);
        List<RelationshipTuple> tuples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String user = AuthzGraphUtil.authzUser("aa" + i + "-" + suffix());
            tuples.add(new RelationshipTuple(user, AuthzConstants.END_USER_RELATION, app));
            tuples.addAll(binding(AuthzNameUtil.roleId(AuthzConstants.APPLICATION_TYPE, AuthzConstants.ADMIN), user, app));
        }
        write(appStore, tuples);
        line("");
        line("seeded application grants: " + tuples.size() + " tuples in " + ms(System.nanoTime() - start) + " ms");
    }

    // Retries the action until it succeeds once, for up to thirty seconds
    private static boolean once(java.util.concurrent.Callable<?> action) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        return awaitUntil(() -> {
            if (!done.get()) {
                try {
                    action.call();
                    done.set(true);
                } catch (Exception e) {
                    System.out.println("[authz-benchmark] retrying: " + e);
                }
            }
            return done.get();
        });
    }

    // A service an application's runtime registers, as kinotic sync generates it from a class declaring
    // @AuthzResource({value: 'report', parent: 'tenant'}): each function's check is derived when it registers
    private static ServiceDefinition registeredReportService() {
        ServiceDefinition ret = new ServiceDefinition().setNamespace(REPORT_NAMESPACE).setName("ReportService");
        ret.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType("report").setParent("tenant")));
        ret.addFunction(new FunctionDefinition().setName("findReports"));
        ret.addFunction(new FunctionDefinition().setName("findById").addParameter("id", new StringC3Type()));
        return ret;
    }

    // ---------------------------------------------------------------- fixtures

    private void fixtures() throws Exception {
        application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Benchmark " + suffix(), "benchmark", null)));
        hot = project(application, "Hot");
        OrganizationParticipantIdentity aliceIdentity = member(TEST_ORG_ID, "alice");
        OrganizationParticipantIdentity carolIdentity = member(TEST_ORG_ID, "carol");
        OrganizationParticipantIdentity daveIdentity = member(TEST_ORG_ID, "dave");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, aliceIdentity.getId()), "project.editor",
                                                        new Resource(ProjectService.RESOURCE_TYPE, hot.getId()))));
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, daveIdentity.getId()), "project.viewer",
                                                        new Resource(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID))));
        alice = participant(aliceIdentity.getId(), Map.of());
        carol = participant(carolIdentity.getId(), Map.of());
        dave = participant(daveIdentity.getId(), Map.of());
        aliceCli = participant("cli-" + suffix(), Map.of(DomainUtil.ON_BEHALF_OF_METADATA_KEY, aliceIdentity.getId(),
                                                         DomainUtil.DELEGATE_TYPE_METADATA_KEY, DelegateType.CLI.name()));
        saveHot = body(List.of(hot));
        hotId = body(List.of(hot.getId()));

        person = personDefinition();
        String tenantId = createTenant(TEST_APP_ID);
        ApplicationParticipantIdentity bobIdentity = endUser(tenantId);
        bob = applicationParticipant(tenantId, bobIdentity.getId());
        Resource personHere = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzNameUtil.tenantDefinitionId(person.getId(), tenantId));
        await(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, bobIdentity.getId()),
                                                   AuthzNameUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER), personHere)));
        for (String function : List.of("findById", "save", "findDependencies")) {
            criOf(PROJECT_SERVICE, function, alice);
        }
        criOf(MEMBER_SERVICE, "findMembers", TEST_ORGANIZATION_PARTICIPANT);
        criOf(ENTITIES_SERVICE, "count", bob);
        criOf(ENTITIES_SERVICE, "namedQuery", bob);
        select = migrationParser.parseStatement("SELECT * FROM %s WHERE lastName = :lastName".formatted(person.getName()), "benchmark");

        systemStoreId = await(fga.listStores(100, null, "kinotic-system")).getStores().getFirst().getId();
        systemModelId = await(fga.readAuthorizationModels(systemStoreId, 1, null)).getAuthorizationModels().getFirst().getId();

        // every fixture is visible to its check before anything is timed
        require(awaitUntil(() -> admitted(PROJECT_SERVICE, "save", alice, List.of(hot))), "alice never edited the hot project");
        require(awaitUntil(() -> admitted(PROJECT_SERVICE, "findDependencies", dave, List.of(hot.getId()))), "dave never read the hot project");
        require(awaitUntil(() -> admitted(ENTITIES_SERVICE, "count", bob, List.of(person.getId()))), "bob never read the person rows");
        require(awaitUntil(() -> admitted(ENTITIES_SERVICE, "namedQuery", bob, List.of(person.getId(), FIND_QUERY, List.of()))),
                "bob never ran the named query");
        require(!admitted(PROJECT_SERVICE, "save", carol, List.of(hot)), "carol edited the hot project");
        line("");
        line("system store " + systemStoreId + ", model " + systemModelId);
        line("system model types: " + await(fga.readAuthorizationModels(systemStoreId, 1, null)).getAuthorizationModels().getFirst()
                                                                                               .getTypeDefinitions().size());
        Files.writeString(out.resolve("system-model.json"),
                          jsonMapper.writeValueAsString(await(fga.readAuthorizationModels(systemStoreId, 1, null)).getAuthorizationModels().getFirst()));
    }

    // ---------------------------------------------------------------- stages

    private void stage(String name) throws Exception {
        section(name);
        header();
        row(measure("unchecked function (ProjectService.findById), no engine call", 1, () -> request(PROJECT_SERVICE, "findById", alice, hotId)));
        row(measure("engine floor: GET store (HTTP round trip)", 1, () -> fga.getStore(systemStoreId)));
        row(measure("engine: raw Check, project grant (allow)", 1, () -> rawCheck(alice, "project_can_edit", hotObject(), ConsistencyPreference.MINIMIZE_LATENCY)));
        row(measure("RelationshipService.check, project grant (allow)", 1,
                    () -> accessChecks.check(AuthzStoreService.SYSTEM, tuple(alice, "project_can_edit", hotObject()), Consistency.MINIMIZE_LATENCY)));
        row(measure("request: save, editor of the project (allow)", 1, () -> request(PROJECT_SERVICE, "save", alice, saveHot)));
        row(measure("request: save, member without grant (deny)", 1, () -> refusal(request(PROJECT_SERVICE, "save", carol, saveHot))));
        row(measure("request: findDependencies, viewer on the organization (allow)", 1,
                    () -> request(PROJECT_SERVICE, "findDependencies", dave, hotId)));
        row(measure("request: save, CLI delegate of the editor (allow)", 1, () -> request(PROJECT_SERVICE, "save", aliceCli, saveHot)));
        row(measure("request: findMembers, organization admin (allow)", 1,
                    () -> request(MEMBER_SERVICE, "findMembers", TEST_ORGANIZATION_PARTICIPANT, body(Arrays.asList(null, Map.of("pageNumber", 0, "pageSize", 10))))));
        row(measure("tool call: save by name, editor (allow)", 1, () -> toolCall(PROJECT_SERVICE, "save", alice, Map.of("entity", hot))));
        row(measure("request: app user count on a definition in its tenant (allow)", 1,
                    () -> request(ENTITIES_SERVICE, "count", bob, body(List.of(person.getId())))));
        row(measure("request: app user named query (resolver + check)", 1,
                    () -> request(ENTITIES_SERVICE, "namedQuery", bob, body(List.of(person.getId(), FIND_QUERY, List.of())))));
        row(measure("executor: NamedQueryAuthorizer statement check", 1,
                    () -> namedQueryAuthorizer.authorize(bob, person.getId(), select, false)));
        row(measure("CallerPermissions.findAccessibleIds, org-wide viewer (one check)", 1,
                    () -> runAs(dave, () -> callerPermissions.findAccessibleIds(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))));
        row(measure("CallerPermissions.findAccessibleIds, one grant (check + listing)", 1,
                    () -> runAs(alice, () -> callerPermissions.findAccessibleIds(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))));
        row(measure("request: save, editor, HIGHER_CONSISTENCY check", 1,
                    () -> accessChecks.check(AuthzStoreService.SYSTEM, tuple(alice, "project_can_edit", hotObject()), Consistency.HIGHER_CONSISTENCY)));
        // a different caller and project each time, so the engine's cache holds no answer for the check itself
        if (organizationsSeeded > 0) {
            AtomicInteger next = new AtomicInteger();
            row(measure("RelationshipService.check, a different editor and project each time (allow)", 1, () -> {
                int i = next.getAndIncrement() % organizationsSeeded;
                return accessChecks.check(AuthzStoreService.SYSTEM,
                                           new RelationshipTuple(AuthzGraphUtil.authzUser("bo" + i + "u1"), "project_can_edit",
                                                                 AuthzNameUtil.object(AuthzConstants.PROJECT_TYPE, AuthzGraphUtil.authzId(AuthzConstants.PROJECT_TYPE, "bo" + i, "p0x0"))),
                                           Consistency.MINIMIZE_LATENCY);
            }));
        }
        AtomicInteger stranger = new AtomicInteger();
        row(measure("RelationshipService.check, a different unknown user each time (deny)", 1,
                    () -> accessChecks.check(AuthzStoreService.SYSTEM, new RelationshipTuple(AuthzGraphUtil.authzUser("nobody-" + suffix() + stranger.getAndIncrement()),
                                                                                              "project_can_edit", hotObject()), Consistency.MINIMIZE_LATENCY)));
        metrics(name);
    }

    private void throughput(String name) throws Exception {
        section("Throughput, " + name);
        header();
        for (int concurrency : CONCURRENCY) {
            row(measure("request: save, editor (allow) c=" + concurrency, concurrency, () -> request(PROJECT_SERVICE, "save", alice, saveHot)));
        }
        for (int concurrency : CONCURRENCY) {
            row(measure("request: save, no grant (deny) c=" + concurrency, concurrency, () -> refusal(request(PROJECT_SERVICE, "save", carol, saveHot))));
        }
        for (int concurrency : CONCURRENCY) {
            row(measure("request: app user count c=" + concurrency, concurrency,
                        () -> request(ENTITIES_SERVICE, "count", bob, body(List.of(person.getId())))));
        }
        for (int concurrency : CONCURRENCY) {
            row(measure("engine: raw Check c=" + concurrency, concurrency,
                        () -> rawCheck(alice, "project_can_edit", hotObject(), ConsistencyPreference.MINIMIZE_LATENCY)));
        }
        metrics("throughput " + name);
    }

    private void fanInStage(int bindings) throws Exception {
        section("Fan-in: " + bindings + " bindings on the project and " + bindings + " on the organization");
        header();
        row(measure("request: save, editor (allow)", 1, () -> request(PROJECT_SERVICE, "save", alice, saveHot)));
        row(measure("request: save, no grant (deny)", 1, () -> refusal(request(PROJECT_SERVICE, "save", carol, saveHot))));
        row(measure("request: findDependencies, org viewer (allow)", 1, () -> request(PROJECT_SERVICE, "findDependencies", dave, hotId)));
        row(measure("request: findMembers, org admin (allow)", 1,
                    () -> request(MEMBER_SERVICE, "findMembers", TEST_ORGANIZATION_PARTICIPANT, body(Arrays.asList(null, Map.of("pageNumber", 0, "pageSize", 10))))));
        row(measure("request: save, no grant (deny) c=32", 32, () -> refusal(request(PROJECT_SERVICE, "save", carol, saveHot))));
        row(measure("RelationshipService.check deny, HIGHER_CONSISTENCY", 1,
                    () -> accessChecks.check(AuthzStoreService.SYSTEM, tuple(carol, "project_can_edit", hotObject()), Consistency.HIGHER_CONSISTENCY)));
        AtomicInteger next = new AtomicInteger();
        row(measure("RelationshipService.check, a different editor of the project each time (allow)", 1,
                    () -> accessChecks.check(AuthzStoreService.SYSTEM, new RelationshipTuple(AuthzGraphUtil.authzUser("bf" + (next.getAndIncrement() % bindings)),
                                                                                              "project_can_edit", hotObject()), Consistency.MINIMIZE_LATENCY)));
        AtomicInteger stranger = new AtomicInteger();
        row(measure("RelationshipService.check, a different unknown user each time (deny)", 1,
                    () -> accessChecks.check(AuthzStoreService.SYSTEM, new RelationshipTuple(AuthzGraphUtil.authzUser("nobody-" + suffix() + stranger.getAndIncrement()),
                                                                                              "project_can_edit", hotObject()), Consistency.MINIMIZE_LATENCY)));
        row(measure("RelationshipService.check, a different unknown user each time (deny) c=32", 32,
                    () -> accessChecks.check(AuthzStoreService.SYSTEM, new RelationshipTuple(AuthzGraphUtil.authzUser("nobody-" + suffix() + stranger.getAndIncrement()),
                                                                                              "project_can_edit", hotObject()), Consistency.MINIMIZE_LATENCY)));
        metrics("fan-in " + bindings);
    }

    private void listingStage(int projects) throws Exception {
        section("Listing: " + projects + " more projects in the application; alice granted on " + Math.min(projects, 100) / 2 + " of them");
        header();
        int seen = await(runAs(dave, () -> callerPermissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))).size();
        int aliceSees = await(runAs(alice, () -> callerPermissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))).size();
        line("dave lists " + seen + " projects, alice " + aliceSees);
        line("");
        int iterations = Math.max(20, Math.min(ITERATIONS, 200_000 / Math.max(1, projects)));
        row(measure("listAccessible, org-wide viewer (" + seen + " objects)", 1, iterations,
                    () -> runAs(dave, () -> callerPermissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))));
        row(measure("listAccessible, direct grants (" + aliceSees + " objects)", 1, iterations,
                    () -> runAs(alice, () -> callerPermissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))));
        row(measure("listAccessible, no grant (0 objects)", 1, iterations,
                    () -> runAs(carol, () -> callerPermissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))));
        row(measure("listObjects org-wide viewer, HIGHER_CONSISTENCY", 1, iterations,
                    () -> accessChecks.listObjects(AuthzStoreService.SYSTEM, AuthzGraphUtil.authzUser(dave), "project_can_read", "project",
                                                    Consistency.HIGHER_CONSISTENCY)));
        row(measure("listAccessible, org-wide viewer c=16", 16, iterations,
                    () -> runAs(dave, () -> callerPermissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ))));
        row(measure("request: findDependencies, org viewer (allow)", 1, () -> request(PROJECT_SERVICE, "findDependencies", dave, hotId)));
        metrics("listing " + projects);
    }

    private void applicationStage(int tenants) throws Exception {
        section("Application store: " + tenants + " more tenants, 10 users and one grant each");
        header();
        row(measure("request: app user count (allow)", 1, () -> request(ENTITIES_SERVICE, "count", bob, body(List.of(person.getId())))));
        row(measure("request: app user named query (allow)", 1,
                    () -> request(ENTITIES_SERVICE, "namedQuery", bob, body(List.of(person.getId(), FIND_QUERY, List.of())))));
        ScopedParticipant stranger = applicationParticipant("bt0", "bt0u5");
        row(measure("request: app user of another tenant, no grant (deny)", 1,
                    () -> refusal(request(ENTITIES_SERVICE, "count", stranger, body(List.of(person.getId()))))));
        row(measure("request: app user count c=32", 32, () -> request(ENTITIES_SERVICE, "count", bob, body(List.of(person.getId())))));
        metrics("application " + tenants);
    }

    // The paths of the client's own caching: a node resolving the store and its model for the first time, and a node
    // whose kept model version lacks the relation asked about, which the engine refuses and the newest answers
    private void coldAndRetryPaths() throws Exception {
        section("Client cache paths");
        header();
        RelationshipTuple editing = tuple(alice, "project_can_edit", hotObject());
        row(measure("first check of a node (list stores + read model + check)", 1, 30, () -> {
            DefaultAuthzStoreService node = new DefaultAuthzStoreService(fga, authzProperties, vertx, storeRecords);
            return new DefaultAccessCheckService(fga, node).check(AuthzStoreService.SYSTEM, editing, Consistency.MINIMIZE_LATENCY);
        }));

        String store = "bench-grown-" + suffix();
        AuthzStoreService stores = new DefaultAuthzStoreService(fga, authzProperties, vertx, storeRecords);
        await(stores.ensureStore(store));
        DefaultAuthzStoreService writing = (DefaultAuthzStoreService) stores;
        RelationshipService writer = new DefaultRelationshipService(fga, writing, new DefaultObjectLockService(vertx));
        AuthzModel kernel = generator.applicationModel(List.of(), List.of());
        AuthzModel grown = generator.applicationModel(List.of(tenantService()), List.of());
        await(new DefaultRolePermissionsService(fga, writing).ensureModelWithRoles(store, grown));
        await(writer.bind(store, "tenant.viewer", "user:bob", "tenant:t1"));
        RelationshipTuple reading = new RelationshipTuple("user:bob", "tenant_can_read", "tenant:t1");
        List<Long> retried = new ArrayList<>();
        List<Long> steady = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            await(stores.ensureModel(store, kernel));
            DefaultAuthzStoreService node = new DefaultAuthzStoreService(fga, authzProperties, vertx, storeRecords);
            DefaultAccessCheckService nodeChecks = new DefaultAccessCheckService(fga, node);
            await(node.modelId(store));
            await(stores.ensureModel(store, grown));
            long start = System.nanoTime();
            require(await(nodeChecks.check(store, reading, Consistency.MINIMIZE_LATENCY)), "the retried check was refused");
            retried.add(System.nanoTime() - start);
            start = System.nanoTime();
            await(nodeChecks.check(store, reading, Consistency.MINIMIZE_LATENCY));
            steady.add(System.nanoTime() - start);
        }
        row(Stats.of("check on a stale kept model (400, re-read model, retry)", 1, retried, 0, 0));
        row(Stats.of("same node's next check (newest model kept)", 1, steady, 0, 0));
        await(stores.deleteStore(store));
    }

    // Runs the editor's request at a steady rate for longer than the store id and model version retention, so the
    // requests that pay for a refresh show in the tail
    private void retentionRun() throws Exception {
        section("Retention run: 75 s at concurrency 1, slowest requests by time");
        List<long[]> samples = new ArrayList<>();
        Semaphore inFlight = new Semaphore(1);
        long begin = System.nanoTime();
        long end = begin + TimeUnit.SECONDS.toNanos(75);
        Context[] contexts = contexts(1);
        AtomicInteger i = new AtomicInteger();
        while (System.nanoTime() < end) {
            inFlight.acquire();
            long start = System.nanoTime();
            onContext(contexts[i.getAndIncrement() % contexts.length], () -> request(PROJECT_SERVICE, "save", alice, saveHot))
                    .onComplete(r -> {
                        synchronized (samples) {
                            samples.add(new long[]{start - begin, System.nanoTime() - start, r.succeeded() ? 1 : 0});
                        }
                        inFlight.release();
                    });
        }
        inFlight.acquire(1);
        List<Long> latencies = samples.stream().map(s -> s[1]).toList();
        Stats all = Stats.of("save, editor, 75 s", 1, latencies, System.nanoTime() - begin, samples.stream().filter(s -> s[2] == 0).count());
        header();
        row(all);
        line("");
        line("requests slower than 3x the median, by second of the run:");
        long threshold = (long) (all.p50 * 3_000_000);
        Map<Long, Integer> bySecond = new java.util.TreeMap<>();
        Map<Long, Long> worst = new HashMap<>();
        for (long[] s : samples) {
            if (s[1] > threshold) {
                long second = TimeUnit.NANOSECONDS.toSeconds(s[0]);
                bySecond.merge(second, 1, Integer::sum);
                worst.merge(second, s[1], Math::max);
            }
        }
        bySecond.forEach((second, count) -> line("- s" + second + ": " + count + " slow, worst " + ms(worst.get(second)) + " ms"));
        StringBuilder csv = new StringBuilder("startMs,latencyUs\n");
        for (long[] s : samples) {
            csv.append(s[0] / 1_000_000).append(',').append(s[1] / 1_000).append('\n');
        }
        Files.writeString(out.resolve("retention-run.csv"), csv);
    }

    // ---------------------------------------------------------------- seeding

    // Organizations as the services write them: the organization on the system, three applications of ten projects
    // each, one member administering the organization and five editing a project each
    private void seedOrganizations(int from, int to) throws Exception {
        long start = System.nanoTime();
        List<RelationshipTuple> tuples = new ArrayList<>();
        for (int i = from; i < to; i++) {
            String organization = AuthzNameUtil.object(AuthzConstants.ORGANIZATION_TYPE, "bo" + i);
            tuples.add(AuthzGraphUtil.organizationContainment("bo" + i));
            tuples.addAll(binding(AuthzConstants.ORGANIZATION_ADMIN_ROLE, AuthzGraphUtil.authzUser("bo" + i + "u0"), organization));
            for (int a = 0; a < 3; a++) {
                String app = AuthzGraphUtil.authzApplication("bo" + i, "a" + a);
                tuples.add(AuthzGraphUtil.containment(organization, app));
                for (int p = 0; p < 10; p++) {
                    String project = AuthzNameUtil.object(AuthzConstants.PROJECT_TYPE, AuthzGraphUtil.authzId(AuthzConstants.PROJECT_TYPE, "bo" + i, "p" + a + "x" + p));
                    tuples.add(AuthzGraphUtil.containment(app, project));
                    if (a == 0 && p < 5) {
                        tuples.addAll(binding("project.editor", AuthzGraphUtil.authzUser("bo" + i + "u" + (p + 1)), project));
                    }
                }
            }
        }
        write(AuthzStoreService.SYSTEM, tuples);
        organizationsSeeded = to;
        line("");
        line("seeded organizations " + from + ".." + to + ": " + tuples.size() + " tuples in " + ms(System.nanoTime() - start) + " ms");
    }

    // Other members granted the project editor on the hot project, and other members granted the organization's
    // viewer on the test organization, as the permission service writes those grants
    private void seedFanIn(int from, int to) throws Exception {
        long start = System.nanoTime();
        List<RelationshipTuple> tuples = new ArrayList<>();
        String organization = AuthzNameUtil.object(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID);
        for (int i = from; i < to; i++) {
            String user = AuthzGraphUtil.authzUser("bf" + i);
            tuples.addAll(binding("project.editor", user, hotObject()));
            tuples.addAll(binding("organization.viewer", user, organization));
        }
        write(AuthzStoreService.SYSTEM, tuples);
        line("");
        line("seeded fan-in " + from + ".." + to + ": " + tuples.size() + " tuples in " + ms(System.nanoTime() - start) + " ms");
    }

    // Projects placed in the benchmark's application, alice granted the editor on every other one of the first hundred
    private void seedProjects(int from, int to) throws Exception {
        long start = System.nanoTime();
        List<RelationshipTuple> tuples = new ArrayList<>();
        String app = AuthzGraphUtil.authzApplication(TEST_ORG_ID, application.getId());
        for (int i = from; i < to; i++) {
            String project = AuthzNameUtil.object(AuthzConstants.PROJECT_TYPE, AuthzGraphUtil.authzId(AuthzConstants.PROJECT_TYPE, TEST_ORG_ID, "bp" + i));
            tuples.add(AuthzGraphUtil.containment(app, project));
            if (i < 100 && i % 2 == 0) {
                tuples.addAll(binding("project.editor", AuthzGraphUtil.authzUser(alice), project));
            }
        }
        write(AuthzStoreService.SYSTEM, tuples);
        line("");
        line("seeded projects " + from + ".." + to + ": " + tuples.size() + " tuples in " + ms(System.nanoTime() - start) + " ms");
    }

    // Tenants of the sample application in its own store, each with ten users and its first user granted the
    // definition's viewer within the tenant
    private void seedTenants(int from, int to) throws Exception {
        long start = System.nanoTime();
        String store = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);
        String app = AuthzGraphUtil.authzApplication(TEST_ORG_ID, TEST_APP_ID);
        List<RelationshipTuple> tuples = new ArrayList<>();
        for (int i = from; i < to; i++) {
            String tenant = AuthzNameUtil.object(AuthzConstants.TENANT_TYPE, "bt" + i);
            tuples.add(AuthzGraphUtil.tenantContainment(TEST_ORG_ID, TEST_APP_ID, "bt" + i));
            for (int u = 0; u < 10; u++) {
                String user = AuthzGraphUtil.authzUser("bt" + i + "u" + u);
                tuples.add(new RelationshipTuple(user, AuthzConstants.END_USER_RELATION, app));
                tuples.add(new RelationshipTuple(user, AuthzConstants.MEMBER_RELATION, tenant));
            }
            String here = AuthzNameUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzNameUtil.tenantDefinitionId(person.getId(), "bt" + i));
            tuples.addAll(binding(AuthzNameUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER),
                                  AuthzGraphUtil.authzUser("bt" + i + "u0"), here));
        }
        write(store, tuples);
        line("");
        line("seeded tenants " + from + ".." + to + ": " + tuples.size() + " tuples in " + ms(System.nanoTime() - start) + " ms");
    }

    // Writes the tuples in slices of a thousand, eight slices under way at a time, each tuple in one slice: the
    // grants of one role on one object share the binding's role and attachment
    private void write(String store, List<RelationshipTuple> gathered) throws Exception {
        List<RelationshipTuple> tuples = new ArrayList<>(new LinkedHashSet<>(gathered));
        List<Future<Void>> slices = new ArrayList<>();
        for (int from = 0; from < tuples.size(); from += 1000) {
            List<RelationshipTuple> slice = tuples.subList(from, Math.min(tuples.size(), from + 1000));
            slices.add(relationships.ensure(store, slice));
            if (slices.size() == 8) {
                awaitLong(Future.all(slices));
                slices.clear();
            }
        }
        awaitLong(Future.all(slices));
    }

    // A grant as RelationshipService.bind writes it: the member joins the role's one binding on the object
    private static List<RelationshipTuple> binding(String roleId, String member, String object) {
        String binding = AuthzNameUtil.bindingObject(object, roleId);
        return List.of(new RelationshipTuple(AuthzNameUtil.roleObject(roleId), AuthzConstants.ROLE_RELATION, binding),
                       new RelationshipTuple(member, AuthzConstants.MEMBER_RELATION, binding),
                       new RelationshipTuple(binding, AuthzConstants.ROLE_BINDING_RELATION, object),
                       new RelationshipTuple(binding, "binding", "role_holders:" + roleId));
    }

    // ---------------------------------------------------------------- measuring

    private record Stats(String name, int concurrency, int n, double p50, double p95, double p99, double max, double mean,
                         double perSecond, long failures, double[] engine) {

        Stats withEngine(double[] engine) {
            return new Stats(name, concurrency, n, p50, p95, p99, max, mean, perSecond, failures, engine);
        }

        static Stats of(String name, int concurrency, List<Long> nanos, long elapsedNanos, long failures) {
            long[] sorted = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
            double mean = Arrays.stream(sorted).average().orElse(0) / 1e6;
            double perSecond = elapsedNanos > 0 ? sorted.length / (elapsedNanos / 1e9) : 0;
            return new Stats(name, concurrency, sorted.length, pct(sorted, 50), pct(sorted, 95), pct(sorted, 99),
                             sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6, mean, perSecond, failures, null);
        }

        private static double pct(long[] sorted, double p) {
            return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100 * sorted.length) - 1)] / 1e6;
        }
    }

    private Stats measure(String name, int concurrency, Supplier<? extends Future<?>> operation) throws Exception {
        return measure(name, concurrency, ITERATIONS, operation);
    }

    // Runs the operation the warmup's count of times, then the iterations' with at most the concurrency's under way,
    // each started on one of as many event loop contexts as the concurrency, as the gateway's verticles call it
    private Stats measure(String name, int concurrency, int iterations, Supplier<? extends Future<?>> operation) throws Exception {
        Context[] contexts = contexts(concurrency);
        run(contexts, concurrency, Math.min(WARMUP, iterations), operation, new long[Math.min(WARMUP, iterations)], new AtomicInteger());
        long[] latencies = new long[iterations];
        AtomicInteger failures = new AtomicInteger();
        double[] before = engineCounters();
        long start = System.nanoTime();
        int issued = run(contexts, concurrency, iterations, operation, latencies, failures);
        long elapsed = System.nanoTime() - start;
        double[] after = engineCounters();
        List<Long> nanos = new ArrayList<>(issued);
        for (int i = 0; i < issued; i++) {
            nanos.add(latencies[i]);
        }
        Stats ret = Stats.of(name, concurrency, nanos, elapsed, failures.get());
        if (before != null && after != null) {
            double calls = after[0] - before[0];
            ret = ret.withEngine(calls == 0
                    ? new double[]{0, 0, 0, 0}
                    : new double[]{calls / issued, (after[1] - before[1]) / calls, (after[2] - before[2]) / calls, (after[3] - before[3]) / calls});
        }
        System.out.println("[authz-benchmark] " + format(ret));
        return ret;
    }

    private int run(Context[] contexts, int concurrency, int iterations, Supplier<? extends Future<?>> operation, long[] latencies,
                    AtomicInteger failures) throws Exception {
        Semaphore inFlight = new Semaphore(concurrency);
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int issued = 0;
        for (int i = 0; i < iterations && (i < MINIMUM_ITERATIONS || System.nanoTime() < deadline); i++) {
            inFlight.acquire();
            int index = i;
            long start = System.nanoTime();
            onContext(contexts[i % contexts.length], operation).onComplete(result -> {
                latencies[index] = System.nanoTime() - start;
                if (result.failed()) {
                    if (failures.getAndIncrement() == 0) {
                        System.out.println("[authz-benchmark] failure: " + result.cause());
                    }
                }
                inFlight.release();
            });
            issued++;
        }
        require(inFlight.tryAcquire(concurrency, 10, TimeUnit.MINUTES), "the measurement never finished");
        return issued;
    }

    private Context[] contexts(int count) {
        Context[] ret = new Context[count];
        for (int i = 0; i < count; i++) {
            ret[i] = vertx.getOrCreateContext();
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private static Future<Object> onContext(Context context, Supplier<? extends Future<?>> operation) {
        Promise<Object> promise = Promise.promise();
        context.runOnContext(v -> {
            try {
                ((Future<Object>) operation.get()).onComplete(promise);
            } catch (Throwable t) {
                promise.fail(t);
            }
        });
        return promise.future();
    }

    private Future<Void> request(String service, String function, Participant caller, byte[] body) {
        return requestAuthorizer.authorize(criOf(service, function, caller), caller, EventConstants.CONTENT_TYPE_JSON, body);
    }

    private Future<Void> toolCall(String service, String function, Participant caller, Map<String, ?> arguments) {
        return requestAuthorizer.authorize(criOf(service, function, caller), caller, jsonMapper.valueToTree(arguments));
    }

    private final Map<String, CRI> cris = new java.util.concurrent.ConcurrentHashMap<>();

    private CRI criOf(String service, String function, Participant caller) {
        // the directory is read on the test thread before a measurement, never on the event loop that answers it
        return cris.computeIfAbsent(service + "/" + function + "/" + caller.getClass().getSimpleName(), key -> {
            try {
                return cri(service, function, caller);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // A refusal is the expected outcome; anything else fails the measured call
    private static Future<Void> refusal(Future<Void> authorization) {
        return authorization.transform(result -> result.succeeded()
                ? Future.failedFuture(new IllegalStateException("admitted"))
                : result.cause() instanceof AuthorizationException ? Future.succeededFuture() : Future.failedFuture(result.cause()));
    }

    private Future<Boolean> rawCheck(Participant participant, String relation, String object, ConsistencyPreference consistency) {
        CheckRequest request = new CheckRequest().authorizationModelId(systemModelId)
                                                 .consistency(consistency)
                                                 .tupleKey(new CheckRequestTupleKey().user(AuthzGraphUtil.authzUser(participant))
                                                                                     .relation(relation)
                                                                                     ._object(object));
        return fga.check(systemStoreId, request).map(response -> response.getAllowed());
    }

    // ---------------------------------------------------------------- engine metrics

    // OpenFGA's Prometheus metrics, read from inside the compose network, kept for the report's breakdown of where a
    // check spends its time
    private void metrics(String label) {
        try {
            Process process = new ProcessBuilder("docker", "run", "--rm", "--network", "kinotic-test_default", "curlimages/curl:8.17.0",
                                                 "-s", "http://openfga:2112/metrics").redirectErrorStream(true).start();
            byte[] metrics = process.getInputStream().readAllBytes();
            process.waitFor(60, TimeUnit.SECONDS);
            Files.write(out.resolve("metrics-" + label.replaceAll("[^A-Za-z0-9]+", "-") + ".txt"), metrics);
        } catch (IOException | InterruptedException e) {
            line("(metrics not read: " + e.getMessage() + ")");
        }
    }

    private String metricsUrl;
    private final java.net.http.HttpClient metricsClient = java.net.http.HttpClient.newBuilder()
                                                                              .proxy(java.net.ProxySelector.of(null))
                                                                              .build();

    // The engine's totals over its Check and StreamedListObjects calls: calls, milliseconds spent serving them,
    // datastore queries and dispatches; null when the metrics cannot be read
    private double[] engineCounters() {
        double[] ret = null;
        try {
            if (metricsUrl == null) {
                Process process = new ProcessBuilder("docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", "openfga")
                        .redirectErrorStream(true).start();
                String ip = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                process.waitFor(30, TimeUnit.SECONDS);
                metricsUrl = "http://" + ip + ":2112/metrics";
            }
            String body = metricsClient.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(metricsUrl)).build(),
                                             java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            ret = new double[4];
            for (String metric : body.split("\n")) {
                boolean served = metric.contains("grpc_method=\"check\"") || metric.contains("grpc_method=\"streamedlistobjects\"");
                if (served) {
                    double value = Double.parseDouble(metric.substring(metric.lastIndexOf(' ') + 1));
                    if (metric.startsWith("openfga_datastore_query_count_count{")) {
                        ret[0] += value;
                    } else if (metric.startsWith("openfga_request_duration_ms_sum{")) {
                        ret[1] += value;
                    } else if (metric.startsWith("openfga_datastore_query_count_sum{")) {
                        ret[2] += value;
                    } else if (metric.startsWith("openfga_dispatch_count_sum{")) {
                        ret[3] += value;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[authz-benchmark] engine metrics not read: " + e);
        }
        return ret;
    }

    // ---------------------------------------------------------------- report

    private void section(String name) throws IOException {
        line("");
        line("## " + name);
        line("");
    }

    private void header() throws IOException {
        line("| scenario | c | n | p50 ms | p95 ms | p99 ms | max ms | mean ms | req/s | failures | engine calls/req | engine ms/call | db queries/call | dispatches/call |");
        line("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
    }

    private void row(Stats stats) throws IOException {
        line(format(stats));
    }

    private static String format(Stats s) {
        String engine = s.engine() == null
                ? " | | | |"
                : String.format(Locale.ROOT, " %.2f | %.2f | %.0f | %.0f |", s.engine()[0], s.engine()[1], s.engine()[2], s.engine()[3]);
        return String.format(Locale.ROOT, "| %s | %d | %d | %.2f | %.2f | %.2f | %.2f | %.2f | %.0f | %d |",
                             s.name(), s.concurrency(), s.n(), s.p50(), s.p95(), s.p99(), s.max(), s.mean(), s.perSecond(), s.failures()) + engine;
    }

    private void line(String text) {
        report.append(text).append('\n');
        try {
            Files.writeString(out.resolve("report.md"), report.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }

    // ---------------------------------------------------------------- helpers

    private String hotObject() {
        return AuthzNameUtil.object(AuthzConstants.PROJECT_TYPE, AuthzGraphUtil.authzId(AuthzConstants.PROJECT_TYPE, TEST_ORG_ID, hot.getId()));
    }

    private static RelationshipTuple tuple(Participant participant, String relation, String object) {
        return new RelationshipTuple(AuthzGraphUtil.authzUser(participant), relation, object);
    }

    private byte[] body(Object arguments) {
        return jsonMapper.writeValueAsString(arguments).getBytes(StandardCharsets.UTF_8);
    }

    private static Participant participant(String id, Map<String, String> metadata) {
        Map<String, String> all = new HashMap<>(metadata);
        all.put(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER);
        return new DefaultOrganizationParticipant(id, TEST_ORG_ID, all, List.of());
    }

    private Project project(Application application, String name) throws Exception {
        Project project = new Project();
        project.setName(name + " Project " + suffix());
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(application.getId());
        return await(runAsOrganization(() -> projectService.createProjectIfNotExist(project)));
    }

    private EntityDefinition personDefinition() throws Exception {
        EntityDefinition definition = new EntityDefinition();
        definition.setName("BenchmarkPerson" + suffix());
        definition.setOrganizationId(TestDataService.SAMPLE_ORG_ID);
        definition.setApplicationId(TestDataService.SAMPLE_APP_ID);
        definition.setProjectId(TestDataService.SAMPLE_PROJECT_ID);
        definition.setDescription("A person the authorization benchmark reads");
        definition.setSchema(testDataService.createPersonSchema(MultiTenancyType.SHARED));
        EntityDefinition saved = await(runAsOrganization(() -> testDataService.createSampleProjectIfNotExist()
                                                                              .compose(v -> entityDefinitionService.create(definition))
                                                                              .compose(created -> entityDefinitionService.publish(created.getId()).map(created))));
        FunctionDefinition find = new FunctionDefinition().setName(FIND_QUERY).addParameter("lastName", new StringC3Type());
        find.setDecorators(List.of(new QueryDecorator().setStatements("SELECT * FROM %s WHERE lastName = :lastName".formatted(saved.getName()))));
        NamedQueriesDefinition namedQueries = new NamedQueriesDefinition();
        namedQueries.setId(saved.getId());
        namedQueries.setOrganizationId(saved.getOrganizationId());
        namedQueries.setApplicationId(saved.getApplicationId());
        namedQueries.setProjectId(saved.getProjectId());
        namedQueries.setEntityDefinitionName(saved.getName());
        namedQueries.setNamedQueries(List.of(find));
        await(runAsOrganization(() -> namedQueriesDefinitionService.saveSync(namedQueries)));
        return saved;
    }

    private ApplicationParticipantIdentity endUser(String tenantId) throws Exception {
        ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
        user.setEmail("bench-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("Benchmark User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(TEST_APP_ID);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "End-User-1"));
    }

    // a system service declared on the tenant, whose permission an application's model carries as a relation
    private static ServiceDefinition tenantService() {
        ServiceDefinition ret = new ServiceDefinition().setNamespace("org.kinotic.benchmark").setName("TenantService");
        ret.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType("tenant").setParent("application")));
        FunctionDefinition find = new FunctionDefinition().setName("findById");
        find.setDecorators(List.of(new AuthzCheckC3Decorator().setResource("tenant").setResourceId("{id}")
                                                              .setPermissionResource("tenant").setPermission("can_read")));
        ret.addFunction(find);
        return ret;
    }

    private static <T> T awaitLong(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.MINUTES);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static List<Integer> intsEnv(String name, String fallback) {
        String value = System.getenv().getOrDefault(name, fallback);
        return value.isBlank() ? List.of() : Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }
}
