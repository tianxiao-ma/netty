/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

/**
 * Special {@link ChannelFuture} which is writable.
 */
public interface ChannelPromise extends ChannelFuture, Promise<Void> {

    @Override
    Channel channel();

    @Override
    ChannelPromise setSuccess(Void result);

    ChannelPromise setSuccess();

    boolean trySuccess();

    @Override
    ChannelPromise setFailure(Throwable cause);

    @Override
    ChannelPromise addListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelPromise addListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelPromise removeListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelPromise removeListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelPromise sync() throws InterruptedException;

    @Override
    ChannelPromise syncUninterruptibly();

    @Override
    ChannelPromise await() throws InterruptedException;

    @Override
    ChannelPromise awaitUninterruptibly();

    /**
     * A {@link ChannelFuture} which may only be used for flush, write and sendFile operations and only if you
     * are sure the actual {@link ChannelHandler}s in the {@link ChannelPipeline} don't make use of it.
     */
    interface VoidChannelPromise extends ChannelPromise {

        /**
         * Does nothing and just return itself.
         */
        @Override
        VoidChannelPromise setSuccess(Void result);

        /**
         * Does nothing and just return itself.
         */
        @Override
        VoidChannelPromise setSuccess();

        /**
         * Does nothing and just return itself.
         */
        @Override
        VoidChannelPromise setFailure(Throwable cause);

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise addListener(GenericFutureListener<? extends Future<Void>> listener);

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise addListeners(GenericFutureListener<? extends Future<Void>>... listeners);

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise removeListener(GenericFutureListener<? extends Future<Void>> listener);

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise removeListeners(GenericFutureListener<? extends Future<Void>>... listeners);

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise sync() throws InterruptedException;

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise syncUninterruptibly();

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise await() throws InterruptedException;

        /**
         * Throws {@link IllegalStateException}.
         */
        @Override
        VoidChannelPromise awaitUninterruptibly();

        /**
         * Returns {@code true} if and only if the I/O operation was completed
         * successfully.
         */
        boolean isSuccess();

        /**
         * Returns {@code null}
         */
        Throwable cause();

        /**
         * Throws {@link IllegalStateException}.
         */
        boolean await(long timeout, TimeUnit unit) throws InterruptedException;

        /**
         * Throws {@link IllegalStateException}.
         */
        boolean await(long timeoutMillis) throws InterruptedException;

        /**
         * Throws {@link IllegalStateException}.
         */
        boolean awaitUninterruptibly(long timeout, TimeUnit unit);

        /**
         * Throws {@link IllegalStateException}.
         */
        boolean awaitUninterruptibly(long timeoutMillis);

        /**
         * Returns {@code null}
         */
        Void getNow();
    }
}
