package org.mindignited.structures.internal.api.hooks;

import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;

/**
 * {@link DecoratorLogic} holds a {@link C3Decorator} and its {@link UpsertFieldPreProcessor}
 * The {@link C3Decorator} is the decorator that was assigned to some data.
 * The {@link UpsertFieldPreProcessor} will be provided with the {@link C3Decorator} and the value to process.
 *
 * Created by Navíd Mitchell 🤪 on 5/10/23.
 */
public class DecoratorLogic {

    private final C3Decorator decorator;

    private final UpsertFieldPreProcessor<C3Decorator, Object, Object> processor;

    public DecoratorLogic(C3Decorator decorator,
                          UpsertFieldPreProcessor<C3Decorator, Object, Object> processor) {
        this.decorator = decorator;
        this.processor = processor;
    }

    public C3Decorator getDecorator() {
        return decorator;
    }

    public UpsertFieldPreProcessor<C3Decorator, Object, Object> getProcessor() {
        return processor;
    }
}
