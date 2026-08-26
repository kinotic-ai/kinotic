package org.kinotic.management.api.config.github;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 *
 * Created By Navíd Mitchell 🤪on 8/25/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class ManagementApiProperties {

    @Valid
    private GithubProperties github = new GithubProperties();

}
