package org.kinotic.os.api.model;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Project implements ApplicationScoped<String> {

    /**
     * The id of the project.
     * All project ids are unique throughout the entire system.
     */
    private String id;

    private String organizationId;

    /**
     * The id of the application that this project belongs to.
     * All application ids are unique throughout the entire system.
     */
    private String applicationId;

    /**
     * The name of the project.
     */
    private String name;

    /**
     * The description of the project.
     */
    private String description;

    /**
     * The source of truth for the project.
     */
    private ProjectType sourceOfTruth;

    /**
     * Full name ({@code owner/repo}) of the GitHub repository backing this project.
     * Stamped at create time by the repo provisioner; not synced back from GitHub.
     */
    private String repoFullName;

    /**
     * GitHub's stable repository id. Survives renames on the GitHub side.
     */
    private Long repoId;

    /**
     * Default branch of the backing repository at the time it was provisioned
     * (e.g. {@code main}).
     */
    private String defaultBranch;

    /**
     * Visibility chosen for the backing repository at create time. A snapshot —
     * if the repo's visibility is later changed on GitHub directly, this field is
     * not updated.
     */
    private boolean repoPrivate;

    /**
     * The date and time the project was updated.
     */
    private Date updated;

}
