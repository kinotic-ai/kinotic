package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The optional features this Kinotic server offers, so a UI shows only what the server can do.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Capabilities {

    /**
     * True when the server sends feedback to the Kinotic team through {@code FeedbackService}.
     */
    private boolean feedbackEnabled;

}
