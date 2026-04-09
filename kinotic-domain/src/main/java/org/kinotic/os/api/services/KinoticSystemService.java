package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.os.api.model.KinoticSystem;

import java.util.concurrent.CompletableFuture;

@Publish
public interface KinoticSystemService {

    CompletableFuture<KinoticSystem> getSystem();

    CompletableFuture<KinoticSystem> save(KinoticSystem system);

}
