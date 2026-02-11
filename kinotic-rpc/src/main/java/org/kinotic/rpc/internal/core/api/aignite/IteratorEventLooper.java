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

package org.kinotic.rpc.internal.core.api.aignite;

import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Navid Mitchell on 8/3/17.
 */
public class IteratorEventLooper<T> implements Handler<Void>, SuspendableObserver<T>, Closeable {
    private static final Logger log = LoggerFactory.getLogger(IteratorEventLooper.class);

    private final Vertx vertx;
    private final ContextInternal creatingContext;
    private final AtomicBoolean suspended = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Iterator<T> iterator;
    private Handler<T> dataHandler;
    private Handler<Void> completionHandler;
    private Handler<Throwable> exceptionHandler;

    /**
     * Constructs a new IteratorEventLooper
     *
     * @param vertx instance to use
     * @param iterator to loop over using the vertx event loop
     *
     * @throws IllegalArgumentException if any parameter is null
     * @throws IllegalStateException if the current vertx context belongs to a multi threaded worker
     */
    public IteratorEventLooper(Vertx vertx,
                               Iterator<T> iterator) {

        Validate.notNull(vertx, "Vertx is null");
        Validate.notNull(iterator, "Iterator is null");

        this.vertx = vertx;
        this.iterator = iterator;
        this.creatingContext = (ContextInternal) vertx.getOrCreateContext();

        creatingContext.addCloseHook(this);

    }

    @Override
    public Observer<T> handler(Handler<T> handler) {
        this.dataHandler = handler;
        return this;
    }

    public Observer<T> completionHandler(Handler<Void> completionHandler) {
        this.completionHandler = completionHandler;
        return this;
    }

    public Observer<T> exceptionHandler(Handler<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    @Override
    public SuspendableObserver<T> suspend() {
        suspended.compareAndSet(false,true);
        return this;
    }

    @Override
    public SuspendableObserver<T> resume() {
        if(suspended.compareAndSet(true,false)){
            doLoop();
        }
        return this;
    }

    @Override
    public void handle(Void v) {
        // If there are more entries submit to blocking executor for processing
        if(!closed.get() && !suspended.get()) {

            if (iterator.hasNext()) {

                creatingContext
                        .executeBlocking(() -> iterator.next())
                        .onComplete(future -> {

                            // Did iterator.next() complete properly
                            if (future.succeeded()) {

                                T value = future.result();
                                // Call handler on its context
                                creatingContext.runOnContext(ce -> {
                                    try {

                                        dataHandler.handle(value);

                                        // No exceptions then process the next entry
                                        doLoop();

                                    } catch (Exception e) {
                                        log.warn("IteratorEventLooper's data handler threw an error {}", e.getMessage());
                                        log.warn("Terminating IteratorEventLooper!");

                                        close();
                                        if (exceptionHandler != null) {
                                            creatingContext.runOnContext(vv -> exceptionHandler.handle(e));
                                        }
                                    }
                                });
                            } else {
                                close();
                                if (exceptionHandler != null) {
                                    creatingContext.runOnContext(vv -> exceptionHandler.handle(future.cause()));
                                }
                            }
                        });
            } else {
                close();
                if (completionHandler != null) {
                    creatingContext.runOnContext(vv -> completionHandler.handle(null));
                }
            }

        }
    }

    private void doLoop() {
        creatingContext.runOnContext(this);
    }

    public void start() {
        if(dataHandler == null){
            throw new IllegalStateException("You must set the handler before calling start");
        }
        if (closed.get()) {
            throw new IllegalStateException("You cannot call start after calling close or after completed");
        }
        doLoop();
    }

    @Override
    public void close() {
        close(null);
    }

    @Override
    public void close(Completable<Void> completion) {
        closed.set(true);
        iterator = null;

        if (completion != null) {
            Context context = vertx.getOrCreateContext();
            context.runOnContext(v -> completion.succeed());
        }

        if (creatingContext != null) {
            creatingContext.removeCloseHook(this);
        }
    }
}
