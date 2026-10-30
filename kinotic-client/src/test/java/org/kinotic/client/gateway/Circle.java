package org.kinotic.client.gateway;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Created by Navíd Mitchell 🤪 on 10/26/26.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Accessors(chain = true)
public class Circle implements Shape {

    private double radius;

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

}
