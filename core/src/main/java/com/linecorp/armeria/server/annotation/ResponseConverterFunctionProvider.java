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
package com.linecorp.armeria.server.annotation;

import java.lang.reflect.Type;

import javax.annotation.Nullable;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServiceRequestContext;

/**
 * A {@link ResponseConverterFunction} provider interface which creates a new
 * {@link ResponseConverterFunction} for converting an object of the given type and functions.
 */
@FunctionalInterface
public interface ResponseConverterFunctionProvider {

    /**
     * Creates a new {@link ResponseConverterFunction} instance.
     *
     * @param responseType the return {@link Type} type of the annotated HTTP service method
     * @param generalResponseConverter the function which converts an object to an {@link HttpResponse}
     *                                 using the configured {@link ResponseConverterFunction}s
     * @param generalExceptionConverter the function which converts a {@link Throwable} to an
     *                                  {@link HttpResponse} using the configured
     *                                  {@link ExceptionHandlerFunction}s
     */
    @Nullable
    ResponseConverterFunction createResponseConverterFunction(
            Type responseType,
            GeneralResponseConverter generalResponseConverter,
            GeneralExceptionConverter generalExceptionConverter);

    /**
     * Convert an object to an {@link HttpResponse} using the configured {@link ResponseConverterFunction}s.
     */
    @FunctionalInterface
    interface GeneralResponseConverter {
        HttpResponse convertResponse(ServiceRequestContext ctx, @Nullable Object result);
    }

    /**
     * Convert a {@link Throwable} to an {@link HttpResponse} using the configured
     * {@link ResponseConverterFunction}s.
     */
    @FunctionalInterface
    interface GeneralExceptionConverter {
        HttpResponse convertException(ServiceRequestContext ctx, HttpRequest req, Throwable cause);
    }
}
