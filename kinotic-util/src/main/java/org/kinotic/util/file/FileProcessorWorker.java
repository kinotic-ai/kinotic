

package org.kinotic.util.file;

import org.kinotic.util.AbstractWorker;
import org.kinotic.util.UncheckedInterruptedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;

/**
 *
 * Created by navid on 9/16/19
 */
class FileProcessorWorker extends AbstractWorker {
    private static final Logger log = LoggerFactory.getLogger(FileProcessorWorker.class);

    private final BlockingQueue<FileProcessEvent> workQueue;
    private final Function<Path, Boolean> consumer;
    private final FileProcessorOptions options;

    public FileProcessorWorker(String threadName,
                               BlockingQueue<FileProcessEvent> workQueue,
                               FileProcessorOptions options) {
        super(threadName);
        this.workQueue = workQueue;
        this.consumer = options.getFileConsumer();
        this.options = options;
    }

    @Override
    protected void doWork() throws Exception{
        FileProcessEvent work = workQueue.take();
        try {
            if(!stopped.get()){
                // make sure file still exists, this should always be true but here for sanity
                if(Files.exists(work.getSourcePath())) {

                    boolean finished = false;
                    // Have consumer do the work
                    try {
                        finished = consumer.apply(work.getSourcePath());
                    } catch (Exception e) {
                        // If this is an UncheckedInterruptedException then we know the process is probably shutting down so we will need to
                        // process this file again later so any move or delete logic does not apply 
                        if(!(e instanceof UncheckedInterruptedException)) {
                            FileUtil.handleFailed(options.getSourceDirectory(),
                                                  options.getTargetDirectory(),
                                                  work.getSourcePath(),
                                                  e);
                        }
                    }

                    // outside of consumer try catch so any failures here do not end up in the failed log since they are unrelated to the consumer
                    if(finished) {
                        if (options.isDeleteProcessedFiles()) {

                            Files.delete(work.getSourcePath());

                        } else {
                            FileUtil.handleSuccessWithMove(options.getSourceDirectory(),
                                                           options.getTargetDirectory(),
                                                           work.getSourcePath());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Worker unhandled exception",e);
        }
        work.workerDone();
    }

}
