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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.server.ServiceRequestContext;

import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import reactor.core.publisher.Flux;

/**
 * Wrap an {@link HttpRequest} received from {@code Armeria} as a {@link ServerHttpResponse}.
 */
public class ArmeriaServerHttpRequest extends AbstractServerHttpRequest {

    private final ServiceRequestContext ctx;
    private final HttpRequest req;
    private final DataBufferFactory factory;

    ArmeriaServerHttpRequest(ServiceRequestContext ctx,
                             HttpRequest req,
                             DataBufferFactory factory) throws URISyntaxException {
        super(new URI(requireNonNull(req, "req").path()),
              null,
              fromArmeriaHttpHeaders(req.headers()));
        this.ctx = requireNonNull(ctx, "ctx");
        this.req = req;
        this.factory = requireNonNull(factory, "factory");
    }

    private static HttpHeaders fromArmeriaHttpHeaders(com.linecorp.armeria.common.HttpHeaders httpHeaders) {
        final HttpHeaders newHttpHeaders = new HttpHeaders();
        httpHeaders.forEach(entry -> newHttpHeaders.add(entry.getKey().toString(), entry.getValue()));
        return newHttpHeaders;
    }

    @Override
    protected MultiValueMap<String, HttpCookie> initCookies() {
        final MultiValueMap<String, HttpCookie> cookies = new LinkedMultiValueMap<>();
        final List<String> values = req.headers().getAll(HttpHeaderNames.COOKIE);
        values.stream()
              .map(ServerCookieDecoder.LAX::decode)
              .flatMap(Collection::stream)
              .forEach(c -> cookies.add(c.name(), new HttpCookie(c.name(), c.value())));
        return cookies;
    }

    @Nullable
    @Override
    protected SslInfo initSslInfo() {
        final SSLSession session = ctx.sslSession();
        return session != null ? new DefaultSslInfo(session)
                               : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getNativeRequest() {
        return (T) req;
    }

    @Override
    public String getMethodValue() {
        return req.method().name();
    }

    @Override
    public Flux<DataBuffer> getBody() {
        return Flux.from(req).map(httpObject -> {
            // Expect HttpData only because this method is to get the body of the request.
            assert httpObject instanceof HttpData;
            final HttpData data = (HttpData) httpObject;
            final byte[] array = data.array();
            return factory.wrap(ByteBuffer.wrap(array, data.offset(), data.length()));
        });
    }
}
