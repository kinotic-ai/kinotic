

package org.kinotic.util.file;

import org.apache.commons.lang3.Validate;

import java.nio.file.Path;

/**
 * Contains a path and if the system should consider it processed ot not.
 * NOTE:
 * The {@link PathResult} implements {@link Comparable} but delegates comparison to {@link Path}
 * equals and hashCode also delegate to {@link Path}
 *
 * Created by navid on 9/24/19
 */
public class PathResult implements Comparable<PathResult> {

    private final Path path;
    private ProcessingStatus status = ProcessingStatus.NOT_PROCESSED;
    private Exception failedReason = null;

    public PathResult(Path path) {
        Validate.notNull(path,"The path must not be null");
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public boolean isProcessed() {
        return status == ProcessingStatus.SUCCEEDED;
    }

    /**
     * Marks this {@link PathResult} as processed
     */
    public void setProcessed(){
        status = ProcessingStatus.SUCCEEDED;
    }

    public void setFailed(Exception failedReason){
        this.failedReason = failedReason;
        status = ProcessingStatus.FAILED;
    }

    public boolean isFailed(){
        return status == ProcessingStatus.FAILED;
    }

    public Exception getFailedReason() {
        return failedReason;
    }

    @Override
    public int compareTo(PathResult other) {
        return path.compareTo(other.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return path.equals(obj);
    }

    @Override
    public String toString() {
        return "Processed: "+status + " " + path.toString();
    }


    public enum ProcessingStatus {
        NOT_PROCESSED,
        SUCCEEDED,
        FAILED
    }
}
