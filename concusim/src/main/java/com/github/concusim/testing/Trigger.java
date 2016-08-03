package com.github.concusim.testing;

final class Trigger {
    private volatile boolean fired;

    void fire() {
        if (fired)
            return;

        synchronized (this) {
            fired = true;
            notify();
        }
    }

    boolean await(long timeout) throws InterruptedException {
        boolean ret = true;

        if (!fired) {
            synchronized (this) {
                if (!fired)
                    wait(timeout);

                ret = fired;
            }
        }

        fired = false;

        return ret;
    }
}
