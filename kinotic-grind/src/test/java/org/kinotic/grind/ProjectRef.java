package org.kinotic.grind;

/**
 * A typed job input consumed by tasks-class tests.
 *
 * @param id identifies the project in assertions
 */
public record ProjectRef(String id) {
}
