package org.kinotic.authz.api.model;

import java.util.List;

/**
 * Whether a subject holds a permission on a resource, and the grants on the resource and its ancestors it holds
 * the permission through: each one a role bundling the permission, made to the subject or to a group the subject
 * is in.
 *
 * @param allowed true when the subject holds the permission
 * @param through the grants it holds the permission through, in the order of the resource's lineage from the
 *                resource up; empty when it holds the permission through nothing a grant explains, such as a
 *                membership
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record AccessExplanation(boolean allowed, List<Grant> through) {
}
