package org.kinotic.management.api.model.feedback;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The public GitHub issue a piece of feedback was filed as, where the Kinotic team answers it.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class FeedbackIssue {

    /**
     * The issue's number in the Kinotic repository.
     */
    private int number;

    /**
     * The issue's page on GitHub.
     */
    private String url;

}
