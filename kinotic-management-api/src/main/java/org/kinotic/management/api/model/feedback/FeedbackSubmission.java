package org.kinotic.management.api.model.feedback;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A bug report or feature request about Kinotic, as its sender writes it. Every field is published
 * in a public GitHub issue.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class FeedbackSubmission {

    /**
     * Whether this reports a bug or requests a feature.
     */
    private FeedbackType type;

    /**
     * One line naming the problem or the request, at most 200 characters.
     */
    private String title;

    /**
     * What went wrong, or what is wanted and why, in Markdown.
     */
    private String description;

    /**
     * For a bug, the steps that make it happen; optional.
     */
    private String stepsToReproduce;

    /**
     * For a bug, what should have happened; optional.
     */
    private String expectedBehavior;

    /**
     * For a bug, what happened instead, such as the error message returned; optional.
     */
    private String actualBehavior;

    /**
     * Where in Kinotic the sender was, such as the portal page's route or the MCP tool that failed;
     * optional.
     */
    private String location;

}
