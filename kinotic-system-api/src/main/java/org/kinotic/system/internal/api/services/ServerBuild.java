package org.kinotic.system.internal.api.services;

import java.util.List;

/**
 * One build of a Kinotic server: the server kind and version its nodes report, and the libraries
 * their SBOMs list.
 *
 * @param serverName the server's name
 * @param version    the server's version
 * @param packages   the package URLs of the libraries the build runs, or {@code null} when its nodes
 *                   recorded no SBOM
 */
record ServerBuild(String serverName, String version, List<String> packages) {}
