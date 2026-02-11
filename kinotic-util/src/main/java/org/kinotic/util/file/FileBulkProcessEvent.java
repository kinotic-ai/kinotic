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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An event of work to do during bulk file processing
 *
 * Created by navid on 9/16/19
 */
public class FileBulkProcessEvent {

    private final Set<PathResult> sources;
    private final FileBulkProcessorOptions options;
    private final ConcurrentHashMap<String, PathResult> activeFileProcessesMap;

    public FileBulkProcessEvent(Set<PathResult> sources,
                                FileBulkProcessorOptions options,
                                ConcurrentHashMap<String, PathResult> activeFileProcessesMap) {
        this.sources = sources;
        this.options = options;
        this.activeFileProcessesMap = activeFileProcessesMap;
    }

    public Set<PathResult> getSources() {
        return sources;
    }

    public FileBulkProcessorOptions getOptions() {
        return options;
    }

    /**
     * Must be called by the worker when it is done processing this {@link FileProcessEvent}
     */
    public void workerDone(){
        for(PathResult pathResult: sources){
            activeFileProcessesMap.remove(pathResult.getPath().toString());
        }
    }
}
