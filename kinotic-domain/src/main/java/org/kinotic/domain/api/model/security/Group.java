package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * A group of an organization's members: a name and a description for the membership the group has in the
 * authorization engine, where a grant made to the group reaches every member it has at the time of a check.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Group implements OrganizationScoped<String> {
    private String id;
    private String organizationId;
    private String name;
    private String description;
    private Date created;
    private Date updated;
}
