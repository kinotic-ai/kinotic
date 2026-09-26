package org.kinotic.core.api.event;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.utils.ZoneUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The zones a server hosts and the zones it reaches. A node advertises an event bus consumer to the cluster only
 * in a zone it hosts, and routes a send or publish only to a zone it reaches. A zone covers its sub-zones, so
 * {@code app} covers {@code app.acme.orders}, and an address without a zone is platform-internal and passes both.
 * The server module declares its partition as a bean; a server that declares none hosts and reaches every zone.
 */
public final class ZonePartition {

    private static final String HOSTS_ATTRIBUTE_PREFIX = "kinotic.zone.";

    private final String name;
    private final Set<String> hostedZones;
    private final Set<String> reachableZones;

    private ZonePartition(String name, Set<String> hostedZones, Set<String> reachableZones) {
        this.name = name;
        this.hostedZones = hostedZones;
        this.reachableZones = reachableZones;
    }

    /**
     * A server that hosts {@code hostedZones} and routes to {@code reachableZones}.
     *
     * @param name           names the server kind, such as {@code org}
     * @param hostedZones    the zones the server hosts consumers in
     * @param reachableZones the zones the server routes to, which must cover every hosted zone
     * @return the partition
     */
    public static ZonePartition of(String name, Set<String> hostedZones, Set<String> reachableZones) {
        Validate.notBlank(name, "name must not be blank");
        Validate.notEmpty(hostedZones, "hostedZones must not be empty");
        Validate.notEmpty(reachableZones, "reachableZones must not be empty");
        hostedZones.forEach(ZoneUtil::validateZone);
        reachableZones.forEach(ZoneUtil::validateZone);
        // a node routes its own local sends through the cluster view too, so it must reach what it hosts
        for (String zone : hostedZones) {
            Validate.isTrue(ZoneUtil.zoneMatches(zone, reachableZones), "The hosted zone '%s' is not reachable", zone);
        }
        return new ZonePartition(name, Set.copyOf(hostedZones), Set.copyOf(reachableZones));
    }

    /**
     * A server that hosts and reaches every zone, as one server running every module does.
     *
     * @param name names the server kind
     * @return the partition
     */
    public static ZonePartition everyZone(String name) {
        Validate.notBlank(name, "name must not be blank");
        return new ZonePartition(name, null, null);
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
        return covers(hostedZones, address);
    }

    /**
     * Whether this server routes to the address.
     *
     * @param address an event bus address or CRI
     * @return true when the address has no zone or its zone is reachable from here
     */
    public boolean reaches(String address) {
        return covers(reachableZones, address);
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

    private static boolean covers(Set<String> zones, String address) {
        boolean ret;
        if (zones == null) {
            ret = true;
        } else {
            String zone = ZoneUtil.zoneOf(address);
            ret = zone == null || ZoneUtil.zoneMatches(zone, zones);
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
