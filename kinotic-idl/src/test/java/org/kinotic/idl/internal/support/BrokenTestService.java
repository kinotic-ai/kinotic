package org.kinotic.idl.internal.support;

/**
 * A service whose return type has no {@code ResolvableTypeConverter}, so its conversion always fails.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
public interface BrokenTestService {

    Thread unconvertibleReturn();

}
