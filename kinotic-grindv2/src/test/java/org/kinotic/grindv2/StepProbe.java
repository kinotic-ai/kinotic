package org.kinotic.grindv2;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An application bean steps classes record into, so tests observe construction counts and
 * step order without statics.
 */
public class StepProbe {

    public final List<String> recorded = Collections.synchronizedList(new ArrayList<>());
    public final AtomicInteger instantiations = new AtomicInteger();
    public final AtomicBoolean failNext = new AtomicBoolean();

    public void record(String entry) {
        recorded.add(entry);
    }

}
