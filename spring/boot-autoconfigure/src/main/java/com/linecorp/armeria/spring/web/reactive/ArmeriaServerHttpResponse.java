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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;

import com.linecorp.armeria.common.DefaultHttpHeaders;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.server.ServiceRequestContext;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.AsciiString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Wrap an {@link HttpResponseWriter} to adapt it to {@link ServerHttpResponse} interface.
 */
public class ArmeriaServerHttpResponse extends AbstractServerHttpResponse {
    private static final Logger logger = LoggerFactory.getLogger(ArmeriaServerHttpResponse.class);

    private final ServiceRequestContext ctx;
    private final HttpResponseWriter writer;
    private final DataBufferFactory factory;

    private final HttpHeaders responseHeaders = new DefaultHttpHeaders();
    private boolean isHeaderSent;

    ArmeriaServerHttpResponse(ServiceRequestContext ctx,
                              HttpResponseWriter writer,
                              DataBufferFactory factory) {
        super(requireNonNull(factory, "factory"));
        this.ctx = requireNonNull(ctx, "ctx");
        this.writer = requireNonNull(writer, "writer");
        this.factory = factory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getNativeResponse() {
        return (T) writer;
    }

    @Override
    protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> publisher) {
        return Flux.from(publisher)
                   .map(this::write0)
                   .doOnComplete(this::setComplete)
                   .doOnError(this::setComplete)
                   .then();
    }

    @Override
    protected Mono<Void> writeAndFlushWithInternal(
            Publisher<? extends Publisher<? extends DataBuffer>> publisher) {
        return Flux.from(publisher)
                   .map(this::writeWithInternal)
                   .doOnComplete(this::setComplete)
                   .doOnError(this::setComplete)
                   .then();
    }

    private DataBuffer write0(DataBuffer data) {
        sendResponseHeaders();
        writer.write(HttpData.of(data.asByteBuffer().array()));
        // FluxMap doesn't allow returning null from mapper, so return the received data.
        return data;
    }

    private void sendResponseHeaders() {
        if (isHeaderSent) {
            return;
        }

        assert responseHeaders.status() != null : "no http status in the response header";
        writer.write(responseHeaders);
        isHeaderSent = true;
    }

    @Override
    protected void applyStatusCode() {
        final HttpStatus httpStatus = getStatusCode();
        if (httpStatus != null) {
            responseHeaders.status(httpStatus.value());
        } else {
            // If there is no status code specified, set 200 Ok by default.
            responseHeaders.status(com.linecorp.armeria.common.HttpStatus.OK);
        }
    }

    @Override
    protected void applyHeaders() {
        getHeaders().forEach((name, values) -> responseHeaders.add(AsciiString.of(name), values));
    }

    @Override
    protected void applyCookies() {
        final List<String> cookieValues =
                getCookies().values().stream()
                            .flatMap(Collection::stream)
                            .map(ArmeriaServerHttpResponse::toNettyCookie)
                            .map(ServerCookieEncoder.STRICT::encode).collect(toImmutableList());
        if (!cookieValues.isEmpty()) {
            responseHeaders.add(HttpHeaderNames.SET_COOKIE, cookieValues);
        }
    }

    @Override
    public Mono<Void> setComplete() {
        return setComplete(null);
    }

    /**
     * Closes the {@link HttpResponseWriter} with the specified {@link Throwable} which is raised during
     * sending the response.
     */
    public Mono<Void> setComplete(@Nullable Throwable cause) {
        return super.setComplete().then(cleanup(cause));
    }

    /**
     * Closes the {@link HttpResponseWriter} if it is opened.
     */
    private Mono<Void> cleanup(@Nullable Throwable cause) {
        return Mono.fromRunnable(() -> {
            if (writer.isOpen()) {
                if (cause != null) {
                    writer.close(cause);
                    logger.debug("{} HttpResponseWriter has been closed with a cause", ctx, cause);
                } else {
                    sendResponseHeaders();
                    writer.close();
                    logger.debug("{} HttpResponseWriter has been closed", ctx);
                }
            }
        });
    }

    /**
     * Converts the specified {@link ResponseCookie} to Netty's {@link Cookie} interface.
     */
    private static Cookie toNettyCookie(ResponseCookie resCookie) {
        final DefaultCookie cookie = new DefaultCookie(resCookie.getName(), resCookie.getValue());
        cookie.setHttpOnly(resCookie.isHttpOnly());
        cookie.setMaxAge(resCookie.getMaxAge().getSeconds());
        cookie.setSecure(resCookie.isSecure());
        // Domain and path are nullable, but the setters allow null as its input.
        cookie.setDomain(resCookie.getDomain());
        cookie.setPath(resCookie.getPath());
        return cookie;
    }
}
