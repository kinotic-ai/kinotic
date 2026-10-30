package org.kinotic.client.gateway;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A value the client tests send and receive through a proxy.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(chain = true)
public class TestDto {

    private String name;
    private int count;
    private String description;

}
