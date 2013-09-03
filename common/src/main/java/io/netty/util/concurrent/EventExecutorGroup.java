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
package io.netty.util.concurrent;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The {@link EventExecutorGroup} is responsible to provide {@link EventExecutor}'s to use via its
 * {@link #next()} method. Beside this it also is responsible to handle their live-cycle and allows
 * to shut them down in a global fashion.
 *
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * Returns {@code true} if and only if this executor was started to be
     * {@linkplain #shutdownGracefully() shut down gracefuclly} or was {@linkplain #isShutdown() shut down}.
     */
    boolean isShuttingDown();

    /**
     * Shortcut method for {@link #shutdownGracefully(long, long, TimeUnit)} with sensible default values.
     */
    void shutdownGracefully();

    /**
     * Signals this executor that the caller wants the executor to be shut down.  Once this method is called,
     * {@link #isShuttingDown()} starts to return {@code true}, and the executor prepares to shut itself down.
     * Unlike {@link #shutdown()}, graceful shutdown ensures that no tasks are submitted for <i>'the quiet period'</i>
     * (usually a couple seconds) before it shuts itself down.  If a task is submitted during the quiet period,
     * it is guaranteed to be accepted and the quiet period will start over.
     * <p>
     * 这里的quietPeriod，也就是静默期的意思是，当在这段时间内没有任何任务提交任务的时候，就能够安全的停止事件循环了。事件循环每次执行完一个或者多个任务之后都回去更新一个最后执行时间，当要关闭这个事件循环的时候回去检查一下当前时间与上一次执行时间是不是在静默期内，如果在静默期内那么事件循环就不能关闭。
     *
     * 当然，如果在静默周期内一直有任务提交，那么这个事件循环就永远无法关闭了，所以又有了另外一个参数，也就是timeout。不管静默周期内是否有任务提交，只要第一次调用shutdownGracefully的时间和当前时间超出了timeout约定的时间，那么事件循环都将会关闭
     * </p>
     * @param quietPeriod the quiet period as described in the documentation
     * @param timeout     the maximum amount of time to wait until the executor is {@linkplain #shutdown()}
     *                    regardless if a task was submitted during the quiet period
     * @param unit        the unit of {@code quietPeriod} and {@code timeout}
     *
     */
    void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    List<Runnable> shutdownNow();

    /**
     * Returns one of the {@link EventExecutor}s that belong to this group.
     */
    EventExecutor next();

    /**
     * Returns a read-only {@link Iterator} over all {@link EventExecutor}, which are handled by this
     * {@link EventExecutorGroup} at the time of invoke this method.
     */
    @Override
    Iterator<EventExecutor> iterator();

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
