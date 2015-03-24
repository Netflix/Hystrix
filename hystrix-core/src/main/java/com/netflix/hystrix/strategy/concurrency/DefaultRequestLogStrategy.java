package com.netflix.hystrix.strategy.concurrency;

/**
 * Indicates that the HystrixConcurrencyStrategy does not use a custom request log,
 * therefore threads do not need to initialize the request log.
 */
public interface DefaultRequestLogStrategy {
}
