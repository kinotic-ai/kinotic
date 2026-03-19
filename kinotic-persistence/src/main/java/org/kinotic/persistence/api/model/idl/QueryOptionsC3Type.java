package org.kinotic.persistence.api.model.idl;

import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.IntC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.persistence.api.model.QueryOptions;

/**
 * A {@link QueryOptionsC3Type} represents the {@link QueryOptions} type.
 * This allows us to detect its usage in {@link FunctionDefinition} parameters and apply the necessary logic.
 * Created By Navíd Mitchell 🤪on 2/24/25
 */
public class QueryOptionsC3Type extends ObjectC3Type {

    public QueryOptionsC3Type() {
        addProperty("timeZone", new StringC3Type());
        addProperty("requestTimeout", new IntC3Type());
        addProperty("pageTimeout", new StringC3Type());
    }

}
