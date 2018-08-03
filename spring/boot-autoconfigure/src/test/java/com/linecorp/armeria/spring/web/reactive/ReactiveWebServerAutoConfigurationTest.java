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

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpMessage;
import com.linecorp.armeria.testing.internal.DummyAddressResolverGroup;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Flux;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        // https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html
        // #boot-features-testing-spring-boot-applications-detecting-web-app-type
        properties = "spring.main.web-application-type=reactive"
)
@ActiveProfiles({ "test_reactive" })
public class ReactiveWebServerAutoConfigurationTest {

    @SpringBootApplication
    @Configuration
    static class TestConfiguration {
        @RestController
        static class TestController {
            @GetMapping("/hello")
            Flux<String> hello() {
                return Flux.fromArray(new String[] { "h", "e", "l", "l", "o" });
            }
        }
    }

    private static final ClientFactory clientFactory =
            new ClientFactoryBuilder()
                    .sslContextCustomizer(b -> b.trustManager(InsecureTrustManagerFactory.INSTANCE))
                    .addressResolverGroupFactory(eventLoopGroup -> new DummyAddressResolverGroup())
                    .build();

    @Rule
    public TestRule globalTimeout = new DisableOnDebug(new Timeout(10, TimeUnit.SECONDS));

    @LocalServerPort
    int port;

    @Test
    public void shouldGetHelloViaH1() throws Exception {
        final HttpClient client = HttpClient.of(clientFactory, "h1://example.com:" + port);
        final AggregatedHttpMessage response = client.get("/hello").aggregate().join();
        assertThat(response.content().toStringUtf8()).isEqualTo("hello");
    }

    @Test
    public void shouldGetHelloViaH2() throws Exception {
        final HttpClient client = HttpClient.of(clientFactory, "h2://example.com:" + port);
        final AggregatedHttpMessage response = client.get("/hello").aggregate().join();
        assertThat(response.content().toStringUtf8()).isEqualTo("hello");
    }
}
