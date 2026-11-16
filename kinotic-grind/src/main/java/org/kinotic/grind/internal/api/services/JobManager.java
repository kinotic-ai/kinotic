package org.kinotic.grind.internal.api.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.grind.api.services.SystemJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cluster's job manager, one HA cluster singleton on the Ignite service grid. It has a run of
 * each {@link SystemJob} whose definition declares a {@link JobDefinition#getPeriod() period} started
 * whenever a period has passed since the job's latest run started, whoever started that run, so a run
 * an operator starts moves the job's schedule. A job with no run on record is due at once. Each run
 * is started on one of the nodes that hold the system jobs, chosen in turn; a node that does not
 * start it is followed by the next. A job that comes due while a run of it is still executing is
 * refused its run, as any run of an exclusive job is, and is due again a period later.
 */
@Slf4j
@RequiredArgsConstructor
public class JobManager implements Service {

    static final String SINGLETON_NAME = "grind-job-manager";

    // How long after a job's latest run could not be read, or no node started its run, the job is due again
    private static final long RETRY_MS = 60_000;

    // How long a node is given to answer that it started a run
    private static final long START_TIMEOUT_MS = 30_000;

    // Matches the nodes that hold the system jobs: the ones the singleton is deployed among
    private final IgnitePredicate<ClusterNode> hosts;

    // Injected by Ignite on the node elected to host the singleton
    @IgniteInstanceResource
    private transient Ignite ignite;
    @SpringResource(resourceClass = JobService.class)
    private transient JobService jobService;
    @SpringResource(resourceClass = JobRunRepository.class)
    private transient JobRunRepository jobRuns;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    // The period of each scheduled job in milliseconds, by the job's name; written only in init
    private transient Map<String, Long> periods;
    // When each scheduled job is next due, in epoch milliseconds, worked out from the recorded runs by
    // each node that comes to host the singleton. Guarded by this with the fields below, since the
    // timer, the reads of the recorded runs and the nodes' answers complete on other threads
    private transient Map<String, Long> dueTimes;
    // The jobs whose decision is in flight
    private transient Set<String> deciding;
    // The one timer, set to the earliest due time
    private transient Long timerId;
    private transient int turn;
    // Read without the lock where a node is about to be asked for a run
    private transient volatile boolean cancelled;

    @Override
    public void init() {
        // this instance was serialized to the hosting node, so runtime state is created here rather
        // than in field initializers, which do not run on deserialization
        periods = new HashMap<>();
        dueTimes = new HashMap<>();
        deciding = new HashSet<>();
        for (JobDefinition job : jobService.findSystemJobs()) {
            if (job.getPeriod() != null) {
                periods.put(job.getName(), job.getPeriod().toMillis());
                // due now, so the first wake reads each job's latest run and works out when it is really due
                dueTimes.put(job.getName(), 0L);
            }
        }
        log.info("Starting job manager singleton, scheduling {}", periods.keySet());
        wake();
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the timer set in init and at every wake
    }

    @Override
    public synchronized void cancel() {
        log.info("Stopping job manager singleton");
        cancelled = true;
        setTimer();
    }

    // Decides every job that is due. With one timer for every schedule, a wake that returned without
    // setting it would stop them all, so the timer is set whatever a job's decision throws
    private synchronized void wake() {
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> due : dueTimes.entrySet()) {
                if (!cancelled && due.getValue() <= now) {
                    // the job's turn to be tried again, which stands when its decision fails
                    due.setValue(now + RETRY_MS);
                    // asking every node in turn can outlast the retry, and a second decision made
                    // meanwhile could start a second run
                    if (deciding.add(due.getKey())) {
                        decide(due.getKey());
                    }
                }
            }
        } finally {
            setTimer();
        }
    }

    // Reads the job's latest run, since an operator may have started one, and has a run started when a
    // period has passed since that one started
    private void decide(String name) {
        Future.succeededFuture()
              .compose(v -> jobRuns.findLatestRun(name, null))
              .compose(latest -> {
                  long period = periods.get(name);
                  long now = System.currentTimeMillis();
                  Future<Long> nextDue;
                  if (latest != null && latest.getStarted().getTime() + period > now) {
                      nextDue = Future.succeededFuture(latest.getStarted().getTime() + period);
                  } else {
                      nextDue = start(name, latest, nodesInTurn(), 0)
                              .compose(jobRunId -> confirmStarted(name, jobRunId))
                              .map(now + period);
                  }
                  return nextDue;
              })
              .onComplete(nextDue -> decided(name, nextDue));
    }

    // A refused run stands for the period's run when the job's latest run is the one executing, read
    // again since it may have started after the job was decided. Any other refusal is a lock that
    // could not be taken, or a run too new to be read, and the job is decided again
    private Future<Void> confirmStarted(String name, String jobRunId) {
        Future<Void> ret;
        if (jobRunId != null) {
            ret = Future.succeededFuture();
        } else {
            ret = jobRuns.findLatestRun(name, null).map(executing -> {
                if (executing == null || executing.getStatus() != ExecutionStatus.RUNNING) {
                    throw new IllegalStateException("System job " + name + " was refused its run, and its latest run on record, "
                                                            + (executing != null ? executing.getId() : "none") + ", is not executing");
                }
                log.warn("System job {} came due while run {} of it was still executing, so no run was started for this period",
                         name, executing.getId());
                return null;
            });
        }
        return ret;
    }

    private synchronized void decided(String name, AsyncResult<Long> nextDue) {
        deciding.remove(name);
        if (nextDue.succeeded()) {
            dueTimes.put(name, nextDue.result());
            setTimer();
        } else {
            log.warn("System job {} is due and no run of it was started; it is tried again within {}s",
                     name, RETRY_MS / 1000, nextDue.cause());
        }
    }

    // The nodes that hold the system jobs, starting with the one whose turn it is
    private List<ClusterNode> nodesInTurn() {
        List<ClusterNode> ret = new ArrayList<>(ignite.cluster().forPredicate(hosts).nodes());
        ret.sort(Comparator.comparing(ClusterNode::id));
        if (!ret.isEmpty()) {
            synchronized (this) {
                Collections.rotate(ret, -Math.floorMod(turn++, ret.size()));
            }
        }
        return ret;
    }

    // Asks the nodes one after another to start a run of the job, until one answers, and completes
    // with the id of the run started, or null when the run was refused because one is executing. A
    // node that failed to answer may still have started the run, so the next node is asked only when
    // the recorded runs show none newer than the latest known before the first was asked
    private Future<String> start(String name, JobRun latest, List<ClusterNode> nodes, int index) {
        Future<String> ret;
        if (cancelled) {
            // the node that hosts the manager from here on decides the job
            ret = Future.failedFuture(new IllegalStateException("The job manager stopped before a run of system job " + name + " was started"));
        } else if (index == nodes.size()) {
            ret = Future.failedFuture(new IllegalStateException("None of the " + nodes.size() + " nodes holding system job "
                                                                        + name + " started a run of it"));
        } else {
            ClusterNode node = nodes.get(index);
            ret = startOn(node, name)
                    .onSuccess(jobRunId -> {
                        if (jobRunId != null) {
                            log.info("Node {} started scheduled system job {} as run {}", node.id(), name, jobRunId);
                        }
                    })
                    .recover(error -> {
                        log.warn("Node {} did not answer that it started system job {}", node.id(), name, error);
                        return jobRuns.findLatestRun(name, null)
                                      .compose(current -> isNewer(current, latest)
                                              ? Future.succeededFuture(current.getId())
                                              : start(name, latest, nodes, index + 1));
                    });
        }
        return ret;
    }

    private static boolean isNewer(JobRun current, JobRun latest) {
        return current != null && (latest == null || current.getStarted().after(latest.getStarted()));
    }

    // Completes with the id of the run the node started, or null when the node was refused the run
    // because one is executing. Future.future fails the future when sending the start throws, as it
    // does for a node that has left
    private Future<String> startOn(ClusterNode node, String name) {
        Context context = vertx.getOrCreateContext();
        return Future.future(promise -> ignite.compute(ignite.cluster().forNodeId(node.id()))
                                              .withNoFailover()
                                              .withTimeout(START_TIMEOUT_MS)
                                              .callAsync(new SystemJobStart(name))
                                              // the Ignite future completes on an Ignite thread
                                              .listen(done -> context.runOnContext(v -> {
                                                  try {
                                                      promise.complete(done.get());
                                                  } catch (RuntimeException e) {
                                                      promise.fail(e);
                                                  }
                                              })));
    }

    // Replaces the timer with one for the earliest due time; called holding the lock
    private void setTimer() {
        if (timerId != null) {
            vertx.cancelTimer(timerId);
            timerId = null;
        }
        if (!cancelled && !dueTimes.isEmpty()) {
            long earliest = Collections.min(dueTimes.values());
            // a job already overdue is given the shortest delay Vert.x accepts
            timerId = vertx.setTimer(Math.max(1, earliest - System.currentTimeMillis()), id -> wake());
        }
    }

}
