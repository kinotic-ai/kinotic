

package org.kinotic.orchestrator.api;

/**
 * Represents the progress of a {@link JobDefinition} or {@link Task}
 *
 *
 * Created by Navid Mitchell on 11/11/20
 */
public class Progress {

    private int percentageComplete;
    private String message;

    public Progress() {
    }

    public Progress(int percentageComplete, String message) {
        this.percentageComplete = percentageComplete;
        this.message = message;
    }

    public int getPercentageComplete() {
        return percentageComplete;
    }

    public Progress setPercentageComplete(int progress) {
        this.percentageComplete = progress;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public Progress setMessage(String message) {
        this.message = message;
        return this;
    }

}
