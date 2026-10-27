package org.kinotic.system.api.config;

/**
 * Where published UIs' files are stored, and so how a site comes to serve.
 */
public enum SiteStorageType {

    /**
     * The Azure sites storage account the edge serves every site from: each workload's URL is scoped to
     * one site's directory, and a site is ready once a request through the edge serves its commit.
     */
    AZURE,

    /**
     * The Azurite emulator a machine's local edge serves every site from: a site is ready once its upload
     * finished. Each workload's URL names its site's directory and carries a SAS for the whole sites container,
     * signed with Azurite's well-known development key.
     */
    AZURITE,

    /**
     * No storage: a deployment uploads no UI, a removed UI's site is deleted without touching storage,
     * and every published UI is marked ready at once with nothing serving it.
     */
    DISABLED

}
