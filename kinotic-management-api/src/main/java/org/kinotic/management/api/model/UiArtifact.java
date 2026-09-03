package org.kinotic.management.api.model;

/**
 * One UI artifact of a project: a package directly under {@code packages/ui} of the
 * project's checkout whose {@code package.json} declares a {@code build} script.
 *
 * @param name the artifact's identity, the unscoped part of the {@code name} in the package's
 *             {@code package.json}: {@code @acme/admin} is {@code admin}. Always a single
 *             zone label
 * @param dir  the package directory, relative to the checkout root
 */
public record UiArtifact(String name, String dir) {}
