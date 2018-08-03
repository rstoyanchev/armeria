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
package com.linecorp.armeria.testing.internal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

/**
 * An {@link AddressResolverGroup} which always returns {@code 127.0.0.1} for any hostname.
 */
public final class DummyAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor eventExecutor) {
        return new InetSocketAddressResolver(eventExecutor, new InetNameResolver(eventExecutor) {
            @Override
            protected void doResolve(String hostname, Promise<InetAddress> promise) {
                try {
                    promise.setSuccess(newAddress(hostname));
                } catch (UnknownHostException e) {
                    promise.setFailure(e);
                }
            }

            @Override
            protected void doResolveAll(String hostname, Promise<List<InetAddress>> promise) {
                try {
                    promise.setSuccess(Collections.singletonList(newAddress(hostname)));
                } catch (UnknownHostException e) {
                    promise.setFailure(e);
                }
            }

            private InetAddress newAddress(String hostname) throws UnknownHostException {
                return InetAddress.getByAddress(hostname, new byte[] { 127, 0, 0, 1 });
            }
        });
    }
}
