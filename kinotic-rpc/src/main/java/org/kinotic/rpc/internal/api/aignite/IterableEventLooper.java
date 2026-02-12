

package org.kinotic.rpc.internal.api.aignite;

import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Navid Mitchell on 8/3/17.
 */
public class IterableEventLooper<T> implements SuspendableObserver<T> , Closeable{
    private static final Logger log = LoggerFactory.getLogger(IterableEventLooper.class);

    private final Vertx vertx;
    private Iterable<T> iterable;
    private final boolean closeIterableOnComplete;
    private final ContextInternal creatingContext;

    private Handler<T> resultHandler;
    private Handler<Void> completionHandler;
    private Handler<Throwable> exceptionHandler;
    private IteratorEventLooper<T> cursorIterator;


    /**
     * Constructs a new {@link IterableEventLooper}
     * {@link IterableEventLooper} will always close the iterable provided even if this method throws an error.
     * NOTE: will not be closed if start is never called
     *
     * @param vertx instance to use
     * @param iterable to loop over using the vertx event loop
     *
     * @throws IllegalArgumentException if any parameter is null
     * @throws IllegalStateException if the current vertx context belongs to a multi threaded worker
     */
    public IterableEventLooper(Vertx vertx,
                               Iterable<T> iterable){
        this(vertx,iterable,true);
    }


    /**
     * Constructs a new {@link IterableEventLooper}
     * {@link IterableEventLooper} will always close the iterable provided even if this method throws an error.
     * NOTE: will not be closed if start is never called
     *
     * @param vertx instance to use
     * @param iterable to loop over using the vertx event loop
     * @param closeIterableOnComplete if true and iterable is {@link AutoCloseable} then {@link AutoCloseable#close} will be called after the completion handler is called
     *                            if false then {@link AutoCloseable#close} will only be called on exceptions and when close or close is called on this {@link IterableEventLooper}
     *
     * @throws IllegalArgumentException if any parameter is null
     * @throws IllegalStateException if the current vertx context belongs to a multi threaded worker
     */
    public IterableEventLooper(Vertx vertx,
                               Iterable<T> iterable,
                               boolean closeIterableOnComplete) {
        try {
            Validate.notNull(vertx, "Vertx is null");
            Validate.notNull(iterable, "Iterable is null");

            this.vertx = vertx;
            this.iterable = iterable;
            this.closeIterableOnComplete = closeIterableOnComplete;

            this.creatingContext = (ContextInternal)vertx.getOrCreateContext();
            creatingContext.addCloseHook(this);

        }catch (Exception e){
            close();
            throw e;
        }
    }


    private synchronized void closeIfAutoCloseable(){
        if(iterable instanceof AutoCloseable){
            creatingContext.executeBlocking(() -> {
                try {
                    if(iterable != null) {
                        ((AutoCloseable) iterable).close();
                        iterable = null;
                    }
                } catch (Exception e2) {
                    log.error("AutoCloseable Iterable threw error during close",e2);
                }
                return null;
            });
        }
    }


    @Override
    public Observer<T> handler(Handler<T> handler) {
        resultHandler =  handler;
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
        cursorIterator.suspend();
        return this;
    }

    @Override
    public SuspendableObserver<T> resume() {
        cursorIterator.resume();
        return this;
    }

    public void start() {
        creatingContext
                .executeBlocking(() -> new IteratorEventLooper<>(vertx, iterable.iterator()))
                .onComplete(result -> {
                    if(result.succeeded()) {
                        // initialize internal vars for use later
                        this.cursorIterator = result.result();

                        cursorIterator.handler(data -> {
                            if(resultHandler != null){
                                resultHandler.handle(data);
                            }
                        });

                        // setup internal handlers to make sure iterable is closed
                        cursorIterator.completionHandler(event -> {

                            if (closeIterableOnComplete) {
                                close();
                            }

                            if (completionHandler != null) {
                                creatingContext.runOnContext(v -> completionHandler.handle(null));
                            }
                        });

                        cursorIterator.exceptionHandler(event -> {

                            close();

                            if (exceptionHandler != null) {
                                creatingContext.runOnContext(v -> exceptionHandler.handle(event));
                            }
                        });

                        // now actually start doing work
                        cursorIterator.start();
                    }else{
                        close();
                        if(exceptionHandler != null){
                            creatingContext.runOnContext(v -> exceptionHandler.handle(new IllegalStateException("Exception creating IteratorEventLooper", result.cause())));
                        }
                    }
                });
    }

    @Override
    public void close() {
        close(null);
    }

    @Override
    public void close(Completable<Void> completion) {
        if(cursorIterator != null){
            cursorIterator.close();
        }

        // handles any context closing to ensure iterable is closed as well
        closeIfAutoCloseable();

        if (completion != null) {
            Context context = vertx.getOrCreateContext();
            context.runOnContext(v -> completion.succeed());
        }

        if (creatingContext != null) {
            creatingContext.removeCloseHook(this);
        }
    }


}
