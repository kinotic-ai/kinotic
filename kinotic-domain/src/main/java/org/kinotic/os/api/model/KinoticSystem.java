package org.kinotic.os.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class KinoticSystem implements Identifiable<String> {

    private String id;

    private List<String> oidcConfigurationIds;

    private Date updated;

}
