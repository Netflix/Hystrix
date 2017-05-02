package com.netflix.hystrix.contrib.javanica.command.closure;

import static org.slf4j.helpers.MessageFormatter.format;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.google.common.base.Throwables;
import com.netflix.hystrix.contrib.javanica.command.ClosureCommand;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

public abstract class BaseClosureFactory<T extends MetaHolder<T,V>, V extends MetaHolder.Builder<T,V>> implements ClosureFactory<T> {

    static final String ERROR_TYPE_MESSAGE = "return type of '{}' method should be {}.";
    static final String INVOKE_METHOD = "invoke";

    @Override
    public Closure createClosure(T metaHolder, Method method, Object o, Object... args) {
        try {       
            return createClosure(method.getName(), execute(metaHolder, method, o, args));
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e.getCause());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Creates closure.
     *
     * @param rootMethodName the name of external method within which closure is created.
     * @param closureObj     the instance of specific anonymous class
     * @return new {@link Closure} instance
     * @throws Exception
     */
   protected Closure createClosure(String rootMethodName, final Object closureObj) throws Exception {
        if (!isClosureCommand(closureObj)) {
            throw new RuntimeException(format(ERROR_TYPE_MESSAGE, rootMethodName,
                    getClosureCommandType().getName()).getMessage());
        }
        Method closureMethod = closureObj.getClass().getMethod(INVOKE_METHOD);
        return new Closure(closureMethod, closureObj);
    }
    
    protected abstract Object execute(T metaHolder, Method method, Object o, Object... args);

    /**
     * Checks that closureObj is instance of necessary class.
     *
     * @param closureObj the instance of an anonymous class
     * @return true of closureObj has expected type, otherwise - false
     */
    protected abstract boolean isClosureCommand(final Object closureObj);

    /**
     * Gets type of expected closure type.
     *
     * @return closure (anonymous class) type
     */
    protected abstract Class<? extends ClosureCommand> getClosureCommandType();
}
