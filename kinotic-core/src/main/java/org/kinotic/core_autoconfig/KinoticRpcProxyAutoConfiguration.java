package org.kinotic.core_autoconfig;

import org.kinotic.core.api.RpcServiceProxyFactory;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.TraceLogFilter;
import org.kinotic.core.internal.config.KinoticJacksonConfig;
import org.kinotic.core.internal.config.KinoticOpenTelemetryConfig;
import org.kinotic.core.internal.api.service.DefaultExceptionConverter;
import org.kinotic.core.internal.api.service.json.JacksonExceptionConverter;
import org.kinotic.core.internal.api.service.rpc.DefaultResponseConverter;
import org.kinotic.core.internal.api.service.rpc.DefaultRpcArgumentConverterResolver;
import org.kinotic.core.internal.api.service.rpc.DefaultRpcReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.converters.BinaryRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.converters.JacksonRpcArgumentConverter;
import org.kinotic.core.internal.api.service.rpc.converters.JacksonRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.converters.TextRpcResponseConverter;
import org.kinotic.core.internal.api.service.rpc.types.CompletableFutureValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.FluxReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.MonoRpcReturnValueHandlerFactory;
import org.kinotic.core.internal.api.service.rpc.types.VertxFutureRpcReturnValueHandlerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configures the beans a service proxy is built from: the converters of its arguments, replies and exceptions,
 * the handlers of its return types, the Jackson mapper they convert with, the tracing of its invocations and the
 * {@link RpcServiceProxyFactory} that mints proxies over any transport. The composites collect every converter and
 * handler bean of the context, so an application adds its own as beans. A cluster member builds on it; an
 * application that only calls services through a gateway runs it alone, with the cluster's auto-configuration
 * excluded. It is defined in a separate package because it must not be scanned by the Spring context.
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
@AutoConfiguration
@EnableConfigurationProperties
@Import({KinoticProperties.class,
         KinoticJacksonConfig.class,
         KinoticOpenTelemetryConfig.class,
         TraceLogFilter.class,
         JacksonRpcArgumentConverter.class,
         DefaultRpcArgumentConverterResolver.class,
         JacksonRpcResponseConverter.class,
         TextRpcResponseConverter.class,
         BinaryRpcResponseConverter.class,
         DefaultResponseConverter.class,
         CompletableFutureValueHandlerFactory.class,
         VertxFutureRpcReturnValueHandlerFactory.class,
         MonoRpcReturnValueHandlerFactory.class,
         FluxReturnValueHandlerFactory.class,
         DefaultRpcReturnValueHandlerFactory.class,
         JacksonExceptionConverter.class,
         DefaultExceptionConverter.class,
         RpcServiceProxyFactory.class})
public class KinoticRpcProxyAutoConfiguration {
}
