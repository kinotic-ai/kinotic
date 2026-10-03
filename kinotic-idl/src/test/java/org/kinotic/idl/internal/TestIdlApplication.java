

package org.kinotic.idl.internal;

import org.kinotic.idl.api.directory.GenericTypeConverter;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.internal.directory.DefaultSchemaFactory;
import org.kinotic.idl.internal.support.authz.TestCallerContext;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/**
 *
 * Created by navid on 10/17/19
 */
@SpringBootApplication
@EnableConfigurationProperties
public class TestIdlApplication {

    @Bean
    public SchemaFactory schemaFactory(GenericTypeConverter typeConverter) {
        return new DefaultSchemaFactory(typeConverter, Set.of(TestCallerContext.class));
    }

}
