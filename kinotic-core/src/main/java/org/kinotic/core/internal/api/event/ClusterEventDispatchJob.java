package org.kinotic.core.internal.api.event;

import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.kinotic.core.api.event.ClusterEvent;
import org.kinotic.core.api.event.ClusterEventHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;

/**
 * Carries a {@link ClusterEvent} to a node and hands it there to each {@link ClusterEventHandler} bean of its type, as
 * the compute job a {@link ClusterEventPublisher} broadcasts.
 */
public class ClusterEventDispatchJob implements IgniteRunnable {

    // the event is carried as the bytes Java serialization gives it, so what a node can read is decided by the
    // event's own serializable form rather than by what the cluster's marshaller makes of its type; the limits bound
    // the graph a peer can make a node allocate, far above what an event carries
    private static final ObjectInputFilter EVENT_CLASSES = ObjectInputFilter.Config.createFilter(
            "maxdepth=32;maxarray=10000;maxrefs=10000;org.kinotic.**;java.**;!*");

    @SpringApplicationContextResource
    private transient ApplicationContext applicationContext;

    private final byte[] event;

    public ClusterEventDispatchJob(ClusterEvent event) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(event);
        } catch (IOException e) {
            throw new UncheckedIOException("The event " + event.getClass().getName() + " could not be serialized", e);
        }
        this.event = bytes.toByteArray();
    }

    @Override
    public void run() {
        if (applicationContext == null) {
            throw new IllegalStateException("The Spring context was not injected; Ignite must be started through IgniteSpring");
        }
        ClusterEvent received = read();
        ResolvableType handlerType = ResolvableType.forClassWithGenerics(ClusterEventHandler.class, received.getClass());
        applicationContext.<ClusterEventHandler<ClusterEvent>>getBeanProvider(handlerType)
                          .orderedStream()
                          .forEach(handler -> handler.handle(received));
    }

    private ClusterEvent read() {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(event))) {
            in.setObjectInputFilter(EVENT_CLASSES);
            return (ClusterEvent) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("The cluster event could not be read", e);
        }
    }

}
