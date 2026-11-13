package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis 会为 SELECT 的每一行构造结果对象。如果注解语句没有显式结果类型，
 * 方法返回值就是结果类型；返回值写成 void 时 MyBatis 会去构造 void 对象并抛出
 * “No constructor found in void matching ...”。这里统一守住这个约束。
 */
class MapperSelectResultTypeTest {
    private static final String MAPPER_PACKAGE = "com.ghostchu.btn.sparkle.mapper";

    @Test
    void selectStatementsMustNotUseVoidAsResultType() throws Exception {
        Configuration configuration = new Configuration();
        List<Class<?>> mappers = mapperInterfaces();
        assertFalse(mappers.isEmpty(), "没有扫描到任何 Mapper 接口");
        mappers.forEach(configuration::addMapper);

        List<String> offenders = new ArrayList<>();
        for (Class<?> mapper : mappers) {
            for (Method method : mapper.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Select.class)) {
                    continue;
                }
                MappedStatement statement = configuration.getMappedStatement(
                        mapper.getName() + "." + method.getName()
                );
                for (ResultMap resultMap : statement.getResultMaps()) {
                    Class<?> type = resultMap.getType();
                    if (type == void.class || type == Void.class) {
                        offenders.add(statement.getId());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "SELECT 不能返回 void，否则 MyBatis 构造结果对象时会失败：" + offenders);
    }

    private List<Class<?>> mapperInterfaces() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + MAPPER_PACKAGE.replace('.', '/') + "/*.class");
        List<Class<?>> mappers = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.contains("$") || !filename.endsWith(".class")) {
                continue;
            }
            String className = MAPPER_PACKAGE + "."
                    + filename.substring(0, filename.length() - ".class".length());
            Class<?> type = Class.forName(className, false, getClass().getClassLoader());
            if (type.isInterface()) {
                mappers.add(type);
            }
        }
        return mappers;
    }
}
