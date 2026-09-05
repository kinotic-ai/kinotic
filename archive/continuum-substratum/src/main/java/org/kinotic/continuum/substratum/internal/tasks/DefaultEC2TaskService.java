/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.substratum.internal.tasks;

import org.kinotic.continuum.grind.api.ContextUtils;
import org.kinotic.continuum.grind.api.Task;
import org.kinotic.continuum.grind.api.Tasks;
import org.kinotic.continuum.substratum.internal.util.Names;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.CreateKeyPairRequest;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.CreateTagsResponse;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 *
 * Created by Navid Mitchell on 3/24/20
 */
@Component
public class DefaultEC2TaskService {

    private final Ec2AsyncClient ec2AsyncClient;

    final ExpressionParser parser = new SpelExpressionParser();

    public DefaultEC2TaskService(Ec2AsyncClient ec2AsyncClient) {
        this.ec2AsyncClient = ec2AsyncClient;
    }

    public Task<CompletableFuture<String>> createKeyPair(String domainName,
                                                         String pathToSaveKey){

        String keyPairName = Names.keyPairName(domainName);
        File dir = new File(pathToSaveKey);
        if(!dir.isDirectory()){
            throw new IllegalArgumentException("pathToSaveKey must be a directory");
        }
        File penFile = Path.of(dir.getAbsolutePath(), keyPairName + ".pem").toFile();
        if(penFile.exists()){
            throw new IllegalArgumentException("File already exists for path " +  penFile.getAbsolutePath());
        }

        return Tasks.fromSupplier("Create key pair " + keyPairName,
                                  () -> {
                                               CreateKeyPairRequest request =
                                                       CreateKeyPairRequest.builder()
                                                                           .keyName(keyPairName)
                                                       .build();
                                               return ec2AsyncClient.createKeyPair(request)
                                                       .thenApply(createKeyPairResponse -> {
                                                           try {
                                                               BufferedWriter writer = new BufferedWriter(new FileWriter(penFile));
                                                               writer.write(createKeyPairResponse.keyMaterial());

                                                               writer.close();
                                                           } catch (IOException e) {
                                                               throw new IllegalStateException(e);
                                                           }
                                                           return penFile.getAbsolutePath();
                                                       });
                                           });
    }

    /**
     * Will tag an AWS resource with the given tags.
     * The resourceId can be a SPEL expression if it is surrounded with ${}
     * In this case the value will be evaluated against the current execution context
     * The value returned by the SPEL must be a String
     *
     * To access a beans value an expression like this can be used @someOtherBean.getData()
     *
     * @param resourceId the id of the AWS resource to tag or a SPEL expression to retrieve the resource id
     * @param tags to add to the given resource
     * @return {@link Task} with no return value
     */
    public Task<CompletableFuture<CreateTagsResponse>> tagAwsResource(String resourceId, Map<String, String> tags){
        return Tasks.fromCallable("Tagging Resource "+resourceId, new TagAwsResourceCallable(resourceId, tags));
    }

    private class TagAwsResourceCallable implements Callable<CompletableFuture<CreateTagsResponse>>, ApplicationContextAware {

        private final String resourceId;
        private final Map<String, String> tags;
        private GenericApplicationContext applicationContext;

        public TagAwsResourceCallable(String resourceId, Map<String, String> tags) {
            this.resourceId = resourceId;
            this.tags = tags;
        }

        @Override
        public CompletableFuture<CreateTagsResponse> call() {
            String id = resourceId;
            // check if this is a SPEL expression
            if(resourceId.startsWith("${")){
                String expressionString = resourceId.substring(2, resourceId.length() - 1);
                StandardEvaluationContext context = new StandardEvaluationContext();
                context.setBeanResolver(new BeanFactoryResolver(this.applicationContext.getBeanFactory()));
                MapPropertySource propertySource = ContextUtils.getGrindPropertySource(this.applicationContext);
                for(String name: propertySource.getPropertyNames()){
                    context.setVariable(name, propertySource.getProperty(name));
                }
                Expression expression = parser.parseExpression(expressionString);
                id = expression.getValue(context, String.class);
            }

            List<Tag> tagObjects = new ArrayList<>(tags.size());
            for(Map.Entry<String, String> entry: tags.entrySet()){
                tagObjects.add(Tag.builder()
                                  .key(entry.getKey())
                                  .value(entry.getValue())
                                  .build());
            }

            RetryPolicy<CreateTagsResponse> retryPolicy = new RetryPolicy<CreateTagsResponse>()
                    .handleIf(throwable -> throwable.getMessage().contains("does not exist"))
                    .withDelay(Duration.ofSeconds(30))
                    .withMaxAttempts(10);

            CreateTagsRequest createTagsRequest = CreateTagsRequest.builder()
                                                                   .tags(tagObjects)
                                                                   .resources(id)
                                                                   .build();

            return Failsafe.with(retryPolicy)
                           .getStageAsync(() -> ec2AsyncClient.createTags(createTagsRequest));

        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = (GenericApplicationContext) applicationContext;
        }
    }


}
