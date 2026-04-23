/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.infra.chat;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 流式首包探测桥接器
 * 创建一个桥，可以在将第一个流事件暴露给真正的回调之前探测它。
 */
final class ProbeStreamBridge implements StreamCallback {

    // 探测成功后将接收流事件的下游回调。
    private final StreamCallback downstream;
    // 一旦观察到第一个有意义的流结果，就完成。
    private final CompletableFuture<ProbeResult> probe = new CompletableFuture<>();
    // 保护缓冲回调和提交状态转换.
    private final Object lock = new Object();
    // 临时存储回调，直到第一个数据包探测器决定可以转发该流.
    private final List<Runnable> buffer = new ArrayList<>();
    // 标记是否允许缓冲事件立即流向下游
    private volatile boolean committed;


    ProbeStreamBridge(StreamCallback downstream) {
        this.downstream = downstream;
    }

    /**********************首包探测的4种可能结果****************************/

    /**
     * 处理正常内容事件，将探测标记为成功，并转发或缓冲回调。
     */
    @Override
    public void onContent(String content) {
        probe.complete(ProbeResult.success());
        bufferOrDispatch(() -> downstream.onContent(content));
    }

    @Override
    public void onThinking(String content) {
        probe.complete(ProbeResult.success());
        bufferOrDispatch(() -> downstream.onThinking(content));
    }

    /**
     * 处理流完成，记录流结束时没有内容，并转发或缓冲完成。
     */
    @Override
    public void onComplete() {
        probe.complete(ProbeResult.noContent());
        bufferOrDispatch(downstream::onComplete);
    }

    /**
     * 错误
     */
    @Override
    public void onError(Throwable t) {
        probe.complete(ProbeResult.error(t));
        bufferOrDispatch(() -> downstream.onError(t));
    }

    /**
     * 阻塞等待首包探测结果，SUCCESS 时自动提交缓冲
     */
    ProbeResult awaitFirstPacket(long timeout, TimeUnit unit) throws InterruptedException {
        ProbeResult result;
        try {
            result = probe.get(timeout, unit);
        } catch (TimeoutException e) {
            return ProbeResult.timeout();
        } catch (ExecutionException e) {
            return ProbeResult.error(e.getCause());
        }

        if (result.isSuccess()) {
            commit();
        }
        return result;
    }

    /**
     * 只提交一次网桥，并按顺序将所有缓冲回调刷新到下游。
     * 在首包返回之前，可能还会有新的聊天请求进来，缓存到list里
     * 只有首包探测时会这样处理
     *
     * 加锁是为防止：多线程都去执行buffer.forEach(Runnable::run)
     */
    private void commit() {
        synchronized (lock) {
            if (committed) {
                return;
            }
            committed = true;
            buffer.forEach(Runnable::run);
        }
    }

    /** 对于新的请求
     * 要么在提交之前缓冲回调，要么在网桥已经提交时立即分派回调。
     */
    private void bufferOrDispatch(Runnable action) {
        boolean dispatchNow;
        synchronized (lock) {
            dispatchNow = committed;
            if (!dispatchNow) {
                buffer.add(action);
            }
        }
        // 额外声明一个变量放到锁外面，这样就不同在锁里执行冗长的run操作
        if (dispatchNow) {
            action.run();
        }
    }

    /**
     * 探测结果
     */
    @Getter
    static class ProbeResult {

        // 对第一个探测包结果进行分类
        enum Type {SUCCESS, ERROR, TIMEOUT, NO_CONTENT}

        // 第一个数据包探测的高级结果。
        private final Type type;

        // 当探测器失败时，携带错误。
        private final Throwable error;

        private ProbeResult(Type type, Throwable error) {
            this.type = type;
            this.error = error;
        }

        /**
         * 构建一个结果，表示流产生了可用的输出。
         */
        static ProbeResult success() {
            return new ProbeResult(Type.SUCCESS, null);
        }

        static ProbeResult error(Throwable t) {
            return new ProbeResult(Type.ERROR, t);
        }

        static ProbeResult timeout() {
            return new ProbeResult(Type.TIMEOUT, null);
        }

        static ProbeResult noContent() {
            return new ProbeResult(Type.NO_CONTENT, null);
        }

        /**
         * 返回探测器是否观察到成功的第一个数据包，并可以释放缓冲的回调。
         */
        boolean isSuccess() {
            return type == Type.SUCCESS;
        }
    }
}
