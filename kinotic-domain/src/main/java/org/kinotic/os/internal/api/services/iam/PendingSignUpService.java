package org.kinotic.os.internal.api.services.iam;

import jakarta.annotation.PostConstruct;
import org.kinotic.os.api.model.iam.PendingSignUp;

import java.util.concurrent.CompletableFuture;

/**
 *
 * Created By Navíd Mitchell 🤪on 4/11/26
 */
public interface PendingSignUpService {
    @PostConstruct
    void verifyIndexExists();

    CompletableFuture<PendingSignUp> save(PendingSignUp pendingSignUp);

    CompletableFuture<PendingSignUp> findByToken(String verificationToken);

    CompletableFuture<PendingSignUp> findByEmail(String email);

    CompletableFuture<Void> deleteById(String id);
}
