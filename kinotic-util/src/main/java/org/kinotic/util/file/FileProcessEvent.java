

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
