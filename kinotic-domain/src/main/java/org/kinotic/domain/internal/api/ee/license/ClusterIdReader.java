package org.kinotic.domain.internal.api.ee.license;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemTrustOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the id of the Kubernetes cluster this server runs in: the UID of the cluster's {@code kube-system} namespace,
 * which a license key bound to the cluster names in its {@code clusterId} claim. The server's service account needs
 * {@code get} on that namespace.
 */
@Component
@RequiredArgsConstructor
public class ClusterIdReader {

    // the in-cluster configuration every pod is given: the API server's address in the environment, and the service
    // account's token and the cluster's CA mounted at the standard path
    private static final String HOST_VARIABLE = "KUBERNETES_SERVICE_HOST";
    private static final String PORT_VARIABLE = "KUBERNETES_SERVICE_PORT";
    private static final String ACCOUNT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/";
    private static final String NAMESPACE_URI = "/api/v1/namespaces/kube-system";

    private final Vertx vertx;

    /**
     * @return the cluster's id, or {@code null} when the server does not run in Kubernetes; fails when it does and the
     *         {@code kube-system} namespace cannot be read
     */
    public Future<String> read() {
        String host = System.getenv(HOST_VARIABLE);
        if (host == null) {
            return Future.succeededFuture();
        }
        String port = System.getenv(PORT_VARIABLE);
        HttpClientAgent client = vertx.createHttpClient(new HttpClientOptions()
                                                                .setSsl(true)
                                                                .setTrustOptions(new PemTrustOptions().addCertPath(ACCOUNT_PATH + "ca.crt")));
        return vertx.fileSystem()
                    .readFile(ACCOUNT_PATH + "token")
                    .compose(token -> client.request(new RequestOptions().setMethod(HttpMethod.GET)
                                                                         .setHost(host)
                                                                         .setPort(port != null ? Integer.parseInt(port) : 443)
                                                                         .setURI(NAMESPACE_URI)
                                                                         .putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.toString().trim())))
                    .compose(request -> request.send())
                    .compose(response -> response.body().map(body -> {
                        if (response.statusCode() != 200) {
                            throw new IllegalStateException("The kube-system namespace could not be read (HTTP " + response.statusCode()
                                                                    + "); the server's service account needs get on it: " + body);
                        }
                        return new JsonObject(body).getJsonObject("metadata").getString("uid");
                    }))
                    .eventually(() -> client.close());
    }
}
