package com.sayana.dojo.exec;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.sayana.dojo.config.DojoProperties;

/**
 * 子JVMを同時にいくつ動かすかの上限。PCが重くならないように JShell と採点で共有する。
 */
@Component
public class ExecutionGate {

    private final Semaphore semaphore;

    public ExecutionGate(DojoProperties props) {
        this.semaphore = new Semaphore(Math.max(1, props.getExec().getMaxConcurrent()), true);
    }

    /** 枠が空くのを最大 waitSeconds 秒待つ。取れなければ false。 */
    public boolean acquire(long waitSeconds) {
        try {
            return semaphore.tryAcquire(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release() {
        semaphore.release();
    }
}
