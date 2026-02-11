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
import java.time.Duration;
import java.util.function.Function;

/**
 * Configuration options for use when creating a {@link FileProcessor}
 *
 * Created by navid on 9/12/19
 */
public class FileProcessorOptions {

    private final Path sourceDirectory;
    private final Function<Path, Boolean> fileConsumer;

    private final Path targetDirectory;

    private Duration pollInterval = Duration.ofMinutes(5);
    private int numberOfWorkersToStart = Math.max(Runtime.getRuntime().availableProcessors(), 1);
    private int maxQueueSize = 1000;
    private boolean deleteProcessedFiles = false;
    private boolean deleteEmptySourceDirectories = false;

    /**
     * The options for the file processor
     * @param sourceDirectory to look for file to process
     * @param targetDirectory to move files to once processed or failed
     *                        if the file is successfully processed and deleteProcessedFiles is true this directory will only be used for failed files
     * @param fileConsumer to do the actual work for each file
     *                     If the function returns true the processor will consider this file done and perform the done logic
     *                     If the function returns false the processor will consider this file in process and not do anything with the file
     *                     If the function throws an exception the processor will consider the file failed and perform the failed logic
     */
    public FileProcessorOptions(Path sourceDirectory,
                                Path targetDirectory,
                                Function<Path, Boolean> fileConsumer) {

        Validate.notNull(sourceDirectory, "The source directory cannot be null");
        Validate.isTrue(sourceDirectory.isAbsolute(), "The source directory must be an absolute path");
        Validate.isTrue(!sourceDirectory.toString().equals("/"), "The source directory must not be '/' ");

        Validate.notNull(targetDirectory, "The target directory cannot be null");
        Validate.isTrue(targetDirectory.isAbsolute(), "The target directory must be an absolute path");
        Validate.isTrue(!targetDirectory.toString().equals("/"), "The target directory must not be '/' ");

        Validate.notNull(fileConsumer, "The file consumer cannot be null");

        this.sourceDirectory = sourceDirectory;
        this.targetDirectory = targetDirectory;
        this.fileConsumer = fileConsumer;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public FileProcessorOptions withPollInterval(Duration pollInterval) {
        Validate.notNull(pollInterval, "The poll interval cannot be null");
        Validate.isTrue(pollInterval.toMillis() > 0, "The poll interval must be greater than 0");
        this.pollInterval = pollInterval;
        return this;
    }

    /**
     * If true will delete the file after it is processed instead of moving it to the target directory
     * @return true if files should be deleted after successful processing
     */
    public boolean isDeleteProcessedFiles() {
        return deleteProcessedFiles;
    }

    public FileProcessorOptions withDeleteProcessedFiles(boolean deleteProcessedFiles) {
        this.deleteProcessedFiles = deleteProcessedFiles;
        return this;
    }

    /**
     * If true will delete empty directories found in the source directory after all of the files have been processed
     * @return true if empty source directories should be deleted after processing
     */
    public boolean isDeleteEmptySourceDirectories() {
        return deleteEmptySourceDirectories;
    }

    public FileProcessorOptions withDeleteEmptySourceDirectories(boolean deleteEmptySourceDirectories) {
        this.deleteEmptySourceDirectories = deleteEmptySourceDirectories;
        return this;
    }

    public Path getSourceDirectory() {
        return sourceDirectory;
    }

    public Path getTargetDirectory() {
        return targetDirectory;
    }

    public Function<Path, Boolean> getFileConsumer() {
        return fileConsumer;
    }

    public int getNumberOfWorkersToStart() {
        return numberOfWorkersToStart;
    }

    public FileProcessorOptions withNumberOfWorkersToStart(int numberOfWorkersToStart) {
        this.numberOfWorkersToStart = numberOfWorkersToStart;
        return this;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public FileProcessorOptions withMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        return this;
    }
}
