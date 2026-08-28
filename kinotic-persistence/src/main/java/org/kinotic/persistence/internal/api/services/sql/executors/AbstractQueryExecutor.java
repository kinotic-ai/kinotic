package org.kinotic.persistence.internal.api.services.sql.executors;

import org.kinotic.persistence.api.model.EntityDescriptor;

/**
 * Created by Navíd Mitchell 🤪 on 4/29/24.
 */
public abstract class AbstractQueryExecutor implements QueryExecutor {

    protected final EntityDescriptor entityDescriptor;

    public AbstractQueryExecutor(EntityDescriptor entityDescriptor) {
        this.entityDescriptor = entityDescriptor;
    }
}
