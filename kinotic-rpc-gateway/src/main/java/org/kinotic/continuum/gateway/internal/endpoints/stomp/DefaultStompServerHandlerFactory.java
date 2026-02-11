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

package org.kinotic.continuum.gateway.internal.endpoints.stomp;

import io.vertx.core.Vertx;
import io.vertx.ext.stomp.lite.StompServerConnection;
import io.vertx.ext.stomp.lite.StompServerHandler;
import io.vertx.ext.stomp.lite.StompServerHandlerFactory;
import org.kinotic.continuum.gateway.internal.endpoints.Services;
import org.springframework.stereotype.Component;

/**
 *
 * Created by Navid Mitchell on 2019-02-04.
 */
@Component
public class DefaultStompServerHandlerFactory implements StompServerHandlerFactory {

    private final Vertx vertx;
    private final Services services;

    public DefaultStompServerHandlerFactory(Vertx vertx, Services services) {
        this.vertx = vertx;
        this.services = services;
    }

    @Override
    public StompServerHandler create(StompServerConnection stompServerConnection) {
        return new DefaultStompServerHandler(vertx,
                                             services,
                                             stompServerConnection);
    }

}
