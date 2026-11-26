package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * The free Kinotic beta cloud plan an organization is on: what it may create while Kinotic Cloud is in
 * beta, how much of it the organization uses, and the plan explained in a few sentences for the person
 * using it.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class BetaLimits {

    /**
     * The plan in a few sentences, naming each limit, for showing to the person before they plan an
     * application.
     */
    private String summary;

    /**
     * The most applications the organization may have, or {@code null} for no cap.
     */
    private Integer maxApplicationsPerOrganization;

    /**
     * The most microservices one application may run across all of its projects, or {@code null} for
     * no cap.
     */
    private Integer maxMicroservicesPerApplication;

    /**
     * How many applications the organization has.
     */
    private long applicationCount;

    /**
     * How many microservices each of the organization's applications runs, by application id. An
     * application that runs none is absent.
     */
    private Map<String, Integer> microservicesByApplication;

}
