package org.kinotic.core.api.event;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.utils.ZoneUtil;

import java.util.Set;
import java.util.function.Function;

/**
 * The zones a participant may address: the zones it may send to and the zones it may subscribe in. Zones come from
 * the CRI itself, so an un-zoned address is only ever sendable by a participant that may send to any zone.
 * {@link #restrictedTo(ZonePartitioningService)} narrows the rules to what one server serves,
 * {@link #subscribingUnderScope(String)} to the one scope a participant hosts services under, and
 * {@link #subscribingWhere(Function)} to the zones a check admits when a subscription names them. A server derives
 * each participant's rules with its {@link ZoneRulesResolver}.
 */
public class ZoneRules {

    private static final ZonePartitioningService EVERY_ZONE = ZonePartitioningService.everyZone("every-zone");

    private final boolean sendAnyZone;
    private final Set<String> sendZones;
    private final Set<String> subscribableZones;
    private final ZonePartitioningService partitioning;
    // the one scope a subscription must carry, or null when any scope and none are allowed
    private final String subscribeScope;
    // asked for the zone of a subscription the rules otherwise allow, or null when they decide alone
    private final Function<String, Future<Boolean>> subscribeCheck;

    private ZoneRules(boolean sendAnyZone, Set<String> sendZones, Set<String> subscribableZones, ZonePartitioningService partitioning,
                      String subscribeScope, Function<String, Future<Boolean>> subscribeCheck) {
        this.sendAnyZone = sendAnyZone;
        this.sendZones = sendZones;
        this.subscribableZones = subscribableZones;
        this.partitioning = partitioning;
        this.subscribeScope = subscribeScope;
        this.subscribeCheck = subscribeCheck;
    }

    /**
     * Rules for a participant that may send to any zone, un-zoned addresses included.
     *
     * @param subscribableZones the zones, with their sub-zones, the participant may subscribe in
     * @return the rules
     */
    public static ZoneRules sendingToAnyZone(Set<String> subscribableZones) {
        Validate.notNull(subscribableZones, "subscribableZones must not be null");
        return new ZoneRules(true, Set.of(), Set.copyOf(subscribableZones), EVERY_ZONE, null, null);
    }

    /**
     * Rules for a participant that may send only to the zones it is given.
     *
     * @param sendZones         the zones, with their sub-zones, the participant may send to
     * @param subscribableZones the zones, with their sub-zones, the participant may subscribe in
     * @return the rules
     */
    public static ZoneRules of(Set<String> sendZones, Set<String> subscribableZones) {
        Validate.notNull(sendZones, "sendZones must not be null");
        Validate.notNull(subscribableZones, "subscribableZones must not be null");
        return new ZoneRules(false, Set.copyOf(sendZones), Set.copyOf(subscribableZones), EVERY_ZONE, null, null);
    }

    /**
     * These rules narrowed to what one server serves: a send only to a zone the partitioning reaches, a subscription
     * only in a zone it hosts.
     *
     * @param partitioning the server's zone partitioning
     * @return the narrowed rules
     */
    public ZoneRules restrictedTo(ZonePartitioningService partitioning) {
        Validate.notNull(partitioning, "partitioning must not be null");
        return new ZoneRules(sendAnyZone, sendZones, subscribableZones, partitioning, subscribeScope, subscribeCheck);
    }

    /**
     * These rules narrowed to a participant that hosts services under one scope alone: a subscription only to an
     * address that carries {@code scope}, so the participant answers no other instance's calls.
     *
     * @param scope the scope every address the participant subscribes to must carry
     * @return the narrowed rules
     */
    public ZoneRules subscribingUnderScope(String scope) {
        Validate.notBlank(scope, "scope must not be blank");
        return new ZoneRules(sendAnyZone, sendZones, subscribableZones, partitioning, scope, subscribeCheck);
    }

    /**
     * These rules narrowed to the zones a check admits: a subscription the rules allow is made only when
     * {@code admits} answers true for its zone. The check is asked on each subscription, so a participant's zones
     * are decided as it subscribes in them.
     *
     * @param admits answers, for the zone of a subscription, whether the participant may subscribe in it
     * @return the narrowed rules
     */
    public ZoneRules subscribingWhere(Function<String, Future<Boolean>> admits) {
        Validate.notNull(admits, "admits must not be null");
        return new ZoneRules(sendAnyZone, sendZones, subscribableZones, partitioning, subscribeScope, admits);
    }

    public boolean sendAllowed(CRI cri) {
        boolean ret;
        if (isRoutableScheme(cri.scheme())) {
            ret = (sendAnyZone || zoneAllowed(cri.zone(), sendZones)) && partitioning.reaches(cri.baseResource());
        } else {
            ret = false;
        }
        return ret;
    }

    /**
     * Whether the participant may subscribe to the address.
     *
     * @param cri the address
     * @return a future completing with true when the subscription is allowed, at once unless the rules were
     *         narrowed with {@link #subscribingWhere(Function)}
     */
    public Future<Boolean> subscribeAllowed(CRI cri) {
        boolean allowed = isRoutableScheme(cri.scheme())
                && zoneAllowed(cri.zone(), subscribableZones) && partitioning.hosts(cri.baseResource())
                && (subscribeScope == null || subscribeScope.equals(cri.scope()));
        Future<Boolean> ret;
        if (allowed && subscribeCheck != null) {
            ret = subscribeCheck.apply(cri.zone());
        } else {
            ret = Future.succeededFuture(allowed);
        }
        return ret;
    }

    // only srv addresses route through the gateway; stream routing is not yet supported
    private static boolean isRoutableScheme(String scheme) {
        return EventConstants.SERVICE_DESTINATION_SCHEME.equals(scheme);
    }

    private static boolean zoneAllowed(String zone, Set<String> allowedZones) {
        return zone != null && ZoneUtil.zoneMatches(zone, allowedZones);
    }
}
