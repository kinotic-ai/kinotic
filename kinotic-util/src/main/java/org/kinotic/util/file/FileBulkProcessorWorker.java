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

import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 *
 * Created by navid on 9/16/19
 */
class FileBulkProcessorWorker extends AbstractWorker {
    private static final Logger log = LoggerFactory.getLogger(FileBulkProcessorWorker.class);

    private final BlockingQueue<FileBulkProcessEvent> workQueue;
    private final Consumer<FileBulkProcessEvent> consumer;

    public FileBulkProcessorWorker(String threadName,
                                   BlockingQueue<FileBulkProcessEvent> workQueue,
                                   Consumer<FileBulkProcessEvent> consumer) {
        super(threadName);
        this.workQueue = workQueue;
        this.consumer = consumer;
    }

    @Override
    protected void doWork() throws Exception{
        FileBulkProcessEvent work = workQueue.take();
        try {
            if(!stopped.get()){

                // Have consumer do the work, we assume it will not throw if it does this code will not behave very well
                // The challenge is that what would you do, fail the whole batch of files.. ?
                consumer.accept(work);

                // move all files
                for(PathResult pathResult: work.getSources()){
                    if (pathResult.isProcessed()){
                        if(work.getOptions().isDeleteProcessedFiles()){

                            Files.delete(pathResult.getPath());

                        }else {
                            FileUtil.handleSuccessWithMove(work.getOptions().getSourceDirectory(),
                                                           work.getOptions().getTargetDirectory(),
                                                           pathResult.getPath());
                        }
                    }else if(pathResult.isFailed()){
                        FileUtil.handleFailed(work.getOptions().getSourceDirectory(),
                                              work.getOptions().getTargetDirectory(),
                                              pathResult.getPath(),
                                              pathResult.getFailedReason());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Worker unhandled exception",e);
        }
        work.workerDone();
    }

}
