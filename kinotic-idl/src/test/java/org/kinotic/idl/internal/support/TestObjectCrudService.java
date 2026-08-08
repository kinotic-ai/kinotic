package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.McpTool;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/26/26.
 */
@McpTool
public interface TestObjectCrudService extends GenericCrudService<TestObject, String> {

    /**
     * Saves the given test object.
     */
    @Override
    CompletableFuture<TestObject> save(TestObject entity);

}
