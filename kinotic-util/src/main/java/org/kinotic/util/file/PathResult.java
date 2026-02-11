/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
