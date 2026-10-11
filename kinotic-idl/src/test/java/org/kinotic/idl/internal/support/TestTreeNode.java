package org.kinotic.idl.internal.support;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Refers to its own type, directly through its parent and through the collection of its children.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class TestTreeNode {
    private String name;
    private TestTreeNode parent;
    private List<TestTreeNode> children;
}
