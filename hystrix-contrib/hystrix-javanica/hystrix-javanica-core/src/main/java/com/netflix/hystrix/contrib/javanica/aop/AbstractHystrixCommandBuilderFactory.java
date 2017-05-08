package com.netflix.hystrix.contrib.javanica.aop;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang3.Validate;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.CommandAction;
import com.netflix.hystrix.contrib.javanica.command.CommandActions;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.GenericSetterBuilder;
import com.netflix.hystrix.contrib.javanica.command.HystrixCommandBuilder;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.contrib.javanica.utils.FallbackMethod;
import com.netflix.hystrix.contrib.javanica.utils.MethodProvider;

public abstract class AbstractHystrixCommandBuilderFactory<T extends MetaHolder<T,V>,V extends MetaHolder.Builder<T,V>> {
	private AbstractCacheInvocationContextFactory<T,V> cacheContextFactory;
	
	protected AbstractHystrixCommandBuilderFactory(AbstractCacheInvocationContextFactory<T,V> cacheContextFactory){
		this.cacheContextFactory=cacheContextFactory;
	}
	
	
	protected abstract V getBuilder();
	protected abstract CommandAction createCommandAction(T metaHolder);
	protected abstract CommandAction createFallbackAction(T metaHolder, T fallBackMetaHolder);

	
	public HystrixCommandBuilder create(T metaHolder) {
        return create(metaHolder, Collections.<HystrixCollapser.CollapsedRequest<Object, Object>>emptyList());
    }

    public <ResponseType> HystrixCommandBuilder create(T metaHolder, Collection<HystrixCollapser.CollapsedRequest<ResponseType, Object>> collapsedRequests) {
        validateMetaHolder(metaHolder);

        return HystrixCommandBuilder.builder()
                .setterBuilder(createGenericSetterBuilder(metaHolder))
                .commandActions(createCommandActions(metaHolder))
                .collapsedRequests(collapsedRequests)
                .cacheResultInvocationContext(cacheContextFactory.createCacheResultInvocationContext(metaHolder))
                .cacheRemoveInvocationContext(cacheContextFactory.createCacheRemoveInvocationContext(metaHolder))
                .ignoreExceptions(metaHolder.getCommandIgnoreExceptions())
                .executionType(metaHolder.getExecutionType())
                .build();
    }

    private void validateMetaHolder(T metaHolder) {
        Validate.notNull(metaHolder, "metaHolder is required parameter and cannot be null");
        Validate.isTrue(metaHolder.isCommandAnnotationPresent(), "hystrixCommand annotation is absent");
    }

    protected GenericSetterBuilder createGenericSetterBuilder(T metaHolder) {
        GenericSetterBuilder.Builder setterBuilder = GenericSetterBuilder.builder()
                .groupKey(metaHolder.getCommandGroupKey())
                .threadPoolKey(metaHolder.getThreadPoolKey())
                .commandKey(metaHolder.getCommandKey())
                .collapserKey(metaHolder.getCollapserKey())
                .commandProperties(metaHolder.getCommandProperties())
                .threadPoolProperties(metaHolder.getThreadPoolProperties())
                .collapserProperties(metaHolder.getCollapserProperties());
        if (metaHolder.isCollapserAnnotationPresent()) {
            setterBuilder.scope(metaHolder.getHystrixCollapser().scope());
        }
        return setterBuilder.build();
    }

    protected CommandActions createCommandActions(T metaHolder) {
        CommandAction commandAction = createCommandAction(metaHolder);
        T fallbackMetaHolder=getFallbackMetaHolder(metaHolder);
        CommandAction fallbackAction=null;
        if(fallbackMetaHolder!=null){
        	 fallbackAction = createFallbackAction(metaHolder,fallbackMetaHolder);
        }
        return CommandActions.builder().commandAction(commandAction)
                .fallbackAction(fallbackAction).build();
    }


    protected T getFallbackMetaHolder(T metaHolder) {

        FallbackMethod fallbackMethod = MethodProvider.getInstance().getFallbackMethod(metaHolder.getObjectClass(),
                metaHolder.getMethod(), metaHolder.isExtendedFallback());
        fallbackMethod.validateReturnType(metaHolder.getMethod());
        if (fallbackMethod.isPresent()) {

            Method fMethod = fallbackMethod.getMethod();
            Object[] args = fallbackMethod.isDefault() ? new Object[0] : metaHolder.getArgs();
            if (fallbackMethod.isCommand()) {
                fMethod.setAccessible(true);
                HystrixCommand hystrixCommand = fMethod.getAnnotation(HystrixCommand.class);
                V builder =  getBuilder()
                        .obj(metaHolder.getObj())
                        .method(fMethod)
                        .objectClass(metaHolder.getObjectClass())
                        .args(args)
                        .fallback(true)
                        .defaultFallback(fallbackMethod.isDefault())
                        .defaultCollapserKey(metaHolder.getDefaultCollapserKey())
                        .fallbackMethod(fMethod)
                        .extendedFallback(fallbackMethod.isExtended())
                        .fallbackExecutionType(fallbackMethod.getExecutionType())
                        .extendedParentFallback(metaHolder.isExtendedFallback())
                        .observable(ExecutionType.OBSERVABLE == fallbackMethod.getExecutionType())
                        .defaultCommandKey(fMethod.getName())
                        .defaultGroupKey(metaHolder.getDefaultGroupKey())
                        .defaultThreadPoolKey(metaHolder.getDefaultThreadPoolKey())
                        .defaultProperties(metaHolder.getDefaultProperties().orNull())
                        .hystrixCollapser(metaHolder.getHystrixCollapser())
                        .observableExecutionMode(hystrixCommand.observableExecutionMode())
                        .hystrixCommand(hystrixCommand);
                
                customizeFallBackMetaHolderBuilder(metaHolder, builder, fallbackMethod);
                return builder.build();
            } else {
                V builder = getBuilder()
                        .obj(metaHolder.getObj())
                        .objectClass(metaHolder.getObjectClass())
                        .defaultFallback(fallbackMethod.isDefault())
                        .method(fMethod)
                        .fallbackExecutionType(ExecutionType.SYNCHRONOUS)
                        .extendedFallback(fallbackMethod.isExtended())
                        .extendedParentFallback(metaHolder.isExtendedFallback())
                        .args(args);
                
                customizeFallBackMetaHolderBuilder(metaHolder, builder, fallbackMethod);
                return builder.build();
            }

        }
        return null;
        
    }
    
    protected void customizeFallBackMetaHolderBuilder(T metaHolder,V builder, FallbackMethod fallbackMethod){
    	
    }
}
