package org.kinotic.domain.api.model;

/**
 * Represents the type of progress being reported.
 * Created By Navíd Mitchell 🤪on 7/12/26
 */
public enum ProgressType {
    STARTED,           // Operation has started
    RUNNING,           // Operation is in progress
    COMPLETED,         // Operation has completed
    ERROR              // Operation has failed
}
