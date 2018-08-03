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

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ReactiveHttpInputMessage;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.spring.ArmeriaSettings;
import com.linecorp.armeria.spring.web.reactive.ArmeriaReactiveWebServerFactoryAutoConfiguration.ArmeriaReactiveWebServerFactoryConfiguration;
import com.linecorp.armeria.spring.web.servlet.ArmeriaServletWebServerFactory;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for reactive web servers.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(ReactiveHttpInputMessage.class)
@EnableConfigurationProperties({ ServerProperties.class, ArmeriaSettings.class })
@Import({ ReactiveWebServerFactoryAutoConfiguration.class, ArmeriaReactiveWebServerFactoryConfiguration.class })
public class ArmeriaReactiveWebServerFactoryAutoConfiguration {

    @Configuration
    @ConditionalOnClass(Server.class)
    @ConditionalOnMissingBean(value = ReactiveWebServerFactory.class, search = SearchStrategy.CURRENT)
    public static class ArmeriaReactiveWebServerFactoryConfiguration {
        /**
         * Returns a new {@link ArmeriaServletWebServerFactory} bean instance.
         */
        @Bean
        public ArmeriaReactiveWebServerFactory armeriaReactiveWebServerFactory(
                ConfigurableListableBeanFactory beanFactory) {
            return new ArmeriaReactiveWebServerFactory(beanFactory);
        }
    }
}
