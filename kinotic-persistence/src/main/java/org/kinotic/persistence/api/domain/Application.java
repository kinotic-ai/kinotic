package org.kinotic.persistence.api.domain;

import lombok.*;
import lombok.experimental.Accessors;
import org.kinotic.rpc.api.crud.Identifiable;


import java.util.Date;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Application implements Identifiable<String> {

    private String id;

    private String description;

    private Date updated = null;

    private boolean enableGraphQL = true;

    private boolean enableOpenAPI = true;

    public Application(String id, String description) {
        this.id = id;
        this.description = description;
    }
}
