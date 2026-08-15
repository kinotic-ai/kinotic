package org.kinotic.idl.internal.support;

/**
 * Instantiates {@link TestPage} with two type arguments that share a simple name across packages, so both
 * functions' returns monomorphize to the same qualified name with different structures.
 */
public interface TestCollidingPageService {

    TestPage<TestObject> findSupportObjects();

    TestPage<org.kinotic.idl.internal.support.collision.TestObject> findCollisionObjects();

}
