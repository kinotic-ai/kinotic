package org.kinotic.authz.api.services;

import io.vertx.core.Future;

import java.util.function.Supplier;

/**
 * The locks that keep changes to one object, made of several writes to its record and to the stores, from
 * interleaving across the cluster.
 */
public interface ObjectLockService {

    /**
     * Runs changes to an object's record and its place in the stores while no other caller of this function is
     * changing the same object anywhere in the cluster, so a creation and a deletion of one id, each made of
     * several writes, never interleave. Objects of one type may wait on each other.
     *
     * @param object  the object, in {@code type:id} form
     * @param changes the changes to run, which may call this function for an object of another type and never for
     *                one of the same type, since the two may wait on each other
     * @return what the changes complete with; fails when the object is still being changed by another caller
     *         after thirty seconds
     */
    <T> Future<T> exclusively(String object, Supplier<Future<T>> changes);

    /**
     * Creates the record an object stands for, with its place in the stores, while no other caller changes the
     * object, as {@link #exclusively} runs it. A record stored already refuses the create. What a deleted record
     * of the object left in the stores is removed before the new record is written, so the new record is never
     * reached through a grant made on the old one.
     *
     * @param object the object the record is known by in the stores, in {@code type:id} form
     * @param stored reads the record stored for the object, emitting null when there is none
     * @param clear  removes what a deleted record of the object left behind
     * @param create writes the record and places it in the stores; what becomes of a record that cannot be placed
     *               is its to decide
     * @return a future emitting what the create emits, or failing with an
     *         {@link org.kinotic.core.api.exceptions.AlreadyExistsException} when a record is stored for the object
     */
    <T> Future<T> createExclusively(String object, Supplier<Future<T>> stored, Supplier<Future<Void>> clear, Supplier<Future<T>> create);
}
