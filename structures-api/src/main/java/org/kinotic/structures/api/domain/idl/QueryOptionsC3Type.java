package org.kinotic.structures.api.domain.idl;

import org.kinotic.continuum.idl.api.schema.FunctionDefinition;
import org.kinotic.continuum.idl.api.schema.IntC3Type;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.kinotic.continuum.idl.api.schema.StringC3Type;
import org.kinotic.structures.api.domain.QueryOptions;

/**
 * A {@link QueryOptionsC3Type} represents the {@link QueryOptions} type.
 * This allows us to detect its usage in {@link FunctionDefinition} parameters and apply the needed logic.
 * Created By Navíd Mitchell 🤪on 2/24/25
 */
public class QueryOptionsC3Type extends ObjectC3Type {

    public QueryOptionsC3Type() {
        addProperty("timeZone", new StringC3Type());
        addProperty("requestTimeout", new IntC3Type());
        addProperty("pageTimeout", new StringC3Type());
    }

}
