package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class IamUser implements Identifiable<String> {

    private String id;

    private String email;

    private String displayName;

    private String authType;

    private String oidcSubject;

    private String oidcConfigId;

    private String authScopeType;

    private String authScopeId;

    private boolean enabled;

    private Date created;

    private Date updated;

}
