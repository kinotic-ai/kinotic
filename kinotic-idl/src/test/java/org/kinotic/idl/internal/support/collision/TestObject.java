package org.kinotic.idl.internal.support.collision;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Shares {@code support.TestObject}'s simple name from a different package with a different structure. A
 * generic instantiated with each collides on the monomorphized name, which is what the collision tests need
 * — and why this type has a package to itself.
 */
@Setter
@Getter
@Accessors(chain = true)
public class TestObject {

    private String id;

}
