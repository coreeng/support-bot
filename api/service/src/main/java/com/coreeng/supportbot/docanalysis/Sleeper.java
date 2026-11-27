package com.coreeng.supportbot.docanalysis;

import java.time.Duration;

@FunctionalInterface
interface Sleeper {

    Sleeper THREAD = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
