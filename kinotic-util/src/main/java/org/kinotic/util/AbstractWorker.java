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
