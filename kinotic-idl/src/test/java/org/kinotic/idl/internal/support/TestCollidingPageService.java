package org.kinotic.idl.internal.support;

/**
 * Instantiates {@link TestPage} with two type arguments that share a simple name across packages, so both
 * functions' returns monomorphize to the same qualified name with different structures.
 */
public interface TestCollidingPageService {

    TestPage<TestObject> findSupportObjects();

    // fully qualified on purpose: importing collision.TestObject would shadow this package's TestObject,
    // making both functions reference the same class and leaving no collision to test
    TestPage<org.kinotic.idl.internal.support.collision.TestObject> findCollisionObjects();

}
