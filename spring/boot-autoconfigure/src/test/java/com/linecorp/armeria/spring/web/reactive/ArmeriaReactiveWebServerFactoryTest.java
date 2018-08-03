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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.SocketUtils;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpMessage;
import com.linecorp.armeria.server.HttpStatusException;
import com.linecorp.armeria.testing.internal.DummyAddressResolverGroup;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;

public class ArmeriaReactiveWebServerFactoryTest {

    static ConfigurableListableBeanFactory beanFactory;
    static String[] names;
    static ClientFactory clientFactory;

    @BeforeClass
    public static void beforeClass() {
        beanFactory = mock(ConfigurableListableBeanFactory.class);
        names = new String[0];
        clientFactory = new ClientFactoryBuilder()
                .sslContextCustomizer(b -> b.trustManager(InsecureTrustManagerFactory.INSTANCE))
                .addressResolverGroupFactory(eventLoopGroup -> new DummyAddressResolverGroup())
                .build();
    }

    private ArmeriaReactiveWebServerFactory factory() {
        when(beanFactory.getBeanNamesForType((Class<?>) any())).thenReturn(names);
        return new ArmeriaReactiveWebServerFactory(beanFactory);
    }

    private HttpClient httpsClient(WebServer server) {
        return HttpClient.of(clientFactory, "https://example.com:" + server.getPort());
    }

    private HttpClient httpClient(WebServer server) {
        return HttpClient.of(clientFactory, "http://example.com:" + server.getPort());
    }

    @Test
    public void shouldRunOnSpecifiedPort() {
        final ArmeriaReactiveWebServerFactory factory = factory();
        final int port = SocketUtils.findAvailableTcpPort();
        factory.setPort(port);
        runEchoServer(factory, server -> {
            assertThat(server.getPort()).isEqualTo(port);
        });
    }

    @Test
    public void shouldReturnEchoResponse() {
        final ArmeriaReactiveWebServerFactory factory = factory();
        runEchoServer(factory, server -> {
            final HttpClient client = httpClient(server);
            postAndValidateEchoResponse(client);

            final AggregatedHttpMessage res = client.get("/hello").aggregate().join();
            assertThat(res.status()).isEqualTo(com.linecorp.armeria.common.HttpStatus.OK);
            assertThat(res.content().toStringUtf8()).isEmpty();
        });
    }

    @Test
    public void shouldConfigureTlsWithSelfSignedCertificate() {
        final ArmeriaReactiveWebServerFactory factory = factory();
        final Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        factory.setSsl(ssl);
        runEchoServer(factory, server -> {
            postAndValidateEchoResponse(httpsClient(server));
        });
    }

    @Test
    public void shouldReturnBadRequestDueToException() {
        final ArmeriaReactiveWebServerFactory factory = factory();
        runServer(factory, AlwaysFailureHandler.INSTANCE, server -> {
            final HttpClient client = httpClient(server);

            final AggregatedHttpMessage res1 = client.post("/hello", "hello").aggregate().join();
            assertThat(res1.status()).isEqualTo(com.linecorp.armeria.common.HttpStatus.BAD_REQUEST);

            final AggregatedHttpMessage res = client.get("/hello").aggregate().join();
            assertThat(res.status()).isEqualTo(com.linecorp.armeria.common.HttpStatus.BAD_REQUEST);
        });
    }

    private void postAndValidateEchoResponse(HttpClient client) {
        final String body = "Hello, world!";
        final AggregatedHttpMessage res = client.post("/hello", body)
                                                .aggregate().join();
        assertThat(res.status()).isEqualTo(com.linecorp.armeria.common.HttpStatus.OK);
        assertThat(res.content().toStringUtf8()).isEqualTo(body);
    }

    private void runEchoServer(ReactiveWebServerFactory factory,
                               Consumer<WebServer> validator) {
        runServer(factory, EchoHandler.INSTANCE, validator);
    }

    private void runServer(ReactiveWebServerFactory factory,
                           HttpHandler httpHandler,
                           Consumer<WebServer> validator) {
        final WebServer server = factory.getWebServer(httpHandler);
        server.start();
        try {
            validator.accept(server);
        } finally {
            server.stop();
        }
    }

    static class EchoHandler implements HttpHandler {
        static final EchoHandler INSTANCE = new EchoHandler();

        @Override
        public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
            response.setStatusCode(HttpStatus.OK);
            return response.writeWith(request.getBody());
        }
    }

    static class AlwaysFailureHandler implements HttpHandler {
        static final AlwaysFailureHandler INSTANCE = new AlwaysFailureHandler();

        @Override
        public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
            response.setStatusCode(HttpStatus.OK);
            return request.getBody().map(data -> {
                // skip data, then throw an exception.
                throw HttpStatusException.of(com.linecorp.armeria.common.HttpStatus.BAD_REQUEST);
            }).doOnComplete(() -> {
                // An HTTP GET request doesn't have a body, so onComplete will be immediately called.
                throw HttpStatusException.of(com.linecorp.armeria.common.HttpStatus.BAD_REQUEST);
            }).then();
        }
    }
}
