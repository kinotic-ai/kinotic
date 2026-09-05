package org.kinotic.idl.internal.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * A generic container mirroring the shape of the platform's paging types, so conversion tests can cover
 * monomorphization without a dependency on kinotic-core.
 */
@Setter
@Getter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class TestPage<T> {

    private List<T> content;
    private Long totalElements;

}
