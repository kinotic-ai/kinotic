package org.kinotic.queue.internal.log;

import java.util.List;

/**
 * The copies of a shard, by storage id, a majority of which counts toward a commit. While the copies change, a majority
 * of the old set and a majority of the new set must both hold a record for it to count.
 *
 * @param voting  the set whose majority counts
 * @param joining the set replacing it, whose majority counts as well; empty when no change is under way
 */
public record Membership(List<String> voting, List<String> joining) {

    /**
     * @return whether the set is changing, so both sets count
     */
    public boolean isJoint() {
        return !joining.isEmpty();
    }
}
