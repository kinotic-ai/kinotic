package org.kinotic.test.tests.system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Direction;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.JobRunFilter;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.management.api.services.JobMonitoringService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The job runs JobMonitoringService finds for a filter, narrowed on the server: by organization,
 * status and start time, and for an organization participant to its own organization. Each test
 * seeds runs that start at a random day in the far future and asks only for runs started since
 * then, so the shared index's other runs never match.
 */
@SpringBootTest
public class JobMonitoringServiceTests extends KinoticTestBase {

    private static final SystemParticipant SYSTEM_PARTICIPANT =
            new DefaultSystemParticipant("test-system-user", Map.of(), List.of("ADMIN"));

    @Autowired
    private JobMonitoringService jobMonitoring;

    @Autowired
    private JobRunRepository jobRuns;

    private final List<String> seeded = new ArrayList<>();

    private final Instant since = Instant.parse("2100-01-01T00:00:00Z")
                                         .plus(Duration.ofDays(ThreadLocalRandom.current().nextInt(100_000)));

    @AfterEach
    public void removeRuns() throws Exception {
        for (String id : seeded) {
            await(jobRuns.deleteByIdSync(id));
        }
        seeded.clear();
    }

    @Test
    public void aSystemParticipantFindsEveryRunOrOneOrganizations() throws Exception {
        String system = seed(null, ExecutionStatus.COMPLETED, 1);
        String organization = seed(TEST_ORG_ID, ExecutionStatus.COMPLETED, 2);

        assertEquals(List.of(organization, system), idsFound(SYSTEM_PARTICIPANT, filter()));
        assertEquals(List.of(organization), idsFound(SYSTEM_PARTICIPANT, filter().setOrganizationId(TEST_ORG_ID)));
    }

    @Test
    public void theStatusAndStartTimeNarrowTheRuns() throws Exception {
        String failed = seed(null, ExecutionStatus.FAILED, 2);
        seed(null, ExecutionStatus.COMPLETED, 3);
        String early = seed(null, ExecutionStatus.FAILED, -1);

        assertEquals(List.of(failed), idsFound(SYSTEM_PARTICIPANT, filter().setStatus(ExecutionStatus.FAILED)));
        assertEquals(List.of(failed, early),
                     idsFound(SYSTEM_PARTICIPANT, new JobRunFilter().setStatus(ExecutionStatus.FAILED)
                                                                    .setStartedSince(Date.from(since.minus(Duration.ofHours(2))))));
    }

    @Test
    public void anOrganizationParticipantFindsOnlyItsOwnRuns() throws Exception {
        seed(null, ExecutionStatus.COMPLETED, 1);
        String own = seed(TEST_ORG_ID, ExecutionStatus.COMPLETED, 2);
        seed("another-organization", ExecutionStatus.COMPLETED, 3);

        assertEquals(List.of(own), idsFound(TEST_ORGANIZATION_PARTICIPANT, filter()));
    }

    @Test
    public void anOrganizationParticipantMayNotAskForAnotherOrganizationsRuns() {
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> idsFound(TEST_ORGANIZATION_PARTICIPANT, filter().setOrganizationId("another-organization")));

        assertInstanceOf(AuthorizationException.class, error.getCause());
    }

    @Test
    public void aPageCarriesTheTotalTheFilterMatches() throws Exception {
        seed(TEST_ORG_ID, ExecutionStatus.COMPLETED, 1);
        seed(TEST_ORG_ID, ExecutionStatus.COMPLETED, 2);
        seed(null, ExecutionStatus.COMPLETED, 3);

        Long total = await(runAs(SYSTEM_PARTICIPANT, () -> jobMonitoring.findJobRuns(filter().setOrganizationId(TEST_ORG_ID),
                                                                                      Pageable.create(0, 1, null))))
                .getTotalElements();

        assertEquals(2L, total);
    }

    // the runs this test seeded: those started since its far-future start time
    private JobRunFilter filter() {
        return new JobRunFilter().setStartedSince(Date.from(since));
    }

    // seeds a run started the given number of hours after this test's start time, owned by the organization or, for null, the system
    private String seed(String organizationId, ExecutionStatus status, int hoursAfter) throws Exception {
        JobRun run = new JobRun().setId("job-monitoring-test-" + suffix())
                                 .setName("job-monitoring-test")
                                 .setOrganizationId(organizationId)
                                 .setStatus(status)
                                 .setStarted(Date.from(since.plus(Duration.ofHours(hoursAfter))));
        await(jobRuns.saveSync(run));
        seeded.add(run.getId());
        return run.getId();
    }

    // the ids of the runs found, most recently started first
    private List<String> idsFound(ScopedParticipant participant, JobRunFilter filter) throws Exception {
        Pageable newestFirst = Pageable.create(0, 100, Sort.by(Direction.DESC, "started"));
        return await(runAs(participant, () -> jobMonitoring.findJobRuns(filter, newestFirst))).getContent().stream()
                                                                                                .map(JobRun::getId)
                                                                                                .toList();
    }

}
