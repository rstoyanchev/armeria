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
package com.linecorp.armeria.server.rxjava;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.internal.ObjectsToHttpResponseConvertingSubscriber;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import com.linecorp.armeria.server.annotation.ResponseConverterFunction;

import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * A {@link ResponseConverterFunction} which subscribes an {@link ObservableSource} and then converts
 * its value when it is resolved.
 */
public class ObservableResponseConverterFunction implements ResponseConverterFunction {

    private final ResponseConverterFunction configuredResponseConverter;
    private final ExceptionHandlerFunction configuredExceptionHandler;

    /**
     * Creates a new {@link ResponseConverterFunction} instance.
     *
     * @param configuredResponseConverter the function which converts an object with the configured
     *                                    {@link ResponseConverterFunction}s
     * @param configuredExceptionHandler the function which converts a {@link Throwable} with the configured
     *                                   {@link ExceptionHandlerFunction}s
     */
    public ObservableResponseConverterFunction(ResponseConverterFunction configuredResponseConverter,
                                               ExceptionHandlerFunction configuredExceptionHandler) {
        this.configuredResponseConverter =
                requireNonNull(configuredResponseConverter, "configuredResponseConverter");
        this.configuredExceptionHandler =
                requireNonNull(configuredExceptionHandler, "configuredExceptionHandler");
    }

    @Override
    public HttpResponse convertResponse(ServiceRequestContext ctx,
                                        @Nullable Object result) throws Exception {
        if (result instanceof ObservableSource) {
            final CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            final ObservableSource<?> observable = (ObservableSource<?>) result;
            final ObjectsToHttpResponseConvertingSubscriber subscriber =
                    new ObjectsToHttpResponseConvertingSubscriber(ctx, ctx.request(), future,
                                                                  configuredResponseConverter,
                                                                  configuredExceptionHandler);
            observable.subscribe(new Observer<Object>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(Object o) {
                    subscriber.onNext(o);
                }

                @Override
                public void onError(Throwable e) {
                    subscriber.onError(e);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            });
            return HttpResponse.from(future);
        }

        return ResponseConverterFunction.fallthrough();
    }
}
