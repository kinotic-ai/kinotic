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

import org.kinotic.util.AbstractWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * Created by navid on 9/16/19
 */
class FileBulkProcessorMaster extends AbstractWorker {
    private static final Logger log = LoggerFactory.getLogger(FileBulkProcessorMaster.class);

    private final FileBulkProcessorOptions options;

    private final BlockingQueue<FileBulkProcessEvent> workQueue;
    private final ConcurrentHashMap<String, PathResult> activeFileProcessesMap = new ConcurrentHashMap<>();

    public FileBulkProcessorMaster(String threadName,
                                   FileBulkProcessorOptions options,
                                   BlockingQueue<FileBulkProcessEvent> workQueue) {
        super(threadName);
        this.options = options;

        this.workQueue = workQueue;
    }

    @Override
    protected void doWork() throws Exception {
        // make sure we do not load more files while the workers are still processing existing ones
        while(workQueue.remainingCapacity() <= 0){
            trySleep(5000);
        }
        if(!stopped.get()) {
            try(Stream<Path> list = Files.list(options.getSourceDirectory())) {

                List<Path> sortedPath = list.sorted(Collections.reverseOrder())
                                            .collect(Collectors.toList());

                for (Path path : sortedPath) {
                    if (stopped.get()) {
                        break;
                    }
                    if (Files.isDirectory(path)) {
                        walkDirectory(path);
                    }
                }
            }
            trySleep(options.getPollInterval().toMillis());
        }
    }

    private void walkDirectory(Path path) throws IOException{
        if(log.isTraceEnabled()){
            log.trace("Walking Directory " + path.toString());
        }
        // Walk the file tree recursively
        Files.walkFileTree(path, new SimpleFileVisitor<>(){

            private TreeSet<PathResult> results = null;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if(options.getPathComparator() != null){
                    results = new TreeSet<>(options.getPathComparator());
                }else{
                    results = new TreeSet<>();
                }

                if (stopped.get()) {
                    return FileVisitResult.TERMINATE;
                }else{
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                FileVisitResult ret = FileVisitResult.CONTINUE;
                Path toUse = path.toAbsolutePath();

                activeFileProcessesMap.computeIfAbsent(toUse.toString(), s -> {
                    PathResult ret1 = new PathResult(toUse);
                    results.add(ret1);
                    return ret1;
                });

                if(stopped.get()){
                    results.clear();
                    ret = FileVisitResult.TERMINATE;
                }

                return ret;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {

                // pass all files in a directory to a worker thread for processing
                if(results != null && results.size() > 0) {
                    try {
                        workQueue.put(new FileBulkProcessEvent(results, options, activeFileProcessesMap));
                        results = null;
                    } catch (InterruptedException e) {
                        // we were interrupted possibly by shutdown.
                        if (stopped.get()) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                }

                try {
                    // if we encounter any empty directories we delete.
                    if (options.isDeleteEmptySourceDirectories()
                            && !dir.equals(options.getSourceDirectory())
                            && dir.startsWith(options.getSourceDirectory())) {

                        Files.delete(dir);

                        if(log.isTraceEnabled()){
                            log.trace("Directory Deleted"+ dir);
                        }
                    }
                } catch (DirectoryNotEmptyException dne){
                    // this is a standard case since files may be written in a race type scenario
                } catch (Exception e) {
                    log.error("Error deleting directory",e);
                }

                if (stopped.get()) {
                    return FileVisitResult.TERMINATE;
                }else{
                    return FileVisitResult.CONTINUE;
                }
            }
        });
    }

    private void trySleep(long interval){
        if(!stopped.get()) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ex) {
                log.trace("Interrupted while sleeping");
            }
        }
    }

}
