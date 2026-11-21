package org.kinotic.management.api.model.github;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An issue opened on GitHub.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CreatedIssue {

    /**
     * The issue's number in its repository.
     */
    private int number;

    /**
     * The issue's page on GitHub.
     */
    private String url;

}
