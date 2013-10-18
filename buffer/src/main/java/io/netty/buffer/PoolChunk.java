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

/**
 * 每个chunk利用树型结构来组织可用存储区域，用来分配可用存储区域的树型数据结构被存放在一个数组中(<code>memoryMap</code>)，以<code>pageSize=4</code>，<code>maxOrder=4</code>为例，会形成如下的数据结构：
 * <pre>
 *                                        64
 *                                         |
 *                           -----------------------------
 *                          |                             |
 *                          32                           32
 *                          |                             |
 *                     -----------                   -----------
 *                    |           |                 |           |
 *                   16          16                 16          16
 *                    |           |                 |           |
 *                  -----       -----             -----       -----
 *                 |     |     |      |          |     |     |     |
 *                 8     8     8      8          8     8     8     8
 *                 |     |     |      |          |     |     |     |
 *                ---   ---   ---    ---        ---   ---   ---   ---
 *               |   | |   | |   |  |   |      |   | |   | |   | |   |
 *               4   4 4   4 4   4  4   4      4   4 4   4 4   4 4   4
 * </pre>
 * <p>
 * 按照上面这颗树组织起来的chunk(一颗满二叉树)，总的大小是64，最小的存储分配单位是一个page，大小是4，可以分配16个；最大的存储分配单位是整个chunk，大小是64，可以分配1个。
 * </p>
 * <p>
 * 在最大和最小存储分配单位之间，还可以分配大小为8、16和32的存储块，他们的数量分别是8个、4个和2个。
 * </p>
 * <p>
 * 这棵树的高度是<code>maxOrder+1</code>，拥有<code>2^(maxOrder + 1) - 1</code>个节点。第n层上节点表示的存储区域是第n-1层节点的2倍，其中n的最大值是<code>maxOrder+1</code>。
 * </p>
 * <p>
 * 分配存储空间其实就是对树进行遍历，目的是找到一个节点，这个节点表示存储空间大小与要求分配的存储空间大小一致且未使用，如果找到了这样的几点，那么返回这个节点在<code>memoryMap</code>中的索引号，否则返回-1。遍历时采用深度优先算法(但与标准的深度优先算法有所不同)。
 * </p>
 * @param <T>
 */
final class PoolChunk<T> {
    // 未使用状态，内存块初始时的状态
    private static final int ST_UNUSED = 0;
    // 当前内存块所在节点的子树已经被遍历过了
    private static final int ST_BRANCH = 1;
    // 内存块已分配
    private static final int ST_ALLOCATED = 2;
    private static final int ST_ALLOCATED_SUBPAGE = ST_ALLOCATED | 1;

    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;

    final PoolArena<T> arena;
    final T memory;
    final boolean unpooled;

    // memoryMap用来追踪内存块的使用情况
    private final int[] memoryMap;
    private final PoolSubpage<T>[] subpages;
    /** Used to determine if the requested capacity is equal to or greater than pageSize. */
    private final int subpageOverflowMask;
    private final int pageSize;
    private final int pageShifts;

    private final int chunkSize;
    private final int maxSubpageAllocs;

    private long random = (System.nanoTime() ^ multiplier) & mask;

    private int freeBytes;

    // chunk属于哪个chunkList
    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /**
     * chunkSize = pageSize << maxOrder = (1 << pageShifts) << maxOrder
     * chunkSizeInPages(每个chunk中的page数) = chunkSize >> pageShifts = 1 << maxOrder
     * @param arena
     * @param memory
     * @param pageSize
     * @param maxOrder
     * @param pageShifts
     * @param chunkSize
     */
    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        subpageOverflowMask = ~(pageSize - 1); // pageSize是2的n次方
        freeBytes = chunkSize;

        int chunkSizeInPages = chunkSize >>> pageShifts;
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        // memoryMap中保存了不同大小的内存块的索引和状态信息，从下面初始化memoryMap的双层循环可以看出，memoryMap中的内存块大小分别是1个page一个块，2个page一个块，4个page一个块，直到整个chunk(1 << maxorder个page)一个块
        // memoryMap[1]是整个chunk作为一个块，memoryMap[2]和memoryMap[3]分别都是半个chunk一个块，memoryMap[4-7]是四分之一个chunk一个块，依次类推，最后的(1 << maxorder)-1个元素则是1个page一个块
        memoryMap = new int[maxSubpageAllocs << 1];
        int memoryMapIndex = 1;
        // 总的循环次数是一个等比数列求和，第一次内循环1次，第二次内循环2次，第三次内循环4次，第maxorder次内循环运行2^maxorder方
        // 外循环运行了maxOrder + 1次，按照等比数列求和公式可以计算出循环的总次数为(2^(maxOrder+1)) - 1次
        // 而maxSubpageAllocs等于2^maxOrder，分配的memoryMap大小是maxSubpageAllocs << 1 = 2^(maxOrder+1) 正好是能够放下的
        for (int i = 0; i <= maxOrder; i ++) {
            int runSizeInPages = chunkSizeInPages >>> i;//runSizeInPages表示总的page数的1/n，其中n=1，2，4，8，直到maxOrder
            for (int j = 0; j < chunkSizeInPages; j += runSizeInPages) {
                //noinspection PointlessBitwiseExpression
                // chunkSizeInPages = 1 << maxOrder，而maxOrder允许的最大值是14
                // 当maxOrder=14时，j最大的可能值是(1 << 14) - 1，这个值再右移17位，结果就是把int的最高13个位都设置成了1
                // 当maxOrder=14时，chunkSizeInPages = 1 << 14，runSizeInPages的最大值等于chunkSizeInPages，j<<17位正好给runSizeInPages << 2腾出了足够的空间(此时runSizeInPages << 2 = 1 << 16总共需要17个bit位来存放)
                memoryMap[memoryMapIndex ++] = j << 17/*在整个连续存储区域中的起始地址*/ | runSizeInPages << 2 /*子区域的大小*/ |
                        ST_UNUSED;
            }
        }

        // 每个chunk有maxSubpageAllocs个page，maxSubpageAllocs = 1 << maxOrder
        subpages = newSubpageArray(maxSubpageAllocs);
    }

    /** Creates a special chunk that is not pooled. */
    PoolChunk(PoolArena<T> arena, T memory, int size) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        memoryMap = null;
        subpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        chunkSize = size;
        maxSubpageAllocs = 0;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    int usage() {
        if (freeBytes == 0) {
            return 100;
        }

        // 如果不到1%，返回使用率为99
        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    long allocate(int normCapacity) {
        int firstVal = memoryMap[1]; // memory[1]中存的是总的page数
        if ((normCapacity & subpageOverflowMask) != 0) { // 需要分配的内存大于等于pageSize
            return allocateRun(normCapacity, 1, firstVal);
        } else { // 分配的内存小于pageSize
            return allocateSubpage(normCapacity, 1, firstVal);
        }
    }

    private long allocateRun(int normCapacity, int curIdx, int val) {
        for (;;) {
            if ((val & ST_ALLOCATED) != 0) { // state == ST_ALLOCATED || state == ST_ALLOCATED_SUBPAGE
                return -1;
            }

            // 如果当前节点的子树被遍历过了，那么能够分配的最大内存是子树中节点表示的最大内存块
            if ((val & ST_BRANCH) != 0) { // state == ST_BRANCH
                // 随机选择当前节点的一颗子树进行遍历
                int nextIdx = curIdx << 1 ^ nextRandom();
                long res = allocateRun(normCapacity, nextIdx, memoryMap[nextIdx]);
                if (res > 0) {
                    return res;
                }

                // 遍历当前节点的另外一颗子树
                curIdx = nextIdx ^ 1;
                val = memoryMap[curIdx];
                continue;
            }

            // state == ST_UNUSED
            return allocateRunSimple(normCapacity, curIdx, val);
        }
    }

    private long allocateRunSimple(int normCapacity, int curIdx, int val) {
        int runLength = runLength(val);
        if (normCapacity > runLength) {
            return -1;
        }

        for (;;) {
            if (normCapacity == runLength) {
                // Found the run that fits.
                // Note that capacity has been normalized already, so we don't need to deal with
                // the values that are not power of 2.
                memoryMap[curIdx] = val & ~3 | ST_ALLOCATED;
                freeBytes -= runLength;
                return curIdx;
            }

            int nextIdx = curIdx << 1 ^ nextRandom();
            int unusedIdx = nextIdx ^ 1; //末位取反，获得兄弟节点的索引

            // 当前节点标记为已分叉(就是被遍历过了)
            memoryMap[curIdx] = val & ~3 | ST_BRANCH;
            //noinspection PointlessBitwiseExpression
            // 将被选中的孩子节点的兄弟节点标记为未使用
            memoryMap[unusedIdx] = memoryMap[unusedIdx] & ~3 | ST_UNUSED;

            runLength >>>= 1;
            curIdx = nextIdx;
            val = memoryMap[curIdx];
        }
    }

    private long allocateSubpage(int normCapacity, int curIdx, int val) {
        int state = val & 3;
        if (state == ST_BRANCH) {
            int nextIdx = curIdx << 1 ^ nextRandom();
            long res = branchSubpage(normCapacity, nextIdx);
            if (res > 0) {
                return res;
            }

            return branchSubpage(normCapacity, nextIdx ^ 1);
        }

        if (state == ST_UNUSED) {
            return allocateSubpageSimple(normCapacity, curIdx, val);
        }

        // 只有表示单个page的节点才可能会拥有ST_ALLOCATED_SUBPAGE状态，如果一个节点是ST_ALLOCATED_SUBPAGE状态，那么在subpages
        // 中相应位置的对象一定是已经被初始化过了
        if (state == ST_ALLOCATED_SUBPAGE) {
            PoolSubpage<T> subpage = subpages[subpageIdx(curIdx)];
            int elemSize = subpage.elemSize;
            if (normCapacity != elemSize) {
                return -1;
            }

            return subpage.allocate();
        }

        return -1;
    }

    private long allocateSubpageSimple(int normCapacity, int curIdx, int val) {
        int runLength = runLength(val);
        for (;;) {
            if (runLength == pageSize) {
                memoryMap[curIdx] = val & ~3 | ST_ALLOCATED_SUBPAGE;
                freeBytes -= runLength;

                int subpageIdx = subpageIdx(curIdx);
                PoolSubpage<T> subpage = subpages[subpageIdx];
                if (subpage == null) {
                    subpage = new PoolSubpage<T>(this, curIdx, runOffset(val), pageSize, normCapacity);
                    subpages[subpageIdx] = subpage;
                } else {// 释放内存的时候会把响应块的状态设置成UNUSED，但是subpages中的对象并没有被设置成null，所以要重新初始化一下
                    subpage.init(normCapacity);
                }
                return subpage.allocate();
            }

            int nextIdx = curIdx << 1 ^ nextRandom();
            int unusedIdx = nextIdx ^ 1;

            memoryMap[curIdx] = val & ~3 | ST_BRANCH;
            //noinspection PointlessBitwiseExpression
            memoryMap[unusedIdx] = memoryMap[unusedIdx] & ~3 | ST_UNUSED;

            runLength >>>= 1;
            curIdx = nextIdx;
            val = memoryMap[curIdx];
        }
    }

    private long branchSubpage(int normCapacity, int nextIdx) {
        int nextVal = memoryMap[nextIdx];
        if ((nextVal & 3) != ST_ALLOCATED) {
            return allocateSubpage(normCapacity, nextIdx, nextVal);
        }
        return -1;
    }

    void free(long handle) {
        int memoryMapIdx = (int) handle;
        int bitmapIdx = (int) (handle >>> 32);//如果不为0，说明是在一个page内分配的内存

        int val = memoryMap[memoryMapIdx];
        int state = val & 3;
        if (state == ST_ALLOCATED_SUBPAGE) {
            assert bitmapIdx != 0;
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;
            // 如果page中还有被分配出去的字节，直接返回
            // page内的字节第一次被分配出去的时候，从freeBytes减去了整个page的大小，所以只有当page中的字节都被释放之后，才能加回去
            // 返回false说明这个subpage中的字节已经全部被释放了，可以归还到池中
            if (subpage.free(bitmapIdx & 0x3FFFFFFF)) {
                return;
            }
        } else {
            assert state == ST_ALLOCATED : "state: " + state;
            assert bitmapIdx == 0;
        }

        freeBytes += runLength(val);

        for (;;) {
            //noinspection PointlessBitwiseExpression
            memoryMap[memoryMapIdx] = val & ~3 | ST_UNUSED;
            if (memoryMapIdx == 1) {
                assert freeBytes == chunkSize;
                return;
            }

            if ((memoryMap[siblingIdx(memoryMapIdx)] & 3) != ST_UNUSED) {
                break;
            }

            memoryMapIdx = parentIdx(memoryMapIdx);
            val = memoryMap[memoryMapIdx];
        }
    }

    /**
     * 初始化<code>buf</code>，主要是设置<code>buf</code>中的相关位置指针
     * @param buf
     * @param handle
     * @param reqCapacity
     */
    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        int memoryMapIdx = (int) handle;
        int bitmapIdx = (int) (handle >>> 32);
        if (bitmapIdx == 0) {
            int val = memoryMap[memoryMapIdx];
            assert (val & 3) == ST_ALLOCATED : String.valueOf(val & 3);
            buf.init(this, handle, runOffset(val), reqCapacity, runLength(val));
        } else {
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }

    void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        initBufWithSubpage(buf, handle, (int) (handle >>> 32), reqCapacity);
    }

    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = (int) handle;
        int val = memoryMap[memoryMapIdx];
        assert (val & 3) == ST_ALLOCATED_SUBPAGE;

        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        // bitmapIdx要与0x3FFFFFFF的原因是，handle中保存bitmapIdx的高32位在返回的时候为了判断方便(assert bitmapIdx != 0)，将高位
        // 的第63位认为设置成了1，这里需要把这一位还原成0

        // runOffset(val)可以得到page在整个内存数组中的起始偏移位置，而(bitmapIdx & 0x3FFFFFFF) * subpage.elemSize可以得到要分配的
        // 块在page当中的起始位置
        buf.init(this, handle, runOffset(val) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize,
                reqCapacity, subpage.elemSize);
    }

    private static int parentIdx(int memoryMapIdx) {
        return memoryMapIdx >>> 1;
    }

    private static int siblingIdx(int memoryMapIdx) {
        return memoryMapIdx ^ 1;
    }

    /**
     * 获取val表示内存块的大小
     * @param val
     * @return
     */
    private int runLength(int val) {
        // (val >>> 2 & 0x7FFF)可以得到page数，(val >>> 2 & 0x7FFF) << pageShifts其实就是page数乘以page的大小，表示总的内存大小
        return (val >>> 2 & 0x7FFF) << pageShifts;
    }

    /**
     * <p>
     * 获取<code>val</code>表示的page在{@link #memory}中的起始偏移量。
     * </p>
     *
     * <p>
     * {@link #memory}是一个连续的存储空间，<code>val >>> 17</code>可以得到这个page的编号，再左移<code>pageShifts</code>位(相当于乘以{@link #pageSize})就能够得到这个page在整个存储空间中的起始偏移量了。
     * </p>
     * @param val
     * @return
     */
    private int runOffset(int val) {
        return val >>> 17 << pageShifts;
    }

    /**
     * <p>
     * 由memoryMapIdx计算出subpage的索引。在构造<code>memoryMap</code>的时候，分配了<code>2*maxSubpageAllocs</code>个数组元素，
     * 而<code>memoryMap</code>中从下标<code>maxSubpageAllocs</code>开始到下标<code>2*maxSubpageAllocs - 1</code>的元素都表示
     * 以一个page为存储单位的空间，所以用<code>memoryMapIdx</code>减去<code>maxSubpageAllocs</code>就能够得到一个索引，表示是第几个
     * 这样的存储空间。
     * </p>
     *
     * <p>
     * 每一个subpage都会被构建成一个{@link PoolSubpage}对象，引用保存在{@link #subpages}中，下标与{@link #memoryMap}中表示单个page存储空间的元素的下标一一对应。
     * </p>
     *
     * @param memoryMapIdx
     * @return
     */
    private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx - maxSubpageAllocs;
    }

    /**
     * 随机生成0或者1
     * @return
     */
    private int nextRandom() {
        random = random * multiplier + addend & mask;
        return (int) (random >>> 47) & 1;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Chunk(");
        buf.append(Integer.toHexString(System.identityHashCode(this)));
        buf.append(": ");
        buf.append(usage());
        buf.append("%, ");
        buf.append(chunkSize - freeBytes);
        buf.append('/');
        buf.append(chunkSize);
        buf.append(')');
        return buf.toString();
    }

    public static void main(String... args) {
        PoolChunk<byte[]> poolChunk = new PoolChunk<byte[]>(null, null, 4, 4, 2, 4 << 4);
        System.out.println(poolChunk.allocate(2));
//        System.out.println(poolChunk.allocate(2));
    }
}
