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

import org.kinotic.util.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * Created by navid on 9/16/19
 */
public class FileProcessor {
    private static final Logger log = LoggerFactory.getLogger(FileProcessor.class);

    private final FileProcessorOptions fileProcessorOptions;
    protected final AtomicBoolean stopped = new AtomicBoolean(true);
    private final BlockingQueue<FileProcessEvent> workQueue;
    private final ArrayDeque<Worker> workers;

    public FileProcessor(FileProcessorOptions fileProcessorOptions) {
        this.fileProcessorOptions = fileProcessorOptions;
        workQueue = new LinkedBlockingQueue<>(fileProcessorOptions.getMaxQueueSize());
        workers = new ArrayDeque<>(fileProcessorOptions.getNumberOfWorkersToStart() + 1);
    }

    public synchronized void start(){
        if(stopped.get()){
            stopped.set(false);
            // Start one master and specified number of workers
            FileProcessorMaster master = new FileProcessorMaster("fi-master",
                                                                 workQueue,
                                                                 fileProcessorOptions);
            workers.push(master);

            // now create the desired number of workers
            for(int i = 0; i < fileProcessorOptions.getNumberOfWorkersToStart(); i++){
                FileProcessorWorker worker = new FileProcessorWorker("fi-worker-"+i,
                                                                     workQueue,
                                                                     fileProcessorOptions);
                worker.start();
                workers.push(worker);
            }
            // now start the master
            master.start();
            log.info("File processor started for path "+fileProcessorOptions.getSourceDirectory());
        }
    }

    public synchronized void shutdown(){
        if(!stopped.get()){
            stopped.set(true);
            while(!workers.isEmpty()){
                Worker worker = workers.pop();
                try {
                    worker.shutdown(true);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while shutting down worker "+worker.getName());
                }
            }
            log.info("File processor shutdown for path "+fileProcessorOptions.getSourceDirectory());
        }
    }

}
