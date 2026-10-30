package org.kinotic.core.api.crud;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A {@link Page} that uses a cursor to track the current position in the result set.
 * Created by Navíd Mitchell 🤪 on 11/5/23.
 */
public class CursorPage<T> extends Page<T> {

    private final String cursor;

    @JsonCreator
    public CursorPage(@JsonProperty("content") List<T> content,
                      @JsonProperty("cursor") String cursor,
                      @JsonProperty("totalElements") Long totalElements) {
        super(content, totalElements);
        this.cursor = cursor;
    }

    /**
     * @return The cursor to be used for subsequent retrieval of data, or null if this is the last page
     */
    public String getCursor(){
        return cursor;
    }

    @Override
    public <R> CursorPage<R> map(Function<? super T, ? extends R> mapper) {
        return new CursorPage<>(getContent().stream().<R>map(mapper).toList(), cursor, getTotalElements());
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
