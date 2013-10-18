package com.taobao.tianxiao;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.Before;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-10-18 14:12
 */
public class PooledByteBufAllocatorTest {
    private static final int PAGE_SIZE = 1 << 12;
    private static final int MAX_ORDER = 0;

    PooledByteBufAllocator allocator;

    @Before
    public void setup() {
        System.setProperty("io.netty.allocator.pageSize", String.valueOf(PAGE_SIZE));
        System.setProperty("io.netty.allocator.maxOrder", String.valueOf(MAX_ORDER));
        allocator = new PooledByteBufAllocator(true);
    }

    @Test
    public void test() {
        ByteBuf buf512 = allocator.heapBuffer(512);
        ByteBuf buf1024 = allocator.heapBuffer(1024);

        ByteBuf buf512_ = allocator.heapBuffer(512);
        ByteBuf buf1024_ = allocator.heapBuffer(1024);
    }
}
