package org.kinotic.management.api.config.github;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 *
 * Created By Navíd Mitchell 🤪on 8/25/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ManagementApiProperties {

    @Valid
    private GithubProperties github = new GithubProperties();

}
