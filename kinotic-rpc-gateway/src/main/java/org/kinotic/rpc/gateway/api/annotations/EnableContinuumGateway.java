

package org.kinotic.rpc.gateway.api.annotations;

import org.kinotic.rpc.gateway.internal.config.ContinuumGatewayConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be used on a Spring Boot application to enable the Continuum Gateway.
 *
 * NOTE: This is not really meant for production uses except in select cases.
 *       For production use you should run a stand alone Continuum Gateway by deploying the Continuum Gateway Server application
 *
 *
 * Created by Navid Mitchell 🤪 on 2/10/20
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ContinuumGatewayConfiguration.class)
public @interface EnableContinuumGateway {

}
