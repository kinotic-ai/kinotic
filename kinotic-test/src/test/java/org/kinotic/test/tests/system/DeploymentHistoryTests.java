package org.kinotic.test.tests.system;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An organization's deployment history as one listing: what happened to the deployment of every
 * project in the organization and to the records each made, of the kinds asked for, newest first.
 */
@SpringBootTest
public class DeploymentHistoryTests extends KinoticTestBase {

    private static final List<WatchEventKind> NOTABLE = List.of(WatchEventKind.OBSERVED_REPORTED, WatchEventKind.CONDITION_SET);

    // acme is a prefix of acme-corp, so each listing tells the two apart by the separator that ends the
    // organization id in a parent pointer
    private static final String ACME = "acme";
    private static final String ACME_CORP = "acme-corp";

    // The ledger outlives every record, so this run's entries have ids of their own
    private final String suffix = UUID.randomUUID().toString();
    private final String deployed = "history-deployed-" + suffix;
    private final String failing = "history-failing-" + suffix;
    private final String failingMicroservice = "history-failing-ms-" + suffix;
    private final String corpProject = "history-corp-" + suffix;
    private final String corpMicroservice = "history-corp-ms-" + suffix;
    private final String unparentedRun = "history-run-" + suffix;
    private final Set<String> ids = Set.of(deployed, failing, failingMicroservice, corpProject, corpMicroservice, unparentedRun);

    @Autowired
    private ProjectService projectService;

    @Autowired
    private WatchEventRepository watchEvents;

    @Autowired
    private ElasticsearchAsyncClient elasticsearch;

    @Test
    public void listsTheEntriesOfEveryProjectDeploymentInTheOrganizationOfTheKindsAskedFor() throws Exception {
        long now = System.currentTimeMillis();
        enter(deployed, WatchedType.PROJECT_DEPLOYMENT, ACME, null, WatchEventKind.DESIRED_UPDATED, now - 4000);
        enter(deployed, WatchedType.PROJECT_DEPLOYMENT, ACME, null, WatchEventKind.OBSERVED_REPORTED, now - 3000);
        enter(failingMicroservice, WatchedType.MICROSERVICE_DEPLOYMENT, ACME,
              new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, ACME, failing), WatchEventKind.CONDITION_SET, now - 2000);
        enter(unparentedRun, WatchedType.JOB_RUN, ACME, null, WatchEventKind.CONDITION_SET, now - 1500);
        enter(corpProject, WatchedType.PROJECT_DEPLOYMENT, ACME_CORP, null, WatchEventKind.OBSERVED_REPORTED, now - 1000);
        enter(corpMicroservice, WatchedType.MICROSERVICE_DEPLOYMENT, ACME_CORP,
              new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, ACME_CORP, corpProject), WatchEventKind.CONDITION_SET, now - 500);
        elasticsearch.indices().refresh(r -> r.index(WatchEventRepository.DATA_STREAM)).get(30, TimeUnit.SECONDS);

        assertEquals(List.of(failingMicroservice + " " + WatchEventKind.CONDITION_SET,
                             deployed + " " + WatchEventKind.OBSERVED_REPORTED),
                     describe(history(member(ACME))));

        assertEquals(List.of(corpMicroservice + " " + WatchEventKind.CONDITION_SET,
                             corpProject + " " + WatchEventKind.OBSERVED_REPORTED),
                     describe(history(member(ACME_CORP))));
    }

    private void enter(String id, WatchedType type, String scope, WatchedParent parent, WatchEventKind kind, long at) throws Exception {
        WatchEvent event = new WatchEvent(new Date(at), type, id, scope, parent, kind, "history test", "test-node",
                                          null, kind + " " + id, null);
        await(watchEvents.record(UUID.randomUUID().toString(), event));
    }

    // Other tests enter their own entries in the same organizations, so the listing is narrowed to this run's
    private List<WatchEvent> history(OrganizationParticipant participant) throws Exception {
        return await(runAs(participant, () -> projectService.findAllDeploymentHistory(NOTABLE, Pageable.create(0, 100, null))))
                .getContent().stream()
                .filter(event -> ids.contains(event.id()))
                .toList();
    }

    private static List<String> describe(List<WatchEvent> events) {
        return events.stream().map(event -> event.id() + " " + event.kind()).toList();
    }

    private static OrganizationParticipant member(String organizationId) {
        return new DefaultOrganizationParticipant("history-member",
                                                  organizationId,
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                         ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                  List.of("ADMIN"));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
