package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterNode;
import org.kinotic.core.api.KinoticNodeAttributes;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.ServerSbom;
import org.kinotic.domain.api.repositories.ServerSbomRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Records the node's {@link ServerSbom} when it starts, deletes it when the node shuts down, and
 * deletes the SBOMs left by nodes that left the cluster without shutting down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerSbomRecorder {

    // Written into each server jar by the serverComponents task of the server convention, one package URL per line
    private static final String COMPONENTS_RESOURCE = "META-INF/kinotic/components.txt";

    private static final long DELETE_TIMEOUT_SECONDS = 10;

    private static final int ID_PAGE_SIZE = 1_000;

    private final Ignite ignite;
    private final ServerSbomRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    public void recordSbom() {
        ClusterNode node = ignite.cluster().localNode();
        Date started = new Date();
        List<String> packages;
        try {
            packages = readComponents();
        } catch (IOException e) {
            log.error("Could not read {}; the node records no SBOM", COMPONENTS_RESOURCE, e);
            return;
        }
        ServerSbom sbom = new ServerSbom().setId(node.id().toString())
                                          .setServerName(node.attribute(KinoticNodeAttributes.SERVER_NAME))
                                          .setVersion(node.attribute(KinoticNodeAttributes.VERSION))
                                          .setPackages(packages)
                                          .setStarted(started);
        repository.save(sbom)
                  .onFailure(error -> log.error("Could not record the node's SBOM", error));
        deleteSbomsOfNodesGone();
    }

    @PreDestroy
    void deleteSbom() {
        // only the node itself deletes its row while it runs, so no node can delete the row of one that is
        // still running; a node that crashes leaves its row for the next node to start to delete
        try {
            repository.deleteById(ignite.cluster().localNode().id().toString())
                      .toCompletionStage()
                      .toCompletableFuture()
                      .get(DELETE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Could not delete the node's SBOM; its row stays behind", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while deleting the node's SBOM; its row stays behind");
        }
    }

    // The rows are listed before the topology is read: a node whose row the listing holds joined the
    // cluster before writing it, so one missing from the topology read after the listing has left
    private void deleteSbomsOfNodesGone() {
        CursorPage.readAll(repository::findAllIds, ID_PAGE_SIZE, Sort.by("id"))
                  .compose(sboms -> {
                      Set<String> running = ignite.cluster().nodes().stream()
                                                  .map(node -> node.id().toString())
                                                  .collect(Collectors.toSet());
                      List<String> gone = sboms.stream()
                                               .map(ServerSbom::getId)
                                               .filter(id -> !running.contains(id))
                                               .toList();
                      Future<Void> ret;
                      if (gone.isEmpty()) {
                          ret = Future.succeededFuture();
                      } else {
                          log.info("Deleting the SBOMs of {} nodes that left the cluster", gone.size());
                          ret = repository.deleteAllById(gone);
                      }
                      return ret;
                  })
                  .onFailure(error -> log.warn("Could not delete the SBOMs of nodes that left the cluster", error));
    }

    // The package URLs the classpath's COMPONENTS_RESOURCE lists, empty when it holds none
    private static List<String> readComponents() throws IOException {
        List<String> ret = new ArrayList<>();
        try (InputStream listing = ServerSbomRecorder.class.getClassLoader().getResourceAsStream(COMPONENTS_RESOURCE)) {
            if (listing != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(listing, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        ret.add(line.trim());
                    }
                }
            }
        }
        return ret;
    }

}
