package org.kinotic.core.api;

/**
 * What a Kinotic server runs, which its nodes advertise to the cluster so the nodes of a type can be selected. The
 * server module declares its type as a bean; a server that declares none runs every module and is selected with
 * every type.
 */
public enum ServerType {
    /** The management server: the management-api services behind the portal. */
    MANAGEMENT,
    /** The system server: the system-api services behind the system console, which deployments run through. */
    SYSTEM,
    /** The app server: the app-api services and the services every application publishes. */
    APP
}
