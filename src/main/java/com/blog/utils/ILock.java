package com.blog.utils;

public interface ILock {
    boolean tryLock(long timeoutSec);
    void unlock();
}
