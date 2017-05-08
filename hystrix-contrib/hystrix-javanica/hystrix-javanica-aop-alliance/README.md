# hystrix-javanica-aop-alliance

## Prerequisites

Javanica for AOP Alliance depends on [MethodHandle](https://docs.oracle.com/javase/7/docs/api/java/lang/invoke/MethodHandle.html) which was introduced in Java 7 and hence require Java 7 or higher.

# How to Use

First of all, you need to add hystrix-javanica-aop-alliance dependency in your project.

Example for Maven:
```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-javanica-aop-alliance</artifactId>
    <version>x.y.z</version>
</dependency>
```

The next step is to specifcy the interception advice. An example has been given for Guice, HK2 and Spring.

## Guice
```java
Guice.createInjector(new AbstractModule() {

			@Override
			protected void configure() {
				bindInterceptor(Matchers.any(), new HystrixMethodMatcher(HystrixCommand.class), new HystrixCommandAspect());
				bindInterceptor(Matchers.any(), new HystrixMethodMatcher(HystrixCollapser.class), new HystrixCommandAspect());
				bindInterceptor(Matchers.any(), new HystrixMethodMatcher(CacheRemove.class), new HystrixCacheAspect());

			}
		}
		
public class HystrixMethodMatcher extends AbstractMatcher<Method> {

	private Class<? extends Annotation> annotationClass;

	private HystrixMethodMatcher(Class<? extends Annotation> ann) {
			this.annotationClass = ann;
	}

	@Override
	public boolean matches(final Method method) {
		return method.isAnnotationPresent(annotationClass) && !method.isSynthetic();
	}
}
```

## HK2
```java
new AbstractBinder() {

			@Override
			protected void configure() {
				bind(CommandInterceptionService.class).to(InterceptionService.class).in(Singleton.class);
				bind(CacheRemoveInterceptionService.class).to(InterceptionService.class).in(Singleton.class);
			}

		});

@Service
public class CommandInterceptionService implements InterceptionService {
	private static final List<MethodInterceptor> METHOD_LIST = Collections.<MethodInterceptor> singletonList(new HystrixCommandAspect());

	public Filter getDescriptorFilter() {
		return BuilderHelper.allFilter();
	}

	public List<MethodInterceptor> getMethodInterceptors(Method method) {
		if (method.isAnnotationPresent(HystrixCommand.class) || method.isAnnotationPresent(HystrixCollapser.class)) {
			return METHOD_LIST;
		}
		return null;
	}

	public List<ConstructorInterceptor> getConstructorInterceptors(Constructor<?> constructor) {
		return null;
	}

}

@Service
public class CacheRemoveInterceptionService implements InterceptionService {
	private static final List<MethodInterceptor> METHOD_LIST = Collections.<MethodInterceptor> singletonList( new HystrixCacheAspect());

	public Filter getDescriptorFilter() {
		return BuilderHelper.allFilter();
	}

	public List<MethodInterceptor> getMethodInterceptors(Method method) {
		if (method.isAnnotationPresent(CacheRemove.class) && !method.isAnnotationPresent(HystrixCommand.class)) {
			return METHOD_LIST;
		}
		return null;
	}

	public List<ConstructorInterceptor> getConstructorInterceptors(Constructor<?> constructor) {
		return null;
	}

}

```
## Spring
```java
@Configuration
public class HystrixConfiguration {

    @Bean
	public Advisor commandAdvisor() {
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand) || @annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser)");
		return new DefaultPointcutAdvisor(pointcut, new SpringHystrixCommandAspect());
	}

	@Bean
	public Advisor cacheAdvisor() {
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression("@annotation(com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove) && !@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)");
		return new DefaultPointcutAdvisor(pointcut, new SpringHystrixCacheAspect());
	}

}
```