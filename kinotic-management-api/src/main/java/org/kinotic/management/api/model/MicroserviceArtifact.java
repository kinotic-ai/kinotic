package org.kinotic.management.api.model;

/**
 * One microservice artifact of a project: a package directly under
 * {@code packages/microservices} of the project's checkout.
 *
 * @param name  the artifact's identity, the unscoped part of the {@code name} in the package's
 *              {@code package.json}: {@code @acme/orders} is {@code orders}. Always a single
 *              zone label
 * @param dir   the package directory, relative to the checkout root
 * @param entry the module the runtime starts, relative to {@link #dir}: the
 *              {@code package.json} {@code main}, or {@code src/main.ts} when it declares none
 */
public record MicroserviceArtifact(String name, String dir, String entry) {}
