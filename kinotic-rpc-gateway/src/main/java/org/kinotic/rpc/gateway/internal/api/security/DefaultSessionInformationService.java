

package org.kinotic.rpc.gateway.internal.api.security;

import io.vertx.core.Vertx;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ScanQuery;
import org.kinotic.rpc.api.event.StreamData;
import org.kinotic.rpc.api.security.SessionMetadata;
import org.kinotic.rpc.gateway.api.security.SessionInformationService;
import org.kinotic.rpc.internal.config.IgniteCacheConstants;
import org.kinotic.rpc.internal.api.aignite.IgniteContinuousQueryObserver;
import org.kinotic.rpc.internal.api.security.DefaultSessionMetadata;
import org.kinotic.rpc.internal.utils.IgniteUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 *
 * Created by Navid Mitchell on 7/1/20
 */
@Component
public class DefaultSessionInformationService implements SessionInformationService {

    private final Vertx vertx;
    private final Ignite ignite;
    private final IgniteCache<String, DefaultSessionMetadata> sessionCache;
    private final Scheduler scheduler;


    public DefaultSessionInformationService(Vertx vertx,
                                            @Autowired(required = false) Ignite ignite) {
        this.vertx = vertx;
        this.ignite = ignite;

        // Will be null when running some tests
        if(ignite !=  null){
            sessionCache = ignite.cache(IgniteCacheConstants.SESSION_CACHE_NAME);
        }else{
            sessionCache = null;
        }

        scheduler = Schedulers.fromExecutor(command -> vertx.executeBlocking(() -> {
            command.run();
            return null;
        }));
    }

    @Override
    public Flux<Long> countActiveSessionsContinuous() {
        return IgniteUtil.countCacheEntriesContinuous(ignite, vertx, sessionCache);
    }

    @Override
    public Flux<StreamData<String, SessionMetadata>> listActiveSessionsContinuous() {
        if(ignite == null){
            throw new IllegalStateException("This method is not available when ignite is disabled");
        }
        Flux<StreamData<String, SessionMetadata>> ret;

        ret = IgniteUtil.observerToFlux(() -> new IgniteContinuousQueryObserver<>(vertx, sessionCache, new ScanQuery<>()));
        return ret.subscribeOn(scheduler);
    }

}
