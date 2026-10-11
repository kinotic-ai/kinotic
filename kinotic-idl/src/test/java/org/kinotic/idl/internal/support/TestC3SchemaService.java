package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.decorators.C3Decorator;

import java.util.List;

/**
 * Carries the C3 schema's own types in its signatures.
 */
public interface TestC3SchemaService {

    ObjectC3Type findSchema(String name);

    void decorate(String name, List<C3Decorator> decorators);
}
