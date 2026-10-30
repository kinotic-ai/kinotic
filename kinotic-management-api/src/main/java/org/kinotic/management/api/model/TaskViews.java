package org.kinotic.management.api.model;

import org.kinotic.grind.api.model.TaskViewKind;
import org.kinotic.management.api.model.deployment.ProjectArtifacts;
import org.kinotic.management.api.model.deployment.SbomGeneration;
import org.kinotic.management.api.model.vulnerability.AdvisoryImport;
import org.kinotic.management.api.model.vulnerability.SbomScan;
import org.kinotic.management.api.model.workload.WorkloadLog;

/**
 * The kinds of task views the platform's jobs show, each rendered by the console component
 * registered under its name.
 */
public final class TaskViews {

    /** A workload's console log, followed while the task runs. */
    public static final TaskViewKind<WorkloadLog> WORKLOAD_LOG = new TaskViewKind<>("workloadLog");

    /** The artifacts a deployed commit contains. */
    public static final TaskViewKind<ProjectArtifacts> PROJECT_ARTIFACTS = new TaskViewKind<>("projectArtifacts");

    /** The generation of a project's SBOM: its workload's log, and whether the run generated it. */
    public static final TaskViewKind<SbomGeneration> SBOM_GENERATION = new TaskViewKind<>("sbomGeneration");

    /** What one import of an ecosystem's vulnerability advisories from OSV stored. */
    public static final TaskViewKind<AdvisoryImport> ADVISORY_IMPORT = new TaskViewKind<>("advisoryImport");

    /** What one scan of every project's SBOM for vulnerabilities did. */
    public static final TaskViewKind<SbomScan> SBOM_SCAN = new TaskViewKind<>("sbomScan");

    private TaskViews() {
    }

}
