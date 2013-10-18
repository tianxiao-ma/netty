package com.taobao.tianxiao;

import io.netty.buffer.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-01 15:57
 */
public class BufferTest {
    private static final int ST_UNUSED = 0;

    @Test
    public void test() {
        int pageSize = 8192;
        int pageShifts = validateAndCalculatePageShifts(pageSize);
        int maxOrder = 11;
        int chunkSize = validateAndCalculateChunkSize(pageSize, maxOrder);
        int chunkSizeInPages = chunkSize >>> pageShifts;
        int maxSubpageAllocs = 1 << maxOrder;

        Assert.assertEquals(chunkSizeInPages, maxSubpageAllocs);
        System.out.println("pageSize=2的" + calculateShifts(pageSize) + "次方");
        System.out.println("chunkSize=2的" + calculateShifts(chunkSize) + "次方");
        System.out.println("pageShifts:" + pageShifts);

        int totalLoop = 0;
        int[] memoryMap = new int[maxSubpageAllocs << 1];
        int memoryMapIndex = 1;
        for (int i = 0; i <= maxOrder; i++) {
            int runSizeInPages = chunkSizeInPages >>> i;
            int loopCount = 0;
            for (int j = 0; j < chunkSizeInPages; j += runSizeInPages) {
                //noinspection PointlessBitwiseExpression
                memoryMap[memoryMapIndex++] = j << 17 | runSizeInPages << 2 | ST_UNUSED;
                loopCount++;
                totalLoop++;
            }
            System.out.println(loopCount + "=" + "2的" + calculateShifts(loopCount) + "次方");
        }


        System.out.println(maxSubpageAllocs << 1);
        System.out.println(totalLoop);
    }

    @Test
    public void test2() {
        System.out.println((4096 & 4096 - 1) + 16);


        System.out.println(96 >>> 6);
        System.out.println(96 & 63);

        System.out.println(0x4000000000000000L);
        System.out.println((long) 1 << 62);
        System.out.println(0x8000000000000000L);
        System.out.println((long) 1 << 63);
    }

    @Test
    public void test3() {
        int curIdx = 1;
        for (int i = 0; i < 10; i++) {
            int nextIdx = curIdx << 1 ^ nextRandom();
            int unusedIdx = nextIdx ^ 1;
            System.out.println(nextIdx + ":" + unusedIdx);
            curIdx = nextIdx;
        }
    }

    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;
    private long random = (System.nanoTime() ^ multiplier) & mask;

    private int nextRandom() {
        random = random * multiplier + addend & mask;
        return (int) (random >>> 47) & 1;
    }

    private static int calculateShifts(int val) {
        int shifts = 0;
        for (int i = val; i != 0 && (i & 1) == 0; i >>= 1) {
            shifts++;
        }

        return shifts;
    }

    private static int validateAndCalculatePageShifts(int pageSize) {
        if (pageSize < 4096) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: 4096+)");
        }

        // Ensure pageSize is power of 2.
        // 只有最高位为1，其他各个位都位0
        int loopCount = 0;
        boolean found1 = false;
        int pageShifts = 0;
        for (int i = pageSize; i != 0; i >>= 1) {
            if ((i & 1) != 0) {
                if (!found1) {
                    found1 = true;
                } else {
                    throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: power of 2");
                }
            } else {
                if (!found1) {
                    pageShifts++;
                }
            }
            loopCount++;
        }
        System.out.println("pageShifts loopCount:" + loopCount);
        return pageShifts;
    }


    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int loopCount = 0;
        int chunkSize = pageSize;
        System.out.println("init chunkSize=2的" + calculateShifts(chunkSize) + "次方");
        for (int i = maxOrder; i > 0; i--) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
            loopCount++;
        }
        System.out.println("validateAndCalculateChunkSize loop count:" + loopCount);
        return chunkSize;
    }

    @Test
    public void testPooledByteBufAllocator() {
//        validateAndCalculateChunkSize(MAX_CHUNK_SIZE / 2, 2);


        int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);
        int MIN_PAGE_SIZE = 4096 << 4;
        System.out.println(MAX_CHUNK_SIZE);
        System.out.println(MIN_PAGE_SIZE << 14);


        boolean found1 = false;
        int pageShifts = 0;
        for (int i = 8192; i != 0; i >>= 1) {
            if ((i & 1) != 0) {
                if (!found1) {
                    found1 = true;
                } else {
                    throw new IllegalArgumentException("pageSize: " + 8192 + " (expected: power of 2");
                }
            } else {
                if (!found1) {
                    pageShifts++;
                }
            }
        }

        System.out.println(pageShifts);
    }
}
