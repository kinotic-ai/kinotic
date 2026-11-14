package org.kinotic.system.api.model.cluster;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.management.api.model.vulnerability.VulnerabilityFinding;

import java.util.ArrayList;
import java.util.List;

/**
 * The advisories affecting the libraries one build of a Kinotic server runs, with how many cluster
 * nodes run that build.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ServerVulnerabilities {

    /**
     * The server kind the build is, such as {@code management}, {@code system} or {@code app}.
     */
    private String serverName;

    /**
     * The Kinotic version of the build, or {@code null} when its nodes run from classes rather than a
     * packaged jar.
     */
    private String version;

    /**
     * How many nodes run the build.
     */
    private int nodeCount;

    /**
     * How many libraries the build runs; none when its nodes' classpath holds no library list, such
     * as when an IDE runs them from classes.
     */
    private int componentCount;

    /**
     * The advisories affecting the build's libraries, the most severe first.
     */
    private List<VulnerabilityFinding> findings = new ArrayList<>();

    /**
     * Why the build's libraries could not be checked, or {@code null} when they were; a build that
     * could not be checked has no findings.
     */
    private String error;

}
