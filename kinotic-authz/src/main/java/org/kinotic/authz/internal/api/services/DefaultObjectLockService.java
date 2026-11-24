package org.kinotic.authz.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.services.ObjectLockService;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DefaultObjectLockService implements ObjectLockService {

    private static final String OBJECT_LOCK_PREFIX = "kinotic.authz.object.";
    private static final long OBJECT_LOCK_TIMEOUT_MILLIS = 30_000;
    // the locks the objects of one type share
    private static final int OBJECT_LOCK_STRIPES = 512;

    private final Vertx vertx;

    @Override
    public <T> Future<T> exclusively(String object, Supplier<Future<T>> changes) {
        Validate.notBlank(object, "object cannot be blank");
        Validate.notNull(changes, "changes cannot be null");
        return vertx.sharedData().withLock(lockOf(object), OBJECT_LOCK_TIMEOUT_MILLIS, changes);
    }

    // An object takes one of a fixed number of locks of its type, chosen by the hash of its id, which every node
    // computes alike. The cluster keeps every lock name it is asked for, about 300 bytes each in a region of 100 MB
    // that all nodes share, so the names are bounded. Objects sharing a lock wait on each other as callers changing
    // one object do. A type's locks are its own, so a change holding one can take the lock of an object of another
    // type
    private static String lockOf(String object) {
        return OBJECT_LOCK_PREFIX + AuthzNameUtil.typeOf(object) + "." + Math.floorMod(AuthzNameUtil.idOf(object).hashCode(), OBJECT_LOCK_STRIPES);
    }

    @Override
    public <T> Future<T> createExclusively(String object, Supplier<Future<T>> stored, Supplier<Future<Void>> clear, Supplier<Future<T>> create) {
        // the removal runs only when no record is stored, so it never takes the place of a record in use, and
        // under the lock a deletion takes, so a deletion's removal never takes the place this create writes
        return exclusively(object, () -> stored.get()
                .compose(existing -> existing == null
                        ? clear.get()
                        : Future.failedFuture(new AlreadyExistsException(object + " already exists")))
                .compose(v -> create.get()));
    }
}
