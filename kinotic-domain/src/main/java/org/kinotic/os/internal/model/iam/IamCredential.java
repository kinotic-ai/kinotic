package org.kinotic.os.internal.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class IamCredential implements Identifiable<String> {

    private String id;

    private String passwordHash;

}
