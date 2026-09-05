package org.kinotic.idl.internal.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Created by Navíd Mitchell 🤪 on 7/21/26.
 */
@Setter
@Getter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class TestAddress {

    private String street;
    private String city;

}
