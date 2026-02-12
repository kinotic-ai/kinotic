

package org.kinotic.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * Created by navid on 9/16/19
 */
public abstract class AbstractWorker implements Runnable, Worker {

    private static final Logger log = LoggerFactory.getLogger(AbstractWorker.class);

    private final String threadName;

    protected final AtomicBoolean stopped = new AtomicBoolean(true);
    private Thread workThread = null;

    public AbstractWorker(String threadName) {
        this.threadName = threadName;
    }

    public synchronized void start(){
        if(stopped.get() && workThread == null){
            stopped.set(false);
            workThread = new Thread(this,threadName);
            workThread.start();
        }
    }

    public synchronized void shutdown(boolean interrupt) throws InterruptedException{
        if(!stopped.get() && workThread != null){
            stopped.set(true);
            if(interrupt){
                workThread.interrupt();
            }
            workThread.join();
        }
    }

    @Override
    public String getName() {
        return threadName;
    }

    protected abstract void doWork() throws Exception;

    @Override
    public void run() {
        while(!stopped.get()) {
            try {

                doWork();

            } catch (Exception e) {
                if(!stopped.get()) {
                    log.warn("Exception occurred in worker thread: "+threadName, e);
                }
            }
        }
        if(log.isTraceEnabled()){
            log.trace("Worker shutdown successfully");
        }
    }

}
