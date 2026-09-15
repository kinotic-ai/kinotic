/**
 * One microservice artifact of a project: a package directly under packages/microservices
 * of the project's checkout.
 */
export interface MicroserviceArtifact {
    /**
     * The artifact's identity, the unscoped part of the name in the package's package.json:
     * "@acme/orders" is "orders". Always a single zone label.
     */
    name: string
    /**
     * The package directory, relative to the checkout root.
     */
    dir: string
    /**
     * The module the runtime starts, relative to dir: the package.json main, or src/main.ts
     * when it declares none.
     */
    entry: string
}
