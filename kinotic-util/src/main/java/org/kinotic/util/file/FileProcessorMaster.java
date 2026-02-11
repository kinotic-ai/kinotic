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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * Created by navid on 9/16/19
 */
class FileProcessorMaster extends AbstractWorker {
    private static final Logger log = LoggerFactory.getLogger(FileProcessorMaster.class);

    private final FileProcessorOptions options;
    private final BlockingQueue<FileProcessEvent> workQueue;
    private final ConcurrentHashMap<String, FileProcessEvent> activeFileProcessesMap = new ConcurrentHashMap<>();

    public FileProcessorMaster(String threadName,
                               BlockingQueue<FileProcessEvent> workQueue,
                               FileProcessorOptions options) {
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

                    } else {

                        addToWorkQueue(path);
                    }
                }
            }
            trySleep(options.getPollInterval().toMillis());
        }
    }

    private void addToWorkQueue(Path path) throws InterruptedException{
        Path toUse = path.toAbsolutePath();
        AtomicBoolean added = new AtomicBoolean(false);
        FileProcessEvent value = activeFileProcessesMap.computeIfAbsent(toUse.toString(), s -> {
            added.set(true);
            return new FileProcessEvent(toUse, activeFileProcessesMap);
        });
        if(added.get()){
            workQueue.put(value);
        }
    }

    private void walkDirectory(Path path) throws IOException{
        if(log.isTraceEnabled()){
            log.trace("Walking Directory " + path.toString());
        }
        // Walk the file tree recursively
        Files.walkFileTree(path, new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                FileVisitResult ret = FileVisitResult.CONTINUE;
                try {

                    addToWorkQueue(path);

                    if(stopped.get()){
                        ret = FileVisitResult.TERMINATE;
                    }
                } catch (InterruptedException e) {
                    // we were interrupted possibly by shutdown.
                    if(stopped.get()){
                        ret = FileVisitResult.TERMINATE;
                    }

                } catch (Exception e){
                    log.error("Exception occurred during file processing",e);
                    // all exceptions we wait and then try the next file
                    trySleep(5000);
                }
                return ret;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                try {
                    // if we encounter any empty directories we delete.
                    // This won't usually happen on the first pass but on subsequent paths we will clean up empty dirs
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
                return FileVisitResult.CONTINUE;
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
