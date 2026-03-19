package org.kinotic.persistence.internal.api.services.sql.executors;

import org.kinotic.persistence.api.model.EntityDefinition;

/**
 * Created by Navíd Mitchell 🤪 on 4/29/24.
 */
public abstract class AbstractQueryExecutor implements QueryExecutor {

    protected final EntityDefinition entityDefinition;

    public AbstractQueryExecutor(EntityDefinition entityDefinition) {
        this.entityDefinition = entityDefinition;
    }
}
