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
package com.linecorp.armeria.spring.web.servlet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

public class ServletWebServerAutoConfigurationTest {

    @SpringBootApplication
    static class ServletTestConfiguration {
        @RestController
        static class ServletTestController {
            @GetMapping("/hello")
            public String hello() {
                return "hello";
            }
        }
    }

    @Test
    public void shouldNotStartServer() {
        // For servlet stack, there is only a factory which creates ArmeriaWebServer because we excluded
        // dependencies such as Tomcat and Jetty. However ArmeriaServletWebServerFactory is not implemented
        // yet, the application should be failed to start up.
        assertThatThrownBy(() -> SpringApplication.run(ServletTestConfiguration.class))
                .hasCauseInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not implemented yet. Are you interested in sending a PR?");
    }
}
