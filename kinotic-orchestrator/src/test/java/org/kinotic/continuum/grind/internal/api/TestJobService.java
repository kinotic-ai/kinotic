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

package org.kinotic.continuum.grind.internal.api;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kinotic.continuum.grind.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public class TestJobService {

    @Autowired
    private JobService jobService;

    @Test
    public void testSimple(){


        JobDefinition definition = sequenceOfCallableJob(5, true);

        Flux<Result<?>> jobResult = jobService.assemble(definition);

        StepVerifier.create(jobResult)
                    .expectNextMatches(resultPredicate(1))
                    .expectNextMatches(resultPredicate(2))
                    .expectNextMatches(resultPredicate(3))
                    .expectNextMatches(resultPredicate(4))
                    .expectNextMatches(resultPredicate(5))
                    .verifyComplete();
    }

    @Test
    public void testAutowired(){
        JobDefinition definition = JobDefinition.create("testAutowired")
                                                .taskStoreResult(Tasks.fromCallable(() -> new CrazyGrind("Hello Sucka")))
                                                .taskStoreResult(Tasks.fromCallable(new Callable<String>() {

                                                    @Autowired
                                                    private CrazyGrind crazyGrind;

                                                    @Override
                                                    public String call() {
                                                        return crazyGrind.getSlogan();
                                                    }
                                                }), "crazySlogan")
                                                .task(Tasks.fromCallable(new Callable<String>() {

                                                    @Value("${crazySlogan}")
                                                    private String propertyTest;

                                                    @Override
                                                    public String call() {
                                                        return propertyTest;
                                                    }
                                                }));

        Flux<Result<?>> jobResult = jobService.assemble(definition);

        StepVerifier.create(jobResult)
                    .expectNextMatches(resultPredicate(new CrazyGrind("Hello Sucka")))
                    .expectNextMatches(resultPredicate("Hello Sucka"))
                    .expectNextMatches(resultPredicate("Hello Sucka"))
                    .verifyComplete();

    }


    // FIXME: Test is failing
    public void testLifecycleMethods(){
        AtomicBoolean postConstruct = new AtomicBoolean(false);
        AtomicBoolean preDestroy = new AtomicBoolean(false);

        JobDefinition definition = JobDefinition.create("testLifecycleMethods")
                                                .taskStoreResult(Tasks.fromRunnable(new Runnable() {
                                                    @PostConstruct
                                                    public void init(){
                                                        postConstruct.set(true);
                                                    }

                                                    @PreDestroy
                                                    public void destroy(){
                                                        preDestroy.set(true);
                                                    }

                                                    @Override
                                                    public void run() {
                                                    }
                                                }));

        Flux<Result<?>> jobResult = jobService.assemble(definition);

        StepVerifier.create(jobResult)
                    .expectNextMatches(resultPredicate(null))
                    .verifyComplete();

        Assertions.assertTrue(postConstruct.get(),"PostConstruct was not executed");
        Assertions.assertTrue(preDestroy.get(), "PreDestroy was not executed");
    }

    @Test
    public void testException(){
        JobDefinition definition = JobDefinition.create("testException")
                                                .taskStoreResult(Tasks.fromCallable(() -> new CrazyGrind("Hello Sucka")))
                                                .task(Tasks.fromRunnable(() -> {
                                                    throw new IllegalStateException("Exception");
                                                }));

        Flux<Result<?>> jobResult = jobService.assemble(definition);

        StepVerifier.create(jobResult)
                    .expectNextMatches(resultPredicate(new CrazyGrind("Hello Sucka")))
                    .expectErrorMessage("Exception")
                    .verify();
    }

    /**
     * Test for a {@link Task} that dynamically builds a {@link JobDefinition} and executes it
     */
    // FIXME: Test is failing
    public void testSubJob(){

        Random rand = new Random();
        int randomNum = rand.nextInt(10 + 1);

        JobDefinition definition
                = JobDefinition.create("testSubJob")
                     .taskStoreResult(Tasks.fromCallable(() -> randomNum), "callableCount")
                     .task(Tasks.fromCallable("Create Job Definition",new Callable<>() {

                         @Value("${callableCount}")
                         int callableCount;

                         @Override
                         public JobDefinition call() {
                             return sequenceOfCallableJob(callableCount, false);
                         }
                     }));

        StepVerifier.create(jobService.assemble(definition))
                    .expectNextMatches(resultPredicate(randomNum))
                    .expectNextCount(randomNum)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testAutowireCollection(){
        JobDefinition definition
                = JobDefinition.create("testAutowireCollection")
                    .taskStoreResult(Tasks.fromCallable("Creating Grinds", () -> {
                        List<CrazyGrind> ret = new ArrayList<>();
                        for(int i = 0; i < 10; i++){
                            ret.add(new CrazyGrind("Hello Sucka "+i));
                        }
                        return ret;
                    }))
                    .task(Tasks.fromCallable("Autowire Grinds verify Count", new Callable<Integer>() {

                        @Autowired
                        private List<CrazyGrind> grinds;

                        @Override
                        public Integer call() {
                            return grinds.size();
                        }
                    }));

        StepVerifier.create(jobService.assemble(definition))
                    .expectNextCount(1)
                    .expectNextMatches(resultPredicate(10))
                    .expectComplete()
                    .verify();

    }

    // FIXME: Test is failing
    public void testTaskSupplier(){
        JobDefinition definition = JobDefinition.create("testTaskSupplier")
                                                .taskStoreResult(Tasks.fromCallable("Create Crazy Grind",() -> new CrazyGrind("Hello Sucka")))
                                                .task(Tasks.fromSupplier("Inject Crazy Grind",new Supplier<Task<String>>() {
                                                    @Autowired
                                                    private CrazyGrind crazyGrind;

                                                    @Override
                                                    public Task<String> get() {
                                                        return Tasks.fromValue("Crazy Grind Value", crazyGrind.getSlogan());
                                                    }
                                                }));

        StepVerifier.create(jobService.assemble(definition))
                    .expectNextCount(1)
                    .expectNextMatches(resultPredicate("Hello Sucka"))
                    .expectComplete()
                    .verify();
    }

    private JobDefinition sequenceOfCallableJob(int numberOfCallable, boolean parallel){
        Random rand = new Random();
        JobDefinition ret = JobDefinition.create("sequenceOfCallableJob", parallel);
        for(int i = 1; i <= numberOfCallable; i++){
            final int num = i;
            ret.task(Tasks.fromCallable("Callable Task "+num, () -> {
                if(parallel){
                    Thread.sleep(rand.nextInt(1000 + 1));
                }
                return num;
            }));
        }
        return ret;
    }


    /**
     * Compare the {@link Result#getValue()} to the expected type and value
     * @param expectedValue the value that is expected to be equal to {@link Result#getValue()}
     * @return the predicate that can do the comparison
     */
    private Predicate<Result<?>> resultPredicate(Object expectedValue){
        return taskResult -> {
            boolean ret = false;
            Object resultValue = taskResult.getValue();
            if(expectedValue != null){
                if(expectedValue.getClass().isInstance(resultValue)){
                    ret = resultValue.equals(expectedValue);
                }
            }else if(resultValue == null){
                ret = true;
            }
            return ret;
        };
    }

}
