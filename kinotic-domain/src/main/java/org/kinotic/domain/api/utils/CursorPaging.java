package org.kinotic.domain.api.utils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reads a cursor-paged listing to its end, one {@link CursorPage} at a time.
 */
public final class CursorPaging {

    private CursorPaging() {
    }

    /**
     * Reads every page of a cursor-paged listing.
     *
     * @param listing  reads the page the {@link CursorPageable} it is given asks for
     * @param pageSize how many items each page holds
     * @param sort     the order to page in; it must order the items uniquely, or a page boundary
     *                 between two items that tie skips the second
     * @return a future emitting every item of the listing, in the order of the sort
     */
    public static <T> Future<List<T>> readAll(Function<CursorPageable, Future<Page<T>>> listing, int pageSize, Sort sort) {
        List<T> ret = new ArrayList<>();
        return forEachPage(listing, pageSize, sort, items -> {
            ret.addAll(items);
            return Future.succeededFuture();
        }).map(ret);
    }

    /**
     * Hands each page of a cursor-paged listing to an action, reading the next page once the
     * action's future completes.
     *
     * @param listing  reads the page the {@link CursorPageable} it is given asks for
     * @param pageSize how many items each page holds
     * @param sort     the order to page in; it must order the items uniquely, or a page boundary
     *                 between two items that tie skips the second
     * @param action   handles the items of one page
     * @return a future completing once the action has handled the last page, or failing with the
     *         first failure of the listing or the action
     */
    public static <T> Future<Void> forEachPage(Function<CursorPageable, Future<Page<T>>> listing,
                                               int pageSize,
                                               Sort sort,
                                               Function<List<T>, Future<Void>> action) {
        Promise<Void> ret = Promise.promise();
        readPage(listing, pageSize, sort, action, null, ret);
        return ret.future();
    }

    // Each page is read from the callback of the one before rather than composed into its future, so
    // a listing of any length nests no futures and its completion unwinds no stack of them. The
    // listing runs inside compose, so one that throws fails done rather than throwing into onSuccess
    private static <T> void readPage(Function<CursorPageable, Future<Page<T>>> listing,
                                     int pageSize,
                                     Sort sort,
                                     Function<List<T>, Future<Void>> action,
                                     String cursor,
                                     Promise<Void> done) {
        Future.<Void>succeededFuture()
              .compose(v -> listing.apply(Pageable.create(cursor, pageSize, sort)))
              .compose(page -> action.apply(page.getContent()).map(page))
              .onSuccess(page -> {
                  String next = page instanceof CursorPage<T> cursorPage ? cursorPage.getCursor() : null;
                  if (page.getContent().size() < pageSize || next == null) {
                      done.complete();
                  } else {
                      readPage(listing, pageSize, sort, action, next, done);
                  }
              })
              .onFailure(done::fail);
    }
}
