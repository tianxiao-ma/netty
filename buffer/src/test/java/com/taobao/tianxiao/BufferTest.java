package com.taobao.tianxiao;

import io.netty.buffer.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-01 15:57
 */
public class BufferTest {
    @Test
    public void test() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(3);
        buf.writeByte(7);

        System.out.println(buf.getUnsignedByte(1));
        System.out.println(buf.getUnsignedByte(1) << 1);
        System.out.println(BufUtil.hexDump(buf));
    }

    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);
    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i --) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        return chunkSize;
    }

    @Test
    public void testPooledByteBufAllocator() {
        validateAndCalculateChunkSize(MAX_CHUNK_SIZE / 2, 2);


        int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);
        int MIN_PAGE_SIZE = 4096 << 4;
        System.out.println(MAX_CHUNK_SIZE);
        System.out.println(MIN_PAGE_SIZE << 14);


        boolean found1 = false;
        int pageShifts = 0;
        for (int i = 8192; i != 0 ; i >>= 1) {
            if ((i & 1) != 0) {
                if (!found1) {
                    found1 = true;
                } else {
                    throw new IllegalArgumentException("pageSize: " + 8192 + " (expected: power of 2");
                }
            } else {
                if (!found1) {
                    pageShifts ++;
                }
            }
        }

        System.out.println(pageShifts);
    }
}
