package org.kinotic.idl.internal.support;

/**
 * Created by Navíd Mitchell 🤪 on 7/21/26.
 */
public interface OtherTestService {

    TestObject findPerson(String name);

    TestAddress findAddress(TestObject person);

}
