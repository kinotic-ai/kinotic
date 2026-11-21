package org.kinotic.management.api.model.feedback;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Whether feedback reports a problem with Kinotic or asks for something Kinotic does not do yet.
 */
@Getter
@RequiredArgsConstructor
public enum FeedbackType {

    /**
     * Something in Kinotic does not work as it should.
     */
    BUG_REPORT("bug"),

    /**
     * Kinotic lacks something the sender needs.
     */
    FEATURE_REQUEST("enhancement");

    /** The label the feedback's GitHub issue carries. */
    private final String issueLabel;

}
