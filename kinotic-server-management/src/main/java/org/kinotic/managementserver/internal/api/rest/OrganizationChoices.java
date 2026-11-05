package org.kinotic.managementserver.internal.api.rest;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;

import java.time.Duration;
import java.util.List;

/**
 * The organizations a social identity the browser just verified signs in to, held in the browser session for the
 * page that started the round-trip, so that page can offer them: the landing page when the identity signs in to
 * several, the org-naming page of a sign-up when it signs in to any. The offer is answered for two minutes and
 * ends when the page starts a login at an organization.
 */
final class OrganizationChoices {

    private static final String KEY = "organizationChoices";
    // long enough for the page the round-trip returns to to load and ask, short enough that the ids are gone
    // from a browser left on that page
    private static final Duration VALIDITY = Duration.ofMinutes(2);

    private OrganizationChoices() {}

    /**
     * Offers the organizations of {@code users} to the page at {@code origin}, replacing any earlier offer; the
     * redirect back to the page carries no ids, since the page reads them with {@link #read(RoutingContext)}.
     */
    static void offer(RoutingContext ctx, String origin, List<OrganizationParticipantIdentity> users) {
        // read without being removed, since a concurrent request on the session would make the removal
        // unreliable: the offer expires by its timestamp and ends when the page starts an organization's login
        JsonObject offer = new JsonObject()
                .put("organizations", new JsonArray(users.stream().map(OrganizationParticipantIdentity::getOrganizationId).sorted().toList()))
                .put("at", System.currentTimeMillis());
        ctx.session().put(key(origin), offer);
    }

    /** The organization ids offered to the requesting page and not yet expired, or an empty array. */
    static JsonArray read(RoutingContext ctx) {
        JsonArray ret = new JsonArray();
        // only a request presenting the cookie consults the session, since reading a lazy session creates one
        if (SessionBinding.sessionPresented(ctx)) {
            JsonObject offer = ctx.session().get(key(SessionBinding.origin(ctx)));
            if (offer != null && System.currentTimeMillis() - offer.getLong("at") < VALIDITY.toMillis()) {
                ret = offer.getJsonArray("organizations");
            }
        }
        return ret;
    }

    /** Ends the offer made to the requesting page. */
    static void end(RoutingContext ctx) {
        if (SessionBinding.sessionPresented(ctx)) {
            ctx.session().remove(key(SessionBinding.origin(ctx)));
        }
    }

    private static String key(String origin) {
        return SessionBinding.pageKey(KEY, origin);
    }
}
