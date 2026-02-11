package org.kinotic.structures.internal.sample;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 5/11/23.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode
public class Person {

    private String id;
    private String firstName;
    private String lastName;
    private List<Address> addresses;

}
