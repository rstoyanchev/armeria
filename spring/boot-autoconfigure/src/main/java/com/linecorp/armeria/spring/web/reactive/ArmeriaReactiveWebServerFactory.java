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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.linecorp.armeria.spring.ArmeriaConfigurationUtil.configureAnnotatedHttpServices;
import static com.linecorp.armeria.spring.ArmeriaConfigurationUtil.configureHttpServices;
import static com.linecorp.armeria.spring.ArmeriaConfigurationUtil.configurePorts;
import static com.linecorp.armeria.spring.ArmeriaConfigurationUtil.configureServerWithArmeriaSettings;
import static com.linecorp.armeria.spring.ArmeriaConfigurationUtil.configureThriftServices;
import static com.linecorp.armeria.spring.MeterIdPrefixFunctionFactory.DEFAULT;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.web.reactive.server.AbstractReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.Http2;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.Ssl.ClientAuth;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.server.WebServer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.ResourceUtils;

import com.google.common.collect.ImmutableList;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.PathMapping;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.healthcheck.HealthChecker;
import com.linecorp.armeria.spring.AnnotatedServiceRegistrationBean;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import com.linecorp.armeria.spring.ArmeriaSettings;
import com.linecorp.armeria.spring.HttpServiceRegistrationBean;
import com.linecorp.armeria.spring.MeterIdPrefixFunctionFactory;
import com.linecorp.armeria.spring.ThriftServiceRegistrationBean;
import com.linecorp.armeria.spring.web.ArmeriaWebServer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * {@link ReactiveWebServerFactory} which is used to create a new {@link ArmeriaWebServer}.
 */
public class ArmeriaReactiveWebServerFactory extends AbstractReactiveWebServerFactory {
    private static final Logger logger = LoggerFactory.getLogger(ArmeriaReactiveWebServerFactory.class);

    // TODO(hyangtack) Change factory and DataBuffer to Armeria-specific ones later if necessary.
    private static final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    private final ConfigurableListableBeanFactory beanFactory;

    /**
     * Creates a new factory instance with the specified {@link ConfigurableListableBeanFactory}.
     */
    public ArmeriaReactiveWebServerFactory(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = requireNonNull(beanFactory, "beanFactory");
    }

    @Override
    public WebServer getWebServer(HttpHandler httpHandler) {
        final ServerBuilder sb = new ServerBuilder();
        final ArmeriaHttpHandlerAdapter handler = new ArmeriaHttpHandlerAdapter(httpHandler, dataBufferFactory);

        final SessionProtocol protocol;
        final Ssl ssl = getSsl();
        if (ssl != null && ssl.isEnabled()) {
            configureTls(sb, ssl, getSslStoreProvider());
            protocol = SessionProtocol.HTTPS;
        } else {
            protocol = SessionProtocol.HTTP;
        }

        final Http2 http2 = getHttp2();
        if (http2 != null && !http2.isEnabled()) {
            logger.warn(
                    "Cannot disable HTTP/2 protocol for Armeria server. It will be enabled automatically.");
        }

        final InetAddress address = getAddress();
        final int port = ensureValidPort(getPort());
        if (address != null) {
            sb.port(new InetSocketAddress(address, port), protocol);
        } else {
            sb.port(port, protocol);
        }

        final Compression compression = getCompression();
        if (compression != null && compression.getEnabled()) {
            // TODO(hyangtack) Compression. Need to create CompressionDecorator.
        }

        findBean(ArmeriaSettings.class).ifPresent(settings -> configureArmeriaService(sb, settings));

        sb.service(PathMapping.ofCatchAll(), (ctx, req) -> {
            final HttpResponseWriter response = HttpResponse.streaming();
            handler.handle(ctx, req, response).subscribe();
            return response;
        });

        final Server server = sb.build();
        return new ArmeriaWebServer(server, protocol, address, port);
    }

    private void configureArmeriaService(ServerBuilder sb, ArmeriaSettings settings) {
        final MeterIdPrefixFunctionFactory meterIdPrefixFunctionFactory =
                settings.isEnableMetrics() ? findBean(MeterIdPrefixFunctionFactory.class).orElse(DEFAULT)
                                           : null;

        configurePorts(sb, settings.getPorts());
        configureThriftServices(sb,
                                findBeans(ThriftServiceRegistrationBean.class),
                                meterIdPrefixFunctionFactory,
                                settings.getDocsPath());
        configureHttpServices(sb,
                              findBeans(HttpServiceRegistrationBean.class),
                              meterIdPrefixFunctionFactory);
        configureAnnotatedHttpServices(sb,
                                       findBeans(AnnotatedServiceRegistrationBean.class),
                                       meterIdPrefixFunctionFactory);
        configureServerWithArmeriaSettings(sb, settings,
                                           findBean(MeterRegistry.class).orElse(Metrics.globalRegistry),
                                           findBeans(HealthChecker.class));

        findBeans(ArmeriaServerConfigurator.class).forEach(configurator -> configurator.configure(sb));
    }

    private <T> Optional<T> findBean(Class<T> clazz) {
        final String[] names = beanFactory.getBeanNamesForType(clazz);
        if (names.length == 0) {
            return Optional.empty();
        }
        if (names.length == 1) {
            return Optional.of(beanFactory.getBean(names[0], clazz));
        }
        throw new IllegalStateException("Too many " + clazz.getSimpleName() + " beans: " +
                                        String.join(", ", names) + " (expected: 1)");
    }

    private <T> List<T> findBeans(Class<T> clazz) {
        final String[] names = beanFactory.getBeanNamesForType(clazz);
        if (names.length == 0) {
            return ImmutableList.of();
        }
        return Arrays.stream(names)
                     .map(name -> beanFactory.getBean(name, clazz))
                     .collect(toImmutableList());
    }

    private static int ensureValidPort(int port) {
        // 0 means that a user wants his or her server to be bound on an arbitrary port.
        checkArgument(port >= 0 && port <= 65535,
                      "port: %s (expected: 0[arbitrary port] or 1-65535)", port);
        return port;
    }

    private static void configureTls(ServerBuilder sb,
                                     Ssl ssl, @Nullable SslStoreProvider sslStoreProvider) {
        try {
            if (sslStoreProvider == null &&
                ssl.getKeyStore() == null && ssl.getTrustStore() == null) {
                logger.warn("Configuring TLS with a self-signed certificate " +
                            "because no key or trust store was specified");
                sb.tlsSelfSigned();
                return;
            }

            final SslContextBuilder sslBuilder = SslContextBuilder
                    .forServer(getKeyManagerFactory(ssl, sslStoreProvider))
                    .trustManager(getTrustManagerFactory(ssl, sslStoreProvider));

            final String[] enabledProtocols = ssl.getEnabledProtocols();
            if (enabledProtocols != null) {
                sslBuilder.protocols(Arrays.copyOf(enabledProtocols, enabledProtocols.length));
            }

            final String[] ciphers = ssl.getCiphers();
            if (ciphers != null) {
                sslBuilder.ciphers(ImmutableList.copyOf(ciphers));
            }

            final ClientAuth clientAuth = ssl.getClientAuth();
            if (clientAuth != null) {
                switch (clientAuth) {
                    case NEED:
                        sslBuilder.clientAuth(io.netty.handler.ssl.ClientAuth.REQUIRE);
                        break;
                    case WANT:
                        sslBuilder.clientAuth(io.netty.handler.ssl.ClientAuth.OPTIONAL);
                        break;
                }
            }

            sb.tls(sslBuilder.build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyManagerFactory getKeyManagerFactory(
            Ssl ssl, @Nullable SslStoreProvider sslStoreProvider) throws Exception {
        final KeyStore store;
        if (sslStoreProvider != null) {
            store = sslStoreProvider.getKeyStore();
        } else {
            store = loadKeyStore(ssl.getKeyStoreType(), ssl.getKeyStore(), ssl.getKeyStorePassword());
        }

        final KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

        String keyPassword = ssl.getKeyPassword();
        if (keyPassword == null) {
            keyPassword = ssl.getKeyStorePassword();
        }

        keyManagerFactory.init(store, keyPassword != null ? keyPassword.toCharArray()
                                                          : null);
        return keyManagerFactory;
    }

    private static TrustManagerFactory getTrustManagerFactory(
            Ssl ssl, @Nullable SslStoreProvider sslStoreProvider) throws Exception {
        final KeyStore store;
        if (sslStoreProvider != null) {
            store = sslStoreProvider.getTrustStore();
        } else {
            store = loadKeyStore(ssl.getTrustStoreType(), ssl.getTrustStore(), ssl.getTrustStorePassword());
        }

        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(store);
        return trustManagerFactory;
    }

    @Nullable
    private static KeyStore loadKeyStore(
            @Nullable String type,
            @Nullable String resource,
            @Nullable String password) throws IOException, GeneralSecurityException {
        if (resource == null) {
            return null;
        }
        final KeyStore store = KeyStore.getInstance(firstNonNull(type, "JKS"));
        final URL url = ResourceUtils.getURL(resource);
        store.load(url.openStream(), password != null ? password.toCharArray()
                                                      : null);
        return store;
    }
}
