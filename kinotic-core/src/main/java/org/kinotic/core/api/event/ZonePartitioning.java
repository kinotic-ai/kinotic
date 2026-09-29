package org.kinotic.core.api.event;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.utils.ZoneUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The zones a server hosts and the zones it reaches. A node advertises an event bus consumer to the cluster only
 * in a zone it hosts, and routes a send or publish only to a zone it reaches. A zone covers its sub-zones, so
 * {@code app} covers {@code app.acme.orders}, and an address without a zone is platform-internal and passes both.
 * The server module declares its partitioning as a bean; a server that declares none hosts and reaches every zone.
 */
public final class ZonePartitioning {

    private static final String HOSTS_ATTRIBUTE_PREFIX = "kinotic.zone.";

    private final String name;
    private final Set<String> hostedZones;
    private final Set<String> reachableZones;

    private ZonePartitioning(String name, Set<String> hostedZones, Set<String> reachableZones) {
        this.name = name;
        this.hostedZones = hostedZones;
        this.reachableZones = reachableZones;
    }

    /**
     * A server that hosts {@code hostedZones} and routes to {@code reachableZones}.
     * <p>
     * The two sets answer different questions. Hosting is about the services this server itself serves: a
     * consumer registers an address only in a hosted zone, the gateway lets a connection subscribe only in one,
     * and the node carries a {@link #hostsAttribute(String)} for each so the cluster can select the nodes hosting a
     * zone. Reaching is about the services a caller may call through this server: a send or publish looks up
     * handlers only in a reachable zone, on whichever node in the cluster registered them, the gateway lets a
     * connection send only to one, and the service listings narrow to them. So a server reaches a zone it does
     * not host whenever the services it hosts, or the connections it serves, call services another server hosts
     * in that zone.
     * <p>
     * Every hosted zone must also be reachable, because a send to a consumer on this same node goes through the
     * same routing lookup as a send to any other node.
     *
     * @param name           names the server kind, such as {@code org}
     * @param hostedZones    the zones the server hosts consumers in
     * @param reachableZones the zones the server routes to, which must cover every hosted zone
     * @return the partitioning
     */
    public static ZonePartitioning of(String name, Set<String> hostedZones, Set<String> reachableZones) {
        Validate.notBlank(name, "name must not be blank");
        Validate.notEmpty(hostedZones, "hostedZones must not be empty");
        Validate.notEmpty(reachableZones, "reachableZones must not be empty");
        hostedZones.forEach(ZoneUtil::validateZone);
        reachableZones.forEach(ZoneUtil::validateZone);
        // a node routes its own local sends through the cluster view too, so it must reach what it hosts
        for (String zone : hostedZones) {
            Validate.isTrue(ZoneUtil.zoneMatches(zone, reachableZones), "The hosted zone '%s' is not reachable", zone);
        }
        return new ZonePartitioning(name, Set.copyOf(hostedZones), Set.copyOf(reachableZones));
    }

    /**
     * A server that hosts and reaches every zone, as one server running every module does.
     *
     * @param name names the server kind
     * @return the partitioning
     */
    public static ZonePartitioning everyZone(String name) {
        Validate.notBlank(name, "name must not be blank");
        return new ZonePartitioning(name, null, null);
    }

    /**
     * The name of the Ignite node attribute, set to {@code true}, that marks a node hosting the given zone.
     *
     * @param zone the zone
     * @return the attribute name, {@code kinotic.zone.<zone>}
     */
    public static String hostsAttribute(String zone) {
        return HOSTS_ATTRIBUTE_PREFIX + zone;
    }

    /**
     * Names the server kind, and the cluster-wide structures only servers of that kind share.
     */
    public String name() {
        return name;
    }

    /**
     * Whether this server hosts consumers of the address.
     *
     * @param address an event bus address or CRI
     * @return true when the address has no zone or its zone is hosted here
     */
    public boolean hosts(String address) {
        String zone = ZoneUtil.zoneOf(address);
        return zone == null || hostedZones == null || ZoneUtil.zoneMatches(zone, hostedZones);
    }

    /**
     * Whether this server routes to the address.
     *
     * @param address an event bus address or CRI
     * @return true when the address has no zone or its zone is reachable from here
     */
    public boolean reaches(String address) {
        return reachesZone(ZoneUtil.zoneOf(address));
    }

    /**
     * Whether this server routes to the zone.
     *
     * @param zone a zone, or {@code null} for an address without one
     * @return true when the zone is {@code null} or reachable from here
     */
    public boolean reachesZone(String zone) {
        return zone == null || reachableZones == null || ZoneUtil.zoneMatches(zone, reachableZones);
    }

    /**
     * The zones this server reaches, each covering its sub-zones; empty for a server that reaches every zone.
     */
    public Optional<Set<String>> reachableZones() {
        return Optional.ofNullable(reachableZones);
    }

    /**
     * The Ignite node attributes a node of this server carries: a {@link #hostsAttribute(String)} for each hosted
     * zone, none for a server that hosts every zone.
     */
    public Map<String, Object> nodeAttributes() {
        Map<String, Object> ret = new HashMap<>();
        if (hostedZones != null) {
            hostedZones.forEach(zone -> ret.put(hostsAttribute(zone), Boolean.TRUE));
        }
        return ret;
    }

    @Override
    public String toString() {
        return hostedZones == null
                ? name + " (every zone)"
                : name + " (hosts " + hostedZones + ", reaches " + reachableZones + ")";
    }
}
