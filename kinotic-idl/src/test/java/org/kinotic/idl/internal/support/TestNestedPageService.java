package org.kinotic.idl.internal.support;

import java.util.List;

/**
 * Instantiates {@link TestPage} with a type argument that is itself generic.
 */
public interface TestNestedPageService {

    TestPage<List<TestObject>> findNested();

}
