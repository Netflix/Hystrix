/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.utils;


import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.exception.FallbackDefinitionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import rx.Completable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.flattenTypeVariables;
import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.isGenericReturnType;
import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.isParametrizedType;
import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.isReturnTypeParametrized;
import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.isTypeVariable;
import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.isWildcardType;

public class FallbackMethod {


    private final Method method;
    private final boolean extended;
    private final boolean defaultFallback;
    private ExecutionType executionType;

    public static final FallbackMethod ABSENT = new FallbackMethod(null, false, false);

    public FallbackMethod(Method method) {
        this(method, false, false);
    }

    public FallbackMethod(Method method, boolean extended, boolean defaultFallback) {
        this.method = method;
        this.extended = extended;
        this.defaultFallback = defaultFallback;
        if (method != null) {
            this.executionType = ExecutionType.getExecutionType(method.getReturnType());
        }
    }

    public boolean isCommand() {
        return method.isAnnotationPresent(HystrixCommand.class);
    }

    public boolean isPresent() {
        return method != null;
    }

    public Method getMethod() {
        return method;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public boolean isExtended() {
        return extended;
    }

    public boolean isDefault() {
        return defaultFallback;
    }

    public void validateReturnType(Method commandMethod) throws FallbackDefinitionException {
        if (isPresent()) {
            Class<?> commandReturnType = commandMethod.getReturnType();
            if (ExecutionType.OBSERVABLE == ExecutionType.getExecutionType(commandReturnType)) {
                if (ExecutionType.OBSERVABLE != getExecutionType()) {
                    Type commandParametrizedType = commandMethod.getGenericReturnType();

                    // basically any object can be wrapped into Completable, Completable itself ins't parametrized
                    if(Completable.class.isAssignableFrom(commandMethod.getReturnType())) {
                        validateCompletableReturnType(commandMethod, method.getReturnType());
                        return;
                    }

                    if (isReturnTypeParametrized(commandMethod)) {
                        commandParametrizedType = getFirstParametrizedType(commandMethod);
                    }
                    validateParametrizedType(commandParametrizedType, method.getGenericReturnType(), commandMethod, method);
                } else {
                    validateReturnType(commandMethod, method);
                }


            } else if (ExecutionType.ASYNCHRONOUS == ExecutionType.getExecutionType(commandReturnType)) {
                if (isCommand() && ExecutionType.ASYNCHRONOUS == getExecutionType()) {
                    validateReturnType(commandMethod, method);
                }
                if (ExecutionType.ASYNCHRONOUS != getExecutionType()) {
                    Type commandParametrizedType = commandMethod.getGenericReturnType();
                    if (isReturnTypeParametrized(commandMethod)) {
                        commandParametrizedType = getFirstParametrizedType(commandMethod);
                    }
                    validateParametrizedType(commandParametrizedType, method.getGenericReturnType(), commandMethod, method);
                }
                if (!isCommand() && ExecutionType.ASYNCHRONOUS == getExecutionType()) {
                    throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, "fallback cannot return Future if the fallback isn't command when the command is async."));
                }
            } else {
                if (ExecutionType.ASYNCHRONOUS == getExecutionType()) {
                    throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, "fallback cannot return Future if command isn't asynchronous."));
                }
                if (ExecutionType.OBSERVABLE == getExecutionType()) {
                    throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, "fallback cannot return Observable if command isn't observable."));
                }
                validateReturnType(commandMethod, method);
            }

        }
    }

    private Type getFirstParametrizedType(Method m) {
        Type gtype = m.getGenericReturnType();
        if (gtype instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) gtype;
            return pType.getActualTypeArguments()[0];
        }
        return null;
    }

    // everything can be wrapped into completable except 'void'
    private void validateCompletableReturnType(Method commandMethod, Class<?> callbackReturnType) {
        if (Void.TYPE == callbackReturnType) {
            throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, "fallback cannot return 'void' if command return type is " + Completable.class.getSimpleName()));
        }
    }

    private void validateReturnType(Method commandMethod, Method fallbackMethod) {
        if (isGenericReturnType(commandMethod)) {
            List<Type> commandParametrizedTypes = flattenTypeVariables(commandMethod.getGenericReturnType());
            List<Type> fallbackParametrizedTypes = flattenTypeVariables(fallbackMethod.getGenericReturnType());
            Result result = equalsParametrizedTypes(commandParametrizedTypes, fallbackParametrizedTypes);
            if (!result.success) {
                List<String> msg = new ArrayList<String>();
                for (Error error : result.errors) {
                    Optional<Type> parentKindOpt = getParentKind(error.commandType, commandParametrizedTypes);
                    String extraHint = "";
                    if (parentKindOpt.isPresent()) {
                        Type parentKind = parentKindOpt.get();
                        if (isParametrizedType(parentKind)) {
                            extraHint = "--> " + ((ParameterizedType) parentKind).getRawType().toString() + "<Ooops!>\n";
                        }
                    }
                    msg.add(String.format(error.reason + "\n" + extraHint + "Command type literal pos: %s; Fallback type literal pos: %s",
                            positionAsString(error.commandType, commandParametrizedTypes),
                            positionAsString(error.fallbackType, fallbackParametrizedTypes)));
                }
                throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, StringUtils.join(msg, "\n")));
            }
        }
        validatePlainReturnType(commandMethod, fallbackMethod);
    }

    private void validatePlainReturnType(Method commandMethod, Method fallbackMethod) {
        validatePlainReturnType(commandMethod.getReturnType(), fallbackMethod.getReturnType(), commandMethod, fallbackMethod);
    }

    private void validatePlainReturnType(Class<?> commandReturnType, Class<?> fallbackReturnType, Method commandMethod, Method fallbackMethod) {
        if (!commandReturnType.isAssignableFrom(fallbackReturnType)) {
            throw new FallbackDefinitionException(createErrorMsg(commandMethod, fallbackMethod, "Fallback method '"
                    + fallbackMethod + "' must return: " + commandReturnType + " or its subclass"));
        }
    }

    private void validateParametrizedType(Type commandReturnType, Type fallbackReturnType, Method commandMethod, Method fallbackMethod) {
        if (!commandReturnType.equals(fallbackReturnType)) {
            throw new FallbackDefinitionException(createErrorMsg(commandMethod, fallbackMethod, "Fallback method '"
                    + fallbackMethod + "' must return: " + commandReturnType + " or its subclass"));
        }
    }

    private String createErrorMsg(Method commandMethod, Method fallbackMethod, String hint) {
        return "Incompatible return types. \nCommand method: " + commandMethod + ";\nFallback method: " + fallbackMethod + ";\n"
                + (StringUtils.isNotBlank(hint) ? "Hint: " + hint : "");
    }

    private static final Result SUCCESS = Result.success();

    private Result equalsParametrizedTypes(List<Type> commandParametrizedTypes, List<Type> fallbackParametrizedTypes) {
        if (commandParametrizedTypes.size() != fallbackParametrizedTypes.size()) {
            return Result.failure(Collections.singletonList(
                    new Error("Different size of types variables.\n" +
                            "Command  type literals size = " + commandParametrizedTypes.size() + ": " + commandParametrizedTypes + "\n" +
                            "Fallback type literals size = " + fallbackParametrizedTypes.size() + ": " + fallbackParametrizedTypes + "\n"
                    )));
        }

        for (int i = 0; i < commandParametrizedTypes.size(); i++) {
            Type commandParametrizedType = commandParametrizedTypes.get(i);
            Type fallbackParametrizedType = fallbackParametrizedTypes.get(i);
            Result result = equals(commandParametrizedType, fallbackParametrizedType);
            if (!result.success) return result;
        }

        return SUCCESS;
    }

    // Regular Type#equals method cannot be used to compare parametrized types and type variables
    // because it compares generic declarations, see java.lang.reflect.GenericDeclaration.
    // If generic declaration is an instance of java.lang.reflect.Method then command and fallback return types have with different generic declarations which aren't the same.
    // In this case we need to compare only few type properties, such as bounds for type literal and row types for parametrized types.
    private static Result equals(Type commandType, Type fallbackType) {
        if (isParametrizedType(commandType) && isParametrizedType(fallbackType)) {
            final ParameterizedType pt1 = (ParameterizedType) commandType;
            final ParameterizedType pt2 = (ParameterizedType) fallbackType;
            Result result = regularEquals(pt1.getRawType(), pt2.getRawType());
            return result.andThen(new Supplier<Result>() {
                @Override
                public Result get() {
                    return FallbackMethod.equals(pt1.getActualTypeArguments(), pt2.getActualTypeArguments());
                }
            });
        } else if (isTypeVariable(commandType) && isTypeVariable(fallbackType)) {
            final TypeVariable tv1 = (TypeVariable) commandType;
            final TypeVariable tv2 = (TypeVariable) fallbackType;
            if (tv1.getGenericDeclaration() instanceof Method && tv2.getGenericDeclaration() instanceof Method) {
                Result result = equals(tv1.getBounds(), tv2.getBounds());
                return result.append(new Supplier<List<Error>>() {
                    @Override
                    public List<Error> get() {
                        return Collections.singletonList(boundsError(tv1, tv1.getBounds(), "", tv2, tv2.getBounds()));
                    }
                });
            }
            return regularEquals(tv1, tv2);
        } else if (isWildcardType(commandType) && isWildcardType(fallbackType)) {
            final WildcardType wt1 = (WildcardType) commandType;
            final WildcardType wt2 = (WildcardType) fallbackType;
            Result result = equals(wt1.getLowerBounds(), wt2.getLowerBounds());
            result = result.append(new Supplier<List<Error>>() {
                @Override
                public List<Error> get() {
                    return Collections.singletonList(boundsError(wt1, wt1.getLowerBounds(), "lower", wt2, wt2.getLowerBounds()));
                }
            });

            if (result.isFailure()) return result;

            result = equals(wt1.getUpperBounds(), wt2.getUpperBounds());
            return result.append(new Supplier<List<Error>>() {
                @Override
                public List<Error> get() {
                    return Collections.singletonList(boundsError(wt1, wt1.getUpperBounds(), "upper", wt2, wt2.getUpperBounds()));
                }
            });
        } else {
            return regularEquals(commandType, fallbackType);
        }
    }

    private static Result regularEquals(final Type commandType, final Type fallbackType) {
        return Result.of(Objects.equal(commandType, fallbackType), new Supplier<List<Error>>() {
            @Override
            public List<Error> get() {
                return Collections.singletonList(new Error(
                        commandType,
                        String.format("Different types. Command type: '%s'; fallback type: '%s'", commandType, fallbackType),
                        fallbackType));
            }
        });
    }

    private static Optional<Type> getParentKind(Type type, List<Type> types) {
        int pos = position(type, types);
        if (pos <= 0) return Optional.absent();
        return Optional.of(types.get(pos - 1));
    }

    private static String positionAsString(Type type, List<Type> types) {
        int pos = position(type, types);
        if (pos < 0) {
            return "unknown";
        }
        return String.valueOf(pos);
    }

    private static int position(Type type, List<Type> types) {
        if (type == null) return -1;
        if (types == null || types.isEmpty()) return -1;
        return types.indexOf(type);
    }

    private static Error boundsError(Type t1, Type[] b1, String boundType, Type t2, Type[] b2) {
        return new Error(t1,
                String.format("Different %s bounds. Command bounds: '%s'; Fallback bounds: '%s'",
                        boundType,
                        StringUtils.join(b1, ", "),
                        StringUtils.join(b2, ", ")),
                t2);
    }

    private static Result equals(Type[] t1, Type[] t2) {
        if (t1 == null && t2 == null) return SUCCESS;
        if (t1 == null) return Result.failure();
        if (t2 == null) return Result.failure();
        if (t1.length != t2.length)
            return Result.failure(new Error(String.format("Different size of type literals. Command size = %d, fallback size = %d",
                    t1.length, t2.length)));
        Result result = SUCCESS;
        for (int i = 0; i < t1.length; i++) {
            result = result.combine(equals(t1[i], t2[i]));
            if (result.isFailure()) return result;
        }
        return result;
    }

    private static class Result {
        boolean success;
        List<Error> errors = Collections.emptyList();

        boolean isSuccess() {
            return success;
        }

        boolean isFailure() {
            return !success;
        }

        static Result of(boolean res, Supplier<List<Error>> errors) {
            if (res) return success();
            return failure(errors.get());
        }

        static Result success() {
            return new Result(true);
        }

        static Result failure() {
            return new Result(false);
        }

        static Result failure(Error... errors) {
            return new Result(false, Arrays.asList(errors));
        }

        static Result failure(List<Error> errors) {
            return new Result(false, errors);
        }

        Result combine(Result r) {
            return new Result(this.success && r.success, merge(this.errors, r.errors));
        }

        Result andThen(Supplier<Result> resultSupplier) {
            if (!success) return this;
            return resultSupplier.get();
        }

        Result append(List<Error> errors) {
            if (success) return this;
            return failure(merge(this.errors, errors));
        }

        Result append(Supplier<List<Error>> errors) {
            if (success) return this;
            return append(errors.get());
        }

        static List<Error> merge(@Nonnull List<Error> e1, @Nonnull List<Error> e2) {
            List<Error> res = new ArrayList<Error>(e1.size() + e2.size());
            res.addAll(e1);
            res.addAll(e2);
            return Collections.unmodifiableList(res);
        }

        Result(boolean success, List<Error> errors) {
            Validate.notNull(errors, "errors cannot be null");
            this.success = success;
            this.errors = errors;
        }

        Result(boolean success) {
            this.success = success;
            this.errors = Collections.emptyList();
        }
    }

    private static class Error {
        @Nullable
        Type commandType;
        String reason;
        @Nullable
        Type fallbackType;

        Error(String reason) {
            this.reason = reason;
        }

        Error(Type commandType, String reason, Type fallbackType) {
            this.commandType = commandType;
            this.reason = reason;
            this.fallbackType = fallbackType;
        }
    }

}
