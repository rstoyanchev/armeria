/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.spring.web.reactive;

import static java.util.Objects.requireNonNull;

import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.HttpHandler;

import com.linecorp.armeria.common.AggregatedHttpMessage;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServiceRequestContext;

import reactor.core.publisher.Mono;

/**
 * Adapt {@link HttpHandler} to the Armeria server.
 */
public class ArmeriaHttpHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ArmeriaHttpHandlerAdapter.class);

    private final HttpHandler httpHandler;
    private final DataBufferFactory factory;

    ArmeriaHttpHandlerAdapter(HttpHandler httpHandler, DataBufferFactory factory) {
        this.httpHandler = requireNonNull(httpHandler, "httpHandler");
        this.factory = requireNonNull(factory, "factory");
    }

    Mono<Void> handle(ServiceRequestContext ctx, HttpRequest req, HttpResponseWriter res) {
        final ArmeriaServerHttpRequest convertedRequest;
        try {
            convertedRequest = new ArmeriaServerHttpRequest(ctx, req, factory);
        } catch (URISyntaxException e) {
            logger.warn("{} Invalid URL", ctx, e);
            res.close(AggregatedHttpMessage.of(HttpStatus.BAD_REQUEST));
            return Mono.empty();
        }

        final ArmeriaServerHttpResponse convertedResponse = new ArmeriaServerHttpResponse(ctx, res, factory);

        return httpHandler.handle(convertedRequest, convertedResponse)
                          .doOnSuccessOrError((unused, cause) -> {
                              if (cause != null) {
                                  logger.debug("{} Failed to handle a request", ctx, cause);
                                  convertedResponse.setComplete(cause).subscribe();
                              } else {
                                  convertedResponse.setComplete().subscribe();
                              }
                          });
    }
}
