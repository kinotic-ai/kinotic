package org.kinotic.core.api.event;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.utils.ZoneUtil;

import java.util.Set;

/**
 * The zones a participant may address: the zones it may send to and the zones it may subscribe in. Zones come from
 * the CRI itself, so an un-zoned address is only ever sendable by a participant that may send to any zone.
 * {@link #restrictedTo(ZonePartitioningService)} narrows the rules to what one server serves, and
 * {@link #subscribingUnderScope(String)} to the one scope a participant hosts services under. A server derives each
 * participant's rules with its {@link ZoneRulesResolver}.
 */
public class ZoneRules {

    private static final ZonePartitioningService EVERY_ZONE = ZonePartitioningService.everyZone("every-zone");

    private final boolean sendAnyZone;
    private final Set<String> sendZones;
    private final Set<String> subscribableZones;
    private final ZonePartitioningService partitioning;
    // the one scope a subscription must carry, or null when any scope and none are allowed
    private final String subscribeScope;

    private ZoneRules(boolean sendAnyZone, Set<String> sendZones, Set<String> subscribableZones, ZonePartitioningService partitioning,
                      String subscribeScope) {
        this.sendAnyZone = sendAnyZone;
        this.sendZones = sendZones;
        this.subscribableZones = subscribableZones;
        this.partitioning = partitioning;
        this.subscribeScope = subscribeScope;
    }

    /**
     * Rules for a participant that may send to any zone, un-zoned addresses included.
     *
     * @param subscribableZones the zones, with their sub-zones, the participant may subscribe in
     * @return the rules
     */
    public static ZoneRules sendingToAnyZone(Set<String> subscribableZones) {
        Validate.notNull(subscribableZones, "subscribableZones must not be null");
        return new ZoneRules(true, Set.of(), Set.copyOf(subscribableZones), EVERY_ZONE, null);
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
        return new ZoneRules(false, Set.copyOf(sendZones), Set.copyOf(subscribableZones), EVERY_ZONE, null);
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
        return new ZoneRules(sendAnyZone, sendZones, subscribableZones, partitioning, subscribeScope);
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
        return new ZoneRules(sendAnyZone, sendZones, subscribableZones, partitioning, scope);
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

    public boolean subscribeAllowed(CRI cri) {
        boolean ret;
        if (isRoutableScheme(cri.scheme())) {
            ret = zoneAllowed(cri.zone(), subscribableZones) && partitioning.hosts(cri.baseResource())
                    && (subscribeScope == null || subscribeScope.equals(cri.scope()));
        } else {
            ret = false;
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
