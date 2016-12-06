package org.cobbzilla.util.reflect;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.collection.ArrayUtil;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.string.StringUtil.uncapitalize;

/**
 * Handy tools for working quickly with reflection APIs, which tend to be verbose.
 */
@Slf4j
public class ReflectionUtil {

    public static Boolean toBoolean(Object object) {
        if (object == null) return null;
        if (object instanceof Boolean) return (Boolean) object;
        if (object instanceof String) return Boolean.valueOf(object.toString());
        return null;
    }

    public static Long toLong(Object object) {
        if (object == null) return null;
        if (object instanceof Number) return ((Number) object).longValue();
        if (object instanceof String) return Long.valueOf(object.toString());
        return null;
    }

    public static Integer toInteger(Object object) {
        if (object == null) return null;
        if (object instanceof Number) return ((Number) object).intValue();
        if (object instanceof String) return Integer.valueOf(object.toString());
        return null;
    }

    public static Short toShort(Object object) {
        if (object == null) return null;
        if (object instanceof Number) return ((Number) object).shortValue();
        if (object instanceof String) return Short.valueOf(object.toString());
        return null;
    }

    public static Float toFloat(Object object) {
        if (object == null) return null;
        if (object instanceof Number) return ((Number) object).floatValue();
        if (object instanceof String) return Float.valueOf(object.toString());
        return null;
    }

    public static Double toDouble(Object object) {
        if (object == null) return null;
        if (object instanceof Number) return ((Number) object).doubleValue();
        if (object instanceof String) return Double.valueOf(object.toString());
        return null;
    }

    public static BigDecimal toBigDecimal(Object object) {
        if (object == null) return null;
        if (object instanceof Double) return new BigDecimal((Double) object);
        if (object instanceof Float) return new BigDecimal((Float) object);
        if (object instanceof Number) return new BigDecimal(((Number) object).longValue());
        if (object instanceof String) return new BigDecimal(object.toString());
        return null;
    }

    /**
     * Do a Class.forName and only throw unchecked exceptions.
     * @param clazz full class name. May end in [] to indicate array class
     * @param <T> The class type
     * @return A Class&lt;clazz&gt; object
     */
    public static <T> Class<? extends T> forName(String clazz) {
        if (clazz.endsWith("[]")) return arrayClass(forName(clazz.substring(0, clazz.length()-2)));
        try {
            return (Class<? extends T>) Class.forName(clazz);
        } catch (Exception e) {
            return die("Class.forName("+clazz+") error: "+e, e);
        }
    }

    public static <T> Class<? extends T> arrayClass (Class clazz) { return forName("[L"+clazz.getName()+";"); }

    /**
     * Create an instance of a class, only throwing unchecked exceptions. The class must have a default constructor.
     * @param clazz we will instantiate an object of this type
     * @param <T> The class type
     * @return An Object that is an instance of Class&lt;clazz&gt; object
     */
    public static <T> T instantiate(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            return die("Error instantiating "+clazz+": "+e, e);
        }
    }

    /**
     * Create an instance of a class based on a class name, only throwing unchecked exceptions. The class must have a default constructor.
     * @param clazz full class name
     * @param <T> The class type
     * @return An Object that is an instance of Class&lt;clazz&gt; object
     */
    public static <T> T instantiate(String clazz) {
        try {
            return (T) instantiate(forName(clazz));
        } catch (Exception e) {
            return die("instantiate("+clazz+"): "+e, e);
        }
    }

    /**
     * Create an instance of a class using the supplied argument to a matching single-argument constructor.
     * @param clazz The class to instantiate
     * @param argument The object that will be passed to a matching single-argument constructor
     * @param <T> Could be anything
     * @return A new instance of clazz, created using a constructor that matched argument's class.
     */
    public static <T> T instantiate(Class<T> clazz, Object argument) {
        Constructor<T> constructor = null;
        Class<?> tryClass = argument.getClass();
        while (constructor == null) {
            try {
                constructor = clazz.getConstructor(tryClass);
            } catch (NoSuchMethodException e) {
                if (tryClass.equals(Object.class)) {
                    // try interfaces
                    for (Class<?> iface : argument.getClass().getInterfaces()) {
                        try {
                            constructor = clazz.getConstructor(iface);
                        } catch (NoSuchMethodException e2) {
                            // noop
                        }
                    }
                    break;
                } else {
                    tryClass = tryClass.getSuperclass();
                }
            }
        }
        if (constructor == null) {
            die("instantiate: no constructor could be found for class "+clazz.getName()+", argument type "+argument.getClass().getName());
        }
        try {
            return constructor.newInstance(argument);
        } catch (Exception e) {
            return die("instantiate("+clazz.getName()+", "+argument+"): "+e, e);
        }
    }

    /**
     * Create an instance of a class using the supplied argument to a matching single-argument constructor.
     * @param clazz The class to instantiate
     * @param arguments The objects that will be passed to a matching constructor
     * @param <T> Could be anything
     * @return A new instance of clazz, created using a constructor that matched argument's class.
     */
    public static <T> T instantiate(Class<T> clazz, Object... arguments) {
        try {
            for (Constructor constructor : clazz.getConstructors()) {
                final Class<?>[] cParams = constructor.getParameterTypes();
                if (cParams.length == arguments.length) {
                    boolean match = true;
                    for (int i=0; i<cParams.length; i++) {
                        if (!cParams[i].isAssignableFrom(arguments[i].getClass())) {
                            match = false; break;
                        }
                    }
                    if (match) return (T) constructor.newInstance(arguments);
                }
            }
            log.warn("instantiate("+clazz.getName()+"): no matching constructor found, trying with exact match (will probably fail), args="+ArrayUtils.toString(arguments));

            final Class<?>[] parameterTypes = new Class[arguments.length];
            for (int i=0; i<arguments.length; i++) {
                parameterTypes[i] = getSimpleClass(arguments[i]);
            }
            return clazz.getConstructor(parameterTypes).newInstance(arguments);

        } catch (Exception e) {
            return die("instantiate("+clazz.getName()+", "+Arrays.toString(arguments)+"): "+e, e);
        }
    }

    public static Class<?> getSimpleClass(Object argument) {
        Class<?> argClass = argument.getClass();
        final int enhancePos = argClass.getName().indexOf("$$Enhance");
        if (enhancePos != -1) {
            argClass = forName(argClass.getName().substring(0, enhancePos));
        }
        return argClass;
    }

    public static String getSimpleClassName(Object argument) { return getSimpleClass(argument).getClass().getSimpleName(); }

    /**
     * Make a copy of the object, assuming its class has a copy constructor
     * @param thing The thing to copy
     * @param <T> Whatevs
     * @return A copy of the object, created using the thing's copy constructor
     */
    public static <T> T copy(T thing) { return (T) instantiate(thing.getClass(), thing); }

    private enum Accessor { get, set }

    /**
     * Copies fields from src to dest. Code is easier to read if this method is understdood to be like an assignment statement, dest = src
     *
     * We consider only 'getter' methods that meet the following criteria:
     *   (1) starts with "get"
     *   (2) takes zero arguments
     *   (3) has a return value
     *   (4) does not carry any annotation whose simple class name is "Transient"
     *
     * The value returned from the source getter will be copied to the destination (via setter), if a setter exists, and:
     * (1) No getter exists on the destination, or (2) the destination's getter returns a different value (.equals returns false)
     *
     * Getters that return null values on the source object will not be copied.
     *
     * @param dest destination object
     * @param src source object
     * @param <T> objects must share a type
     * @return count of fields copied
     */
    public static <T> int copy (T dest, T src) {
        return copy(dest, src, null, null);
    }

    /**
     * Same as copy(dest, src) but only named fields are copied
     * @param dest destination object
     * @param src source object
     * @param fields only fields with these names will be considered for copying
     * @param <T> objects must share a type
     * @return count of fields copied
     */
    public static <T> int copy (T dest, T src, String[] fields) {
        int copyCount = 0;
        for (String field : fields) {
            try {
                final Object value = get(src, field);
                if (value != null) {
                    set(dest, field, value);
                    copyCount++;
                }
            } catch (Exception e) {
                log.debug("copy: field="+field+": "+e);
            }
        }
        return copyCount;
    }

    /**
     * Same as copy(dest, src) but only named fields are copied
     * @param dest destination object, or a Map<String, Object>
     * @param src source object
     * @param fields only fields with these names will be considered for copying
     * @param exclude fields with these names will NOT be considered for copying
     * @param <T> objects must share a type
     * @return count of fields copied
     */
    public static <T> int copy (T dest, T src, String[] fields, String[] exclude) {
        int copyCount = 0;
        final boolean isMap = dest instanceof Map;
        try {
            checkGetter:
            for (Method getter : src.getClass().getMethods()) {
                // only look for getters on the source object (methods with no arguments that have a return value)
                final Class<?>[] types = getter.getParameterTypes();
                if (types.length != 0) continue;
                if (getter.getReturnType().equals(Void.class)) continue;;

                // and it must be named appropriately
                final String fieldName = fieldName(getter.getName());
                if (fieldName == null || ArrayUtils.contains(exclude, fieldName)) continue;

                // if specific fields were given, it must be one of those
                if (fields != null && !ArrayUtils.contains(fields, fieldName)) continue;

                // getter must not be marked Transient
                final Annotation[] getterNotes = getter.getAnnotations();
                if (getterNotes != null) {
                    for (Annotation a : getterNotes) {
                        final Class<?>[] interfaces = a.getClass().getInterfaces();
                        if (interfaces != null) {
                            for (Class<?> i : interfaces) {
                                if (i.getSimpleName().equals("Transient")) {
                                    continue checkGetter;
                                }
                            }
                        }
                    }
                }

                // what would the setter be called?
                final String setterName = setterForGetter(getter.getName());
                if (setterName == null) continue;

                // get the setter method on the destination object
                Method setter = null;
                if (!isMap) {
                    try {
                        setter = dest.getClass().getMethod(setterName, getter.getReturnType());
                    } catch (Exception e) {
                        log.debug("copy: setter not found: " + setterName);
                        continue;
                    }
                }

                // do not copy null fields (should this be configurable?)
                final Object srcValue = getter.invoke(src);
                if (srcValue == null) continue;

                // does the dest have a getter? if so grab the current value
                Object destValue = null;
                try {
                    if (isMap) {
                        destValue = ((Map) dest).get(fieldName);
                    } else {
                        destValue = getter.invoke(dest);
                    }
                } catch (Exception e) {
                    log.debug("copy: error calling getter on dest: "+e);
                }

                // copy the value from src to dest, if it's different
                if (!srcValue.equals(destValue)) {
                    if (isMap) {
                        ((Map) dest).put(fieldName, srcValue);
                    } else {
                        setter.invoke(dest, srcValue);
                    }
                    copyCount++;
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error copying "+dest.getClass().getSimpleName()+" from src="+src+": "+e, e);
        }
        return copyCount;
    }

    public static String fieldName(String method) {
        if (method.startsWith("get")) return uncapitalize(method.substring(3));
        if (method.startsWith("set")) return uncapitalize(method.substring(3));
        if (method.startsWith("is")) return uncapitalize(method.substring(2));
        return null;
    }

    public static String setterForGetter(String getter) {
        if (getter.startsWith("get")) return "set"+getter.substring(3);
        if (getter.startsWith("is")) return "set"+getter.substring(2);
        return null;
    }

    /**
     * Call setters on an object based on keys and values in a Map
     * @param dest destination object
     * @param src map of field name -> value
     * @param <T> type of object
     * @return the destination object
     */
    public static <T> T copyFromMap (T dest, Map<String, Object> src) {
        for (Map.Entry<String, Object> entry : src.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            if (value != null && Map.class.isAssignableFrom(value.getClass())) {
                if (hasGetter(dest, key)) {
                    Map m = (Map) value;
                    if (m.isEmpty()) continue;
                    if (m.keySet().iterator().next().getClass().equals(String.class)) {
                        copyFromMap(get(dest, key), (Map<String, Object>) m);
                    } else {
                        log.info("copyFromMap: not recursively copying Map (has non-String keys): " + key);
                    }
                }
            } else {
                if (Map.class.isAssignableFrom(dest.getClass())) {// || dest.getClass().getName().equals(HashMap.class.getName())) {
                    ((Map) dest).put(key, value);
                } else {
                    if (hasSetter(dest, key, value.getClass())) {
                        set(dest, key, value);
                    }
                }
            }
        }
        return dest;
    }

    public static final String[] TO_MAP_STANDARD_EXCLUDES = {"declaringClass", "class"};

    /**
     * Make a copy of the object, assuming its class has a copy constructor
     * @param thing The thing to copy
     * @return A copy of the object, created using the thing's copy constructor
     */
    public static Map<String, Object> toMap(Object thing) { return toMap(thing, null, TO_MAP_STANDARD_EXCLUDES); }

    public static Map<String, Object> toMap(Object thing, String[] fields) { return toMap(thing, fields, TO_MAP_STANDARD_EXCLUDES); }

    public static Map<String, Object> toMap(Object thing, String[] fields, String[] exclude) {
        final Map<String, Object> map = new HashMap<>();
        copy(map, thing, fields, exclude);
        return map;
    }

    /**
     * Find the concrete class for the first declared parameterized class variable
     * @param clazz The class to search for parameterized types
     * @return The first concrete class for a parameterized type found in clazz
     */
    public static Class getFirstTypeParam(Class clazz) { return getTypeParam(clazz, 0); }

    public static Class getTypeParam(Class clazz, int index) {
        // todo: add a cache on this thing... could do wonders
        Class check = clazz;
        while (check.getGenericSuperclass() == null || !(check.getGenericSuperclass() instanceof ParameterizedType)) {
            check = check.getSuperclass();
            if (check.equals(Object.class)) die("getTypeParam("+clazz.getName()+"): no type parameters found");
        }
        final ParameterizedType parameterizedType = (ParameterizedType) check.getGenericSuperclass();
        final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (index >= actualTypeArguments.length) die("getTypeParam("+clazz.getName()+"): "+actualTypeArguments.length+" type parameters found, index "+index+" out of bounds");
        return (Class) actualTypeArguments[index];
    }

    /**
     * Find the concrete class for a parameterized class variable.
     * @param clazz The class to start searching. Search will continue up through superclasses
     * @param impl The type (or a supertype) of the parameterized class variable
     * @return The first concrete class found that is assignable to an instance of impl
     */
    public static Class getFirstTypeParam(Class clazz, Class impl) {
        // todo: add a cache on this thing... could do wonders
        Class check = clazz;
        while (check != null && !check.equals(Object.class)) {
            Class superCheck = check;
            Type superType = superCheck.getGenericSuperclass();
            while (superType != null || !superType.equals(Object.class)) {
                if (superType instanceof ParameterizedType) {
                    final ParameterizedType ptype = (ParameterizedType) superType;
                    final Class<?> rawType = (Class<?>) ptype.getRawType();
                    if (impl.isAssignableFrom(rawType)) {
                        return rawType;
                    }
                    for (Type t : ptype.getActualTypeArguments()) {
                        if (impl.isAssignableFrom((Class<?>) t)) {
                            return (Class<?>) t;
                        }
                    }

                } else if (superType instanceof Class) {
                    superType = ((Class) superType).getGenericSuperclass();
                }
            }
            check = check.getSuperclass();
        }
        return null;
    }

    /**
     * Call a getter. getXXX and isXXX will both be checked.
     * @param object the object to call get(field) on
     * @param field the field name
     * @return the value of the field
     * @throws IllegalArgumentException If no getter for the field exists
     */
    public static Object get(Object object, String field) {
        Object target = object;
        for (String token : field.split("\\.")) {
            if (target == null) return null;
            target = invoke_get(target, token);
        }
        return target;
    }

    public static <T> T get(Object object, String field, T defaultValue) {
        try {
            final Object val = get(object, field);
            return val == null ? defaultValue : (T) val;
        } catch (Exception e) {
            log.warn("get: "+e);
            return defaultValue;
        }
    }

    public static boolean hasGetter(Object object, String field) {
        Object target = object;
        try {
            for (String token : field.split("\\.")) {
                final String methodName = getAccessorMethodName(Accessor.get, token);
                target = MethodUtils.invokeExactMethod(target, methodName, null);
            }
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Call a setter
     * @param object the object to call set(field) on
     * @param field the field name
     * @param value the value to set
     */
    public static void set(Object object, String field, Object value) {
        final String[] tokens = field.split("\\.");
        Object target = getTarget(object, tokens);
        if (target != null) invoke_set(target, tokens[tokens.length - 1], value);
    }

    public static void setNull(Object object, String field, Class type) {
        final String[] tokens = field.split("\\.");
        Object target = getTarget(object, tokens);
        if (target != null) invoke_set_null(target, tokens[tokens.length - 1], type);
    }

    private static Object getTarget(Object object, String[] tokens) {
        Object target = object;
        for (int i=0; i<tokens.length-1; i++) {
            target = invoke_get(target, tokens[i]);
            if (target == null) {
                log.warn("getTarget("+object+", "+Arrays.toString(tokens)+"): exiting early, null object found at token="+tokens[i]);
                return null;
            }
        }
        return target;
    }

    public static boolean hasSetter(Object object, String field, Class type) {
        Object target = object;
        final String[] tokens = field.split("\\.");
        try {
            for (int i=0; i<tokens.length-1; i++) {
                target = MethodUtils.invokeExactMethod(target, tokens[i], null);
            }

            target.getClass().getMethod(getAccessorMethodName(Accessor.set, tokens[tokens.length-1]), type);

        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static String getAccessorMethodName(Accessor accessor, String token) {
        return token.length() == 1 ? accessor.name() +token.toUpperCase() : accessor.name() + token.substring(0, 1).toUpperCase() + token.substring(1);
    }

    private static Object invoke_get(Object target, String token) {
        final String methodName = getAccessorMethodName(Accessor.get, token);
        try {
            target = MethodUtils.invokeMethod(target, methodName, null);
        } catch (Exception e) {
            final String isMethod = methodName.replaceFirst("get", "is");
            try {
                target = MethodUtils.invokeMethod(target, isMethod, null);
            } catch (Exception e2) {
                return die("Error calling "+methodName+" and "+isMethod+": "+e+", "+e2);
            }
        }
        return target;
    }

    private static void invoke_set(Object target, String token, Object value) {
        final String methodName = getAccessorMethodName(Accessor.set, token);
        if (value == null) {
            // try to find a single-arg method named methodName...
            Method found = null;
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterTypes().length == 1) {
                    if (found != null) {
                        die("invoke_set: value was null and multiple single-arg methods named " + methodName + " exist");
                    } else {
                        found = m;
                    }
                }
            }
            if (found == null) die("invoke_set: no method " + methodName + " found on target: " + target);
            try {
                found.invoke(target, ArrayUtil.SINGLE_NULL_OBJECT);
            } catch (Exception e) {
                die("Error calling " + methodName + ": " + e);
            }
        } else {
            try {
                MethodUtils.invokeMethod(target, methodName, value);
            } catch (Exception e) {
                die("Error calling " + methodName + ": " + e);
            }
        }
    }

    private static void invoke_set_null(Object target, String token, Class type) {
        final String methodName = getAccessorMethodName(Accessor.set, token);
        try {
            MethodUtils.invokeMethod(target, methodName, new Object[] { null }, new Class[] { type });
        } catch (Exception e) {
            die("Error calling "+methodName+": "+e);
        }
    }

    // methods below forked from dropwizard-- https://github.com/codahale/dropwizard

    /**
     * Finds the type parameter for the given class.
     *
     * @param klass    a parameterized class
     * @return the class's type parameter
     */
    public static Class<?> getTypeParameter(Class<?> klass) {
        return getTypeParameter(klass, Object.class);
    }

    /**
     * Finds the type parameter for the given class which is assignable to the bound class.
     *
     * @param klass    a parameterized class
     * @param bound    the type bound
     * @param <T>      the type bound
     * @return the class's type parameter
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getTypeParameter(Class<?> klass, Class<? super T> bound) {
        Type t = checkNotNull(klass);
        while (t instanceof Class<?>) {
            t = ((Class<?>) t).getGenericSuperclass();
        }
        /* This is not guaranteed to work for all cases with convoluted piping
         * of type parameters: but it can at least resolve straight-forward
         * extension with single type parameter (as per [Issue-89]).
         * And when it fails to do that, will indicate with specific exception.
         */
        if (t instanceof ParameterizedType) {
            // should typically have one of type parameters (first one) that matches:
            for (Type param : ((ParameterizedType) t).getActualTypeArguments()) {
                if (param instanceof Class<?>) {
                    final Class<T> cls = determineClass(bound, param);
                    if (cls != null) { return cls; }
                }
                else if (param instanceof TypeVariable) {
                    for (Type paramBound : ((TypeVariable<?>) param).getBounds()) {
                        if (paramBound instanceof Class<?>) {
                            final Class<T> cls = determineClass(bound, paramBound);
                            if (cls != null) { return cls; }
                        }
                    }
                }
            }
        }
        return die("Cannot figure out type parameterization for " + klass.getName());
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> determineClass(Class<? super T> bound, Type candidate) {
        if (candidate instanceof Class<?>) {
            final Class<?> cls = (Class<?>) candidate;
            if (bound.isAssignableFrom(cls)) {
                return (Class<T>) cls;
            }
        }

        return null;
    }

    public static void close(Object o) throws Exception {
        if (o == null) return;
        if (o instanceof Closeable) {
            ((Closeable) o).close();

        } else {
            final Method closeMethod = o.getClass().getMethod("close", (Class<?>[]) null);
            if (closeMethod == null) die("no close method found on " + o.getClass().getName());
            closeMethod.invoke(o);
        }
    }

    public static void closeQuietly(Object o) {
        if (o == null) return;
        try {
            close(o);
        } catch (Exception e) {
            log.warn("close: error closing: "+e);
        }
    }

    @NoArgsConstructor @AllArgsConstructor
    public static class Setter<T> {
        @Getter protected String field;
        @Getter protected String value;
        public void set(T data) { ReflectionUtil.set(data, field, value); }
        @Override public String toString() { return getClass().getName() + '{' + field + ", " + value + '}'; }
    }

    // adapted from https://stackoverflow.com/a/2924426/1251543
    private static class CallerInspector extends SecurityManager {
        public String getCallerClassName() { return getClassContext()[2].getName(); }
    }
    private final static CallerInspector callerInspector = new CallerInspector();

    public static String callerClassName() { return callerInspector.getCallerClassName(); }
    public static String callerMethodName() { return new Throwable().getStackTrace()[2].getMethodName(); }

    public static String caller () {
        final StackTraceElement[] t = new Throwable().getStackTrace();
        if (t == null || t.length == 0) return "NO STACK TRACE!";
        return caller(t[Math.max(t.length-1, 2)]);
    }

    public static String caller(StackTraceElement s) { return s.getClassName() + "." + s.getMethodName() + ":" + s.getLineNumber(); }

}
