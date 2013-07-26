package com.netflix.hystrix.collapser;

import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCollapser.Scope;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableHolder;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.util.HystrixTimer;

/**
 * Factory for retrieving the correct instance of a RequestCollapser.
 * 
 * @param <BatchReturnType>
 * @param <ResponseType>
 * @param <RequestArgumentType>
 */
public class RequestCollapserFactory<BatchReturnType, ResponseType, RequestArgumentType> {

    private static final Logger logger = LoggerFactory.getLogger(RequestCollapserFactory.class);

    private final CollapserTimer timer;
    private final HystrixCollapserKey collapserKey;
    private final HystrixCollapserProperties properties;
    private final HystrixConcurrencyStrategy concurrencyStrategy;
    private final Scope scope;

    public RequestCollapserFactory(HystrixCollapserKey collapserKey, Scope scope, CollapserTimer timer, HystrixCollapserProperties.Setter propertiesBuilder) {
        /* strategy: ConcurrencyStrategy */
        this.concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();

        this.timer = timer;
        this.scope = scope;
        this.collapserKey = collapserKey;
        this.properties = HystrixPropertiesFactory.getCollapserProperties(this.collapserKey, propertiesBuilder);
    }

    public HystrixCollapserKey getCollapserKey() {
        return collapserKey;
    }

    public Scope getScope() {
        return scope;
    }

    public HystrixCollapserProperties getProperties() {
        return properties;
    }

    public RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> getRequestCollapser(HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser) {
        if (Scope.REQUEST == getScope()) {
            return getCollapserForUserRequest(commandCollapser);
        } else if (Scope.GLOBAL == getScope()) {
            return getCollapserForGlobalScope(commandCollapser);
        } else {
            logger.warn("Invalid Scope: " + getScope() + "  Defaulting to REQUEST scope.");
            return getCollapserForUserRequest(commandCollapser);
        }
    }

    /**
     * Static global cache of RequestCollapsers for Scope.GLOBAL
     */
    // String is CollapserKey.name() (we can't use CollapserKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static ConcurrentHashMap<String, RequestCollapser<?, ?, ?>> globalScopedCollapsers = new ConcurrentHashMap<String, RequestCollapser<?, ?, ?>>();

    @SuppressWarnings("unchecked")
    private RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> getCollapserForGlobalScope(HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser) {
        RequestCollapser<?, ?, ?> collapser = globalScopedCollapsers.get(collapserKey.name());
        if (collapser != null) {
            return (RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>) collapser;
        }
        // create new collapser using 'this' first instance as the one that will get cached for future executions ('this' is stateless so we can do that)
        RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> newCollapser = new RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>(commandCollapser, properties, timer, concurrencyStrategy);
        RequestCollapser<?, ?, ?> existing = globalScopedCollapsers.putIfAbsent(collapserKey.name(), newCollapser);
        if (existing == null) {
            // we won
            return newCollapser;
        } else {
            // we lost ... another thread beat us
            // shutdown the one we created but didn't get stored
            newCollapser.shutdown();
            // return the existing one
            return (RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>) existing;
        }
    }

    /**
     * Static global cache of RequestVariables with RequestCollapsers for Scope.REQUEST
     */
    // String is HystrixCollapserKey.name() (we can't use HystrixCollapserKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static ConcurrentHashMap<String, HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>>> requestScopedCollapsers = new ConcurrentHashMap<String, HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>>>();

    /* we are casting because the Map needs to be <?, ?> but we know it is <ReturnType, RequestArgumentType> for this thread */
    @SuppressWarnings("unchecked")
    private RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> getCollapserForUserRequest(HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser) {
        return (RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>) getRequestVariableForCommand(commandCollapser).get(concurrencyStrategy);
    }

    /**
     * Lookup (or create and store) the RequestVariable for a given HystrixCollapserKey.
     * 
     * @param key
     * @return HystrixRequestVariableHolder
     */
    @SuppressWarnings("unchecked")
    private HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> getRequestVariableForCommand(final HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser) {
        HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> requestVariable = requestScopedCollapsers.get(commandCollapser.getCollapserKey().name());
        if (requestVariable == null) {
            // create new collapser using 'this' first instance as the one that will get cached for future executions ('this' is stateless so we can do that)
            @SuppressWarnings({ "rawtypes" })
            HystrixRequestVariableHolder newCollapser = new RequestCollapserRequestVariable(commandCollapser, properties, timer, concurrencyStrategy);
            HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> existing = requestScopedCollapsers.putIfAbsent(commandCollapser.getCollapserKey().name(), newCollapser);
            if (existing == null) {
                // this thread won, so return the one we just created
                requestVariable = newCollapser;
            } else {
                // another thread beat us (this should only happen when we have concurrency on the FIRST request for the life of the app for this HystrixCollapser class)
                requestVariable = existing;
                /*
                 * This *should* be okay to discard the created object without cleanup as the RequestVariable implementation
                 * should properly do lazy-initialization and only call initialValue() the first time get() is called.
                 * 
                 * If it does not correctly follow this contract then there is a chance of a memory leak here.
                 */
            }
        }
        return requestVariable;
    }

    /**
     * Clears all state. If new requests come in instances will be recreated and metrics started from scratch.
     */
    public static void reset() {
        defaultNameCache.clear();
        globalScopedCollapsers.clear();
        requestScopedCollapsers.clear();
        HystrixTimer.reset();
    }

    /**
     * Used for testing
     */
    public static void resetRequest() {
        requestScopedCollapsers.clear();
    }

    /**
     * Used for testing
     */
    public static HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> getRequestVariable(String key) {
        return requestScopedCollapsers.get(key);
    }

    /**
     * Request scoped RequestCollapser that lives inside a RequestVariable.
     * <p>
     * This depends on the RequestVariable getting reset before each user request in NFFilter to ensure the RequestCollapser is new for each user request.
     */
    private final class RequestCollapserRequestVariable extends HystrixRequestVariableHolder<RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>> {

        /**
         * NOTE: There is only 1 instance of this for the life of the app per HystrixCollapser instance. The state changes on each request via the initialValue()/get() methods.
         * <p>
         * Thus, do NOT put any instance variables in this class that are not static for all threads.
         */

        private RequestCollapserRequestVariable(final HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser, final HystrixCollapserProperties properties, final CollapserTimer timer, final HystrixConcurrencyStrategy concurrencyStrategy) {
            super(new HystrixRequestVariableLifecycle<RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>>() {
                @Override
                public RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> initialValue() {
                    // this gets calls once per request per HystrixCollapser instance
                    return new RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>(commandCollapser, properties, timer, concurrencyStrategy);
                }

                @Override
                public void shutdown(RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> currentCollapser) {
                    // shut down the RequestCollapser (the internal timer tasks)
                    if (currentCollapser != null) {
                        currentCollapser.shutdown();
                    }
                }
            });
        }

    }

    /**
     * Fluent interface for arguments to the {@link HystrixCollapser} constructor.
     * <p>
     * The required arguments are set via the 'with' factory method and optional arguments via the 'and' chained methods.
     * <p>
     * Example:
     * <pre> {@code
     *  Setter.withCollapserKey(HystrixCollapserKey.Factory.asKey("CollapserName"))
                .andScope(Scope.REQUEST);
     * } </pre>
     */
    @NotThreadSafe
    public static class Setter {
        private final HystrixCollapserKey collapserKey;
        private Scope scope = Scope.REQUEST; // default if nothing is set
        private HystrixCollapserProperties.Setter propertiesSetter;

        private Setter(HystrixCollapserKey collapserKey) {
            this.collapserKey = collapserKey;
        }

        /**
         * Setter factory method containing required values.
         * <p>
         * All optional arguments can be set via the chained methods.
         * 
         * @param collapserKey
         *            {@link HystrixCollapserKey} that identifies this collapser and provides the key used for retrieving properties, request caches, publishing metrics etc.
         * @return Setter for fluent interface via method chaining
         */
        public static Setter withCollapserKey(HystrixCollapserKey collapserKey) {
            return new Setter(collapserKey);
        }

        /**
         * {@link Scope} defining what scope the collapsing should occur within
         * 
         * @param scope
         * 
         * @return Setter for fluent interface via method chaining
         */
        public Setter andScope(Scope scope) {
            this.scope = scope;
            return this;
        }

        /**
         * @param propertiesSetter
         *            {@link HystrixCollapserProperties.Setter} that allows instance specific property overrides (which can then be overridden by dynamic properties, see
         *            {@link HystrixPropertiesStrategy} for
         *            information on order of precedence).
         *            <p>
         *            Will use defaults if left NULL.
         * @return Setter for fluent interface via method chaining
         */
        public Setter andCollapserPropertiesDefaults(HystrixCollapserProperties.Setter propertiesSetter) {
            this.propertiesSetter = propertiesSetter;
            return this;
        }

    }

    // this is a micro-optimization but saves about 1-2microseconds (on 2011 MacBook Pro) 
    // on the repetitive string processing that will occur on the same classes over and over again
    @SuppressWarnings("rawtypes")
    private static ConcurrentHashMap<Class<? extends HystrixCollapser>, String> defaultNameCache = new ConcurrentHashMap<Class<? extends HystrixCollapser>, String>();

}
