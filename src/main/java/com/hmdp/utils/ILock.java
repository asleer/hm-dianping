package com.hmdp.utils;

/**
 * @author asleer
 * @description 用redis实现分布式锁
 * @date 2022/11/4 21:06
 */

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，到期后自动释放
     * @return true代表获取锁成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
