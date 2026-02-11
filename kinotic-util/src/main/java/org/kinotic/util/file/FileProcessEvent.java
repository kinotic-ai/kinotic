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

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An event of work to do during file processing
 *
 * Created by navid on 9/16/19
 */
class FileProcessEvent {

    private final Path sourcePath;
    private final ConcurrentHashMap<String, FileProcessEvent> activeFileProcessesMap;

    public FileProcessEvent(Path sourcePath,
                            ConcurrentHashMap<String, FileProcessEvent> activeFileProcessesMap) {
        this.sourcePath = sourcePath;
        this.activeFileProcessesMap = activeFileProcessesMap;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    /**
     * Must be called by the worker when it is done processing this {@link FileProcessEvent}
     */
    public void workerDone(){
        activeFileProcessesMap.remove(sourcePath.toString());
    }
}
