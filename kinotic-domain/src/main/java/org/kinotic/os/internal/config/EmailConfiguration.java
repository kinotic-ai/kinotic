package org.kinotic.os.internal.config;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import lombok.RequiredArgsConstructor;
import org.kinotic.os.api.config.KinoticDomainProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Wires the Azure Communication Services {@link EmailClient} and a Thymeleaf
 * template engine dedicated to rendering the plain-text email variant.
 * <p>
 * Authentication uses {@code DefaultAzureCredential} — in production on AKS this
 * resolves to a Workload-Identity federated token; on a developer laptop it falls
 * through to {@code AzureCliCredential} (via {@code az login}). No connection
 * strings or secrets are read from the Spring environment.
 */
@Configuration
@RequiredArgsConstructor
public class EmailConfiguration {

    private final KinoticDomainProperties properties;

    /**
     * Builds the ACS {@link EmailClient}. Only created when
     * {@code kinotic.email.enabled=true} (the default).
     */
    @Bean
    @ConditionalOnProperty(prefix = "kinotic.email", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public EmailClient emailClient() {
        DefaultAzureCredentialBuilder credBuilder = new DefaultAzureCredentialBuilder();
        if (StringUtils.hasText(properties.getEmail().getManagedIdentityClientId())) {
            credBuilder.managedIdentityClientId(properties.getEmail().getManagedIdentityClientId());
        }
        return new EmailClientBuilder()
                .endpoint(properties.getEmail().getEndpoint())
                .credential(credBuilder.build())
                .buildClient();
    }

    /**
     * Dedicated Thymeleaf engine configured in {@link TemplateMode#TEXT} mode for
     * rendering {@code verification-email.txt}. The default auto-configured
     * {@code SpringTemplateEngine} is reused unchanged for the HTML variant.
     */
    @Bean(name = "emailTextTemplateEngine")
    public SpringTemplateEngine emailTextTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

}
