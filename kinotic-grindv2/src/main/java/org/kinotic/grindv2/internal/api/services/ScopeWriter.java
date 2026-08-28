package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.api.model.DefaultJobContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.kinotic.grindv2.api.model.StoreType;

import java.beans.Introspector;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

/**
 * Writes a task's result into the job scope according to its {@link StoreType}: bean
 * candidates register as beans so later tasks can inject them by type, simple values store as
 * named properties, and an unnamed collection registers each element as its own bean.
 */
@Slf4j
public class ScopeWriter {

    /**
     * Stores the given value in the scope.
     * @param scope the scope to write into
     * @param storeType how the value is kept; {@link StoreType#NONE} stores nothing
     * @param resultName the declared name to store under, or null to derive one
     * @param value the value to store
     * @return the name the value was stored under, or null when nothing was stored or the
     *         elements were stored under generated names
     */
    public static String store(DefaultJobContext scope, StoreType storeType, String resultName, Object value) {
        String ret = null;
        if (storeType != StoreType.NONE && value != null) {
            if (isBeanCandidate(value)) {
                if (value instanceof Collection<?> collection && resultName == null) {
                    // Without a name a collection cannot be one property, so each element becomes
                    // an injectable bean and by-type injection of List<T> collects them
                    for (Object element : collection) {
                        scope.storeBean(element.getClass().getSimpleName() + "_" + UUID.randomUUID(), element);
                    }
                } else if (value instanceof Collection<?>) {
                    scope.storeProperty(resultName, value);
                    ret = resultName;
                } else {
                    ret = resultName != null ? resultName : Introspector.decapitalize(value.getClass().getSimpleName());
                    scope.storeBean(ret, value);
                }
            } else if (resultName != null) {
                scope.storeProperty(resultName, value);
                ret = resultName;
            } else {
                log.warn("Result of type {} needs a declared name to be stored, nothing was stored",
                         value.getClass().getName());
            }
        } else if (storeType != StoreType.NONE) {
            log.warn("Result was requested to be stored, but the result is null");
        }
        return ret;
    }

    private static boolean isBeanCandidate(Object value) {
        Class<?> clazz = value.getClass();
        return !clazz.isArray()
                && !clazz.isEnum()
                && !ClassUtils.isPrimitiveOrWrapper(clazz)
                && !(value instanceof CharSequence)
                && !(value instanceof Date)
                && !(value instanceof Calendar);
    }

}
