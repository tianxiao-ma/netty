package com.taobao.tianxiao;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ConcurrentTest {
    volatile int i = 0;

    private class Holder {
        volatile int barrier = 0;
        int v;
    }

    @Test
    public void accessTest() throws InterruptedException {
        final Holder h = new Holder();
        h.v = 0;

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    System.out.println(Thread.currentThread().getName() + " say:" + i);
                    try {
                        TimeUnit.MILLISECONDS.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    i += 1;
                }
            }
        });

        t1.start();
        t2.start();

        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }
}
