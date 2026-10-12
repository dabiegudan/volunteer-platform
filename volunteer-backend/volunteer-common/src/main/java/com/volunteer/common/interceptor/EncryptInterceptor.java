package com.volunteer.common.interceptor;

import com.volunteer.common.annotation.Encrypted;
import com.volunteer.common.util.EncryptUtil;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * {@link Encrypted} 字段的透明加解密 MyBatis 插件。
 *
 * <h2>读写对称（这是本类存在的前提）</h2>
 * <p>只做读侧解密、不做写侧加密，会造成「写进去是明文、读出来按密文解」的静默损坏；
 * 反之只做写侧加密、读侧不认历史明文，则上线当天所有存量行读取报错。
 * 因此本插件同时具备：
 * <ul>
 *   <li><b>写侧</b>（{@link ParameterHandler#setParameters}）：把参数对象里带
 *       {@link Encrypted} 的 String 字段替换为幂等密文；</li>
 *   <li><b>读侧</b>（{@link ResultSetHandler#handleResultSets}）：把结果对象里带
 *       {@link Encrypted} 的 String 字段解回明文，历史明文原样保留。</li>
 * </ul>
 *
 * <h2>已知边界</h2>
 * <p>{@code UpdateWrapper.set("id_card", ...)} 这类「列名 + 值」的写入走的是 wrapper 内部的
 * {@code paramNameValuePairs}，无法从参数对象推断出它对应哪个实体字段，因此<b>不会被加密</b>。
 * 若要加密这类写入，必须改为 {@code updateById(entity)} 或显式调用
 * {@link EncryptUtil#encryptIfNeeded(String)}。这一点在新增加密字段时必须在评审中确认。
 */
@Intercepts({
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class}),
        @Signature(type = ParameterHandler.class, method = "setParameters",
                args = {PreparedStatement.class})
})
public class EncryptInterceptor implements org.apache.ibatis.plugin.Interceptor {

    /** 对象图遍历的最大深度，防止异常结构导致递归过深。 */
    private static final int MAX_DEPTH = 3;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();

        if (target instanceof ParameterHandler parameterHandler) {
            // 写侧：先改参数再执行 SQL
            encryptParameters(parameterHandler);
            return invocation.proceed();
        }

        // 读侧：先执行 SQL 再解密结果
        Object result = invocation.proceed();
        if (result instanceof Collection<?> collection) {
            for (Object obj : collection) {
                decryptFields(obj, 0, newIdentitySet());
            }
        } else if (result != null) {
            decryptFields(result, 0, newIdentitySet());
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 写侧
    // ------------------------------------------------------------------

    private void encryptParameters(ParameterHandler parameterHandler) {
        try {
            Object parameterObject = parameterHandler.getParameterObject();
            encryptFields(parameterObject, 0, newIdentitySet());
        } catch (RuntimeException e) {
            // 加密失败必须让写入失败，而不是把明文落库 —— 后者是静默的安全退化
            throw new IllegalStateException("敏感字段加密失败，已中止写入", e);
        }
    }

    private void encryptFields(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth > MAX_DEPTH || !visited.add(obj)) {
            return;
        }
        if (obj instanceof CharSequence || obj instanceof Number || obj instanceof Boolean) {
            return;
        }
        if (obj instanceof Collection<?> collection) {
            for (Object element : collection) {
                encryptFields(element, depth + 1, visited);
            }
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                encryptFields(value, depth + 1, visited);
            }
            return;
        }
        if (isMyBatisInternal(obj.getClass())) {
            return;
        }

        for (Field field : fieldsOf(obj.getClass())) {
            if (!field.isAnnotationPresent(Encrypted.class)) {
                continue;
            }
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (field.getType() != String.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value instanceof String text && !text.isEmpty()) {
                    field.set(obj, EncryptUtil.encryptIfNeeded(text));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("无法访问加密字段: " + field.getName(), e);
            }
        }
    }

    // ------------------------------------------------------------------
    // 读侧
    // ------------------------------------------------------------------

    private void decryptFields(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth > MAX_DEPTH || !visited.add(obj)) {
            return;
        }
        if (obj instanceof CharSequence || obj instanceof Number || obj instanceof Boolean) {
            return;
        }
        if (obj instanceof Collection<?> collection) {
            for (Object element : collection) {
                decryptFields(element, depth + 1, visited);
            }
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                decryptFields(value, depth + 1, visited);
            }
            return;
        }
        if (isMyBatisInternal(obj.getClass())) {
            return;
        }

        for (Field field : fieldsOf(obj.getClass())) {
            if (!field.isAnnotationPresent(Encrypted.class)) {
                continue;
            }
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (field.getType() != String.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value instanceof String text && !text.isEmpty()) {
                    // 宽容解密：历史明文、密钥不匹配都不会中断查询
                    field.set(obj, EncryptUtil.decryptIfNeeded(text));
                }
            } catch (Exception e) {
                // 读路径上的解密问题不应影响接口可用性，保留原值
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private Field[] fieldsOf(Class<?> clazz) {
        // 覆盖父类字段：以 BaseEntity 为父类的实体若把加密字段放在父类，也必须生效
        java.util.List<Field> all = new java.util.ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            all.addAll(java.util.Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return all.toArray(new Field[0]);
    }

    /**
     * 跳过 MyBatis / MyBatis-Plus 的内部对象。
     *
     * <p>对 {@code MapperMethod.ParamMap}、{@code Configuration}、{@code MetaObject} 等对象做
     * 反射遍历没有意义，还可能因访问受限字段抛异常。</p>
     */
    private boolean isMyBatisInternal(Class<?> clazz) {
        String name = clazz.getName();
        return name.startsWith("org.apache.ibatis.")
                || name.startsWith("com.baomidou.mybatisplus.")
                || name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jakarta.")
                || name.startsWith("sun.")
                || name.startsWith("jdk.");
    }

    private Set<Object> newIdentitySet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
