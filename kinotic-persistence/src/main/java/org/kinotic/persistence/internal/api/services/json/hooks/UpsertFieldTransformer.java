package org.kinotic.persistence.internal.api.services.json.hooks;

import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.persistence.internal.api.services.json.JsonTransformer;

/**
 * Transforms a field carrying a given {@link C3Decorator} as an entity is saved or updated. Each json path of an
 * entity definition decorated with {@link #implementsDecorator()} is handed to this transformer.
 *
 * Created by Navíd Mitchell 🤪 on 5/10/23.
 */
public interface UpsertFieldTransformer extends JsonTransformer<EntityUpsertState> {

    /**
     * @return the {@link C3Decorator} class whose fields this transformer handles
     */
    Class<? extends C3Decorator> implementsDecorator();
}
