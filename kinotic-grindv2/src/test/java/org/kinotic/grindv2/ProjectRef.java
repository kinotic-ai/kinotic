package org.kinotic.grindv2;

/**
 * A typed job input consumed by steps-class tests.
 *
 * @param id identifies the project in assertions
 */
public record ProjectRef(String id) {
}
