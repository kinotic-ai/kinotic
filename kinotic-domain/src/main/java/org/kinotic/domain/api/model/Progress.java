package org.kinotic.domain.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents progress during a long-running operation.
 * This is used with Flux/Observable to provide real-time progress feedback.
 * Created By Navíd Mitchell 🤪on 7/12/26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Progress<T> {

    /**
     * The type of progress update.
     */
    private ProgressType type;

    /**
     * Summary of the current step.
     */
    private String summary;

    /**
     * Optional details about the current step.
     */
    private String details;

    /**
     * When this progress update was created.
     */
    private Instant timestamp;

    /**
     * The result of the operation if this is a COMPLETED update.
     */
    private T result;

}
