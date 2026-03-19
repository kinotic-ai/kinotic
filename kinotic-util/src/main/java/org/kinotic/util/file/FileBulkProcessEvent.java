

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
