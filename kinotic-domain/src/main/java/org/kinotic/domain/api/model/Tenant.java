package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * One tenant of an application: the slice of the application's {@code MultiTenancyType.SHARED} rows a group of
 * its users share, which {@code ParticipantIdentity.tenantId} points into, and the object the application's
 * store grants on. A tenant is created by a customer signing up, or by the application for each user when it
 * isolates them.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Tenant implements ApplicationScoped<String> {
    /**
     * The record's id, {@code <organizationId>.<applicationId>.<tenantId>} as {@code DomainUtil.createTenantId}
     * makes it, so a tenant id is unique within its application.
     */
    private String id;
    private String organizationId;
    private String applicationId;
    /**
     * The tenant's id within its application: what {@code ParticipantIdentity.tenantId} holds for its users, the
     * routing key of their shared rows, and the id of the tenant object in the application's store. The slug of
     * the tenant's name for a tenant a customer signed up, a generated id for one made per user.
     */
    private String tenantId;
    /**
     * The name the tenant's users know it by, such as the customer's company name.
     */
    private String name;
    /**
     * The user the tenant was created for: the customer who signed up, or the user it isolates.
     */
    private String createdBy;
    private Date created;
    private Date updated;
}
