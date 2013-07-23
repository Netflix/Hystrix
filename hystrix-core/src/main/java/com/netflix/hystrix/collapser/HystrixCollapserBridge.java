package com.netflix.hystrix.collapser;

import java.util.Collection;

import rx.Observable;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.HystrixCollapserKey;

/**
 * Bridge between HystrixCollapser and RequestCollapser to expose 'protected' and 'private' functionality across packages.
 * 
 * @param <BatchReturnType>
 * @param <ResponseType>
 * @param <RequestArgumentType>
 */
public interface HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> {

    public Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shardRequests(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    public Observable<BatchReturnType> createObservableCommand(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    public void mapResponseToRequests(BatchReturnType batchResponse, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    public HystrixCollapserKey getCollapserKey();

}
