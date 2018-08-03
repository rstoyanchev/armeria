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

import javax.servlet.ServletRequest;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.spring.ArmeriaSettings;
import com.linecorp.armeria.spring.web.servlet.ArmeriaServletWebServerFactoryAutoConfiguration.ArmeriaServletWebServerFactoryConfiguration;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for servlet web servers.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ServletRequest.class)
@EnableConfigurationProperties({ ServerProperties.class, ArmeriaSettings.class })
@Import({ ServletWebServerFactoryAutoConfiguration.class, ArmeriaServletWebServerFactoryConfiguration.class })
public class ArmeriaServletWebServerFactoryAutoConfiguration {

    @Configuration
    @ConditionalOnClass(Server.class)
    @ConditionalOnMissingBean(value = ServletWebServerFactory.class, search = SearchStrategy.CURRENT)
    public static class ArmeriaServletWebServerFactoryConfiguration {
        /**
         * Returns a new {@link ArmeriaServletWebServerFactory} bean instance, but it is not implemented yet.
         * So {@link UnsupportedOperationException} will be raised.
         */
        @Bean
        public ArmeriaServletWebServerFactory armeriaServletWebServerFactory() {
            throw new UnsupportedOperationException(
                    "Not implemented yet. Are you interested in sending a PR?");
        }
    }
}
