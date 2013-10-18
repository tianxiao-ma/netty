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

package io.netty.buffer;

final class PoolSubpage<T> {

    final PoolChunk<T> chunk;
    final int memoryMapIdx;
    final int runOffset;
    final int pageSize;
    /**
     * 一个page可以分配{@link #maxNumElems}个具有{@link #elemSize}大小的块，这些块的占用情况就是使用{@link #bitmap}来保存的。
     * {@link #bitmap}中的每一个元素可以表示64个块的状态。相当于把一个具有{@link #maxNumElems}位的状态数组用long类型的数组来表示，
     * 可以减小内存消耗并加快速度。其中0表示未占用，1表示已占用。
     */
    final long[] bitmap;

    PoolSubpage<T> prev;
    PoolSubpage<T> next;

    boolean doNotDestroy;
    int elemSize;
    int maxNumElems;
    int nextAvail;
    int bitmapLength;
    int numAvail;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /** Special constructor that creates a linked list head */
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        runOffset = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubpage(PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64
        init(elemSize);
    }

    void init(int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;
        if (elemSize != 0) {
            maxNumElems = numAvail = pageSize / elemSize; // 一个page最多可以分配多少elemSize大小的块
            nextAvail = 0;

            // bitmap是long类型数组，所以每个元素有64位(long的长度)
            // 将maxNumElems除以64，就可以知道bitmap的元素个数
            // 如果maxNumElems不能被64整除，那么需要增加一个bitmap中的元素
            // 比如，假设maxNumElems=65，也就是一个page可以分配65个elemSize大小的存储块，这个时候需要在bitmap中存放2个元素用来标记
            // 这些小块的分配情况，如果bitmap中元素的某个位是1表示这个小块已经被分配出去了
            bitmapLength = maxNumElems >>> 6; // maxNumElems / 64
            if ((maxNumElems & 63) != 0) {
                bitmapLength ++; // 如果maxNumElems不能整除64，那么bitmapLength加上1
            }

            for (int i = 0; i < bitmapLength; i ++) {
                bitmap[i] = 0;
            }
        }

        addToPool();
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     */
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        final int bitmapIdx = nextAvail;
        int q = bitmapIdx >>> 6;// bitmapIdx / 64
        int r = bitmapIdx & 63;// bitmapIdx除以64的余数

        // 下面计算bitmapIdx代表的位在bitmap中所处的位置，q是存储状态位的bitmap中的long元素的索引，而r是余数，表示在long元素中的具体哪一位
        assert (bitmap[q] >>> r & 1) == 0;
        bitmap[q] |= 1L << r;

        if (-- numAvail == 0) {
            removeFromPool();
            nextAvail = -1;
        } else {
            nextAvail = findNextAvailable();
        }

        return toHandle(bitmapIdx);
    }

    /**
     * @return {@code true} if this subpage is in use.
     *         {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    boolean free(int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }

        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;

        // numAvail是0，说明已经page内已经没有可用空间了，释放了一个之后，又有了可用空间，需要把page重新放到可用page队列中去
        if (numAvail ++ == 0) {
            nextAvail = bitmapIdx;
            addToPool();
            return true;
        }

        if (numAvail < maxNumElems) { //释放之后，可用块的数量小于maxNumElems，说明可以继续在这个page中分配内存
            return true;
        } else { // numAvail == maxNumElems说明整个page中的数据已经都被释放掉了，可以从以分配page中移除，重新进行分配了
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    private void addToPool() {
        PoolSubpage<T> head = chunk.arena.findSubpagePoolHead(elemSize);
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private int findNextAvailable() {
        int newNextAvail = -1;
        loop:
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];
            if (~bits != 0) {
                for (int j = 0; j < 64; j ++) {
                    if ((bits & 1) == 0) {
                        newNextAvail = i << 6 | j;
                        break loop;
                    }
                    bits >>>= 1;
                }
            }
        }

        if (newNextAvail < maxNumElems) {
            return newNextAvail;
        } else {
            return -1;
        }
    }

    // 返回的是一个long，高32位保存bitmapIdx，低32位保存memoryMapIdex
    private long toHandle(int bitmapIdx) {
        // 0x4000000000000000L=1<<62，是long能够表示的正整数中，最大的一个是2的指数次方的值(2的62次方，2的63次方是一个负数)
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

    public String toString() {
        if (!doNotDestroy) {
            return "(" + memoryMapIdx + ": not in use)";
        }

        return String.valueOf('(') + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems +
               ", offset: " + runOffset + ", length: " + pageSize + ", elemSize: " + elemSize + ')';
    }
}
