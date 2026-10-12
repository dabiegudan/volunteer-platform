package com.volunteer.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AllImplementedBackendEndpointSmokeTest {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}/:]+)(?::[^}]+)?}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Answer<Object> SAMPLE_ANSWER = invocation ->
            sampleValue(invocation.getMethod());

    @Test
    void allImplementedBackendEndpointsAreReachable() throws Exception {
        List<Class<?>> controllerClasses = scanRestControllers();
        List<Object> controllers = new ArrayList<>();
        for (Class<?> controllerClass : controllerClasses) {
            controllers.add(instantiateController(controllerClass));
        }

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controllers.toArray())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        List<EndpointCase> endpoints = describeEndpoints(controllerClasses);
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (EndpointCase endpoint : endpoints) {
            checked++;
            MockHttpServletRequestBuilder request = requestFor(endpoint);
            try {
                MvcResult result = mockMvc.perform(request).andReturn();
                int status = result.getResponse().getStatus();
                if (status == 404 || status == 405 || status >= 500) {
                    failures.add(endpoint + " -> HTTP " + status + " " + result.getResponse().getContentAsString());
                }
            } catch (Exception ex) {
                failures.add(endpoint + " -> " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }

        System.out.println("Smoke-tested backend endpoints: " + checked);
        assertThat(endpoints).isNotEmpty();
        assertThat(failures).isEmpty();
    }

    private static List<Class<?>> scanRestControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents("com.volunteer")) {
            Class<?> candidate = Class.forName(beanDefinition.getBeanClassName());
            if (AnnotationUtils.findAnnotation(candidate, RestController.class) != null) {
                classes.add(candidate);
            }
        }
        classes.sort((left, right) -> left.getName().compareTo(right.getName()));
        return classes;
    }

    private static Object instantiateController(Class<?> controllerClass) throws Exception {
        Constructor<?> constructor = Arrays.stream(controllerClass.getDeclaredConstructors())
                .max((left, right) -> Integer.compare(left.getParameterCount(), right.getParameterCount()))
                .orElseThrow();
        constructor.setAccessible(true);
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(AllImplementedBackendEndpointSmokeTest::dependencyFor)
                .toArray();
        return constructor.newInstance(args);
    }

    private static Object dependencyFor(Class<?> dependencyType) {
        if (dependencyType == ObjectMapper.class) {
            return OBJECT_MAPPER;
        }
        if (dependencyType == JdbcTemplate.class) {
            return Mockito.mock(dependencyType, Mockito.withSettings().defaultAnswer(invocation -> {
                if ("queryForObject".equals(invocation.getMethod().getName())) {
                    for (Object argument : invocation.getArguments()) {
                        if (argument instanceof Class<?> targetType) {
                            return sampleValue(targetType);
                        }
                    }
                }
                return sampleValue(invocation.getMethod());
            }));
        }
        return Mockito.mock(dependencyType, Mockito.withSettings().defaultAnswer(SAMPLE_ANSWER));
    }

    private static List<EndpointCase> describeEndpoints(List<Class<?>> controllerClasses) {
        List<EndpointCase> endpoints = new ArrayList<>();
        for (Class<?> controllerClass : controllerClasses) {
            List<String> classPaths = classPaths(controllerClass);
            for (Method method : controllerClass.getDeclaredMethods()) {
                Mapping mapping = methodMapping(method);
                if (mapping == null) {
                    continue;
                }
                for (String classPath : classPaths) {
                    for (String methodPath : mapping.paths()) {
                        for (HttpMethod httpMethod : mapping.methods()) {
                            endpoints.add(new EndpointCase(
                                    controllerClass,
                                    method,
                                    httpMethod,
                                    normalizePath(classPath, methodPath),
                                    mapping.multipart() || hasMultipartParameter(method)
                            ));
                        }
                    }
                }
            }
        }
        endpoints.sort((left, right) -> left.toString().compareTo(right.toString()));
        return endpoints;
    }

    private static List<String> classPaths(Class<?> controllerClass) {
        RequestMapping requestMapping = AnnotationUtils.findAnnotation(controllerClass, RequestMapping.class);
        if (requestMapping == null) {
            return List.of("");
        }
        String[] paths = pathsFrom(requestMapping.path(), requestMapping.value());
        return paths.length == 0 ? List.of("") : Arrays.asList(paths);
    }

    private static Mapping methodMapping(Method method) {
        GetMapping getMapping = AnnotationUtils.findAnnotation(method, GetMapping.class);
        if (getMapping != null) {
            return new Mapping(pathsFrom(getMapping.path(), getMapping.value()), Set.of(HttpMethod.GET),
                    consumesMultipart(getMapping.consumes()));
        }

        PostMapping postMapping = AnnotationUtils.findAnnotation(method, PostMapping.class);
        if (postMapping != null) {
            return new Mapping(pathsFrom(postMapping.path(), postMapping.value()), Set.of(HttpMethod.POST),
                    consumesMultipart(postMapping.consumes()));
        }

        PutMapping putMapping = AnnotationUtils.findAnnotation(method, PutMapping.class);
        if (putMapping != null) {
            return new Mapping(pathsFrom(putMapping.path(), putMapping.value()), Set.of(HttpMethod.PUT),
                    consumesMultipart(putMapping.consumes()));
        }

        DeleteMapping deleteMapping = AnnotationUtils.findAnnotation(method, DeleteMapping.class);
        if (deleteMapping != null) {
            return new Mapping(pathsFrom(deleteMapping.path(), deleteMapping.value()), Set.of(HttpMethod.DELETE),
                    consumesMultipart(deleteMapping.consumes()));
        }

        PatchMapping patchMapping = AnnotationUtils.findAnnotation(method, PatchMapping.class);
        if (patchMapping != null) {
            return new Mapping(pathsFrom(patchMapping.path(), patchMapping.value()), Set.of(HttpMethod.PATCH),
                    consumesMultipart(patchMapping.consumes()));
        }

        RequestMapping requestMapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if (requestMapping == null) {
            return null;
        }
        Set<HttpMethod> methods = new LinkedHashSet<>();
        for (RequestMethod requestMethod : requestMapping.method()) {
            methods.add(HttpMethod.valueOf(requestMethod.name()));
        }
        if (methods.isEmpty()) {
            methods.add(HttpMethod.GET);
        }
        return new Mapping(pathsFrom(requestMapping.path(), requestMapping.value()), methods,
                consumesMultipart(requestMapping.consumes()));
    }

    private static String[] pathsFrom(String[] path, String[] value) {
        if (path != null && path.length > 0) {
            return path;
        }
        if (value != null && value.length > 0) {
            return value;
        }
        return new String[]{""};
    }

    private static boolean consumesMultipart(String[] consumes) {
        return Arrays.stream(consumes == null ? new String[0] : consumes)
                .anyMatch(value -> value.toLowerCase().contains("multipart/form-data"));
    }

    private static boolean hasMultipartParameter(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .anyMatch(type -> type.getName().equals("org.springframework.web.multipart.MultipartFile"));
    }

    private static String normalizePath(String classPath, String methodPath) {
        String combined = ("/" + nullToEmpty(classPath) + "/" + nullToEmpty(methodPath))
                .replaceAll("/+", "/");
        if (combined.length() > 1 && combined.endsWith("/")) {
            combined = combined.substring(0, combined.length() - 1);
        }
        Matcher matcher = PATH_VARIABLE.matcher(combined);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, samplePathVariable(matcher.group(1)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String samplePathVariable(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("key")) {
            return "demo-key";
        }
        if (lower.contains("code")) {
            return "DEMO";
        }
        return "1";
    }

    private static MockHttpServletRequestBuilder requestFor(EndpointCase endpoint) throws Exception {
        MockHttpServletRequestBuilder request;
        if (endpoint.multipart()) {
            request = MockMvcRequestBuilders.multipart(endpoint.path())
                    .file(new MockMultipartFile("file", "sample.txt", MediaType.TEXT_PLAIN_VALUE, "sample".getBytes()));
        } else {
            request = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path());
        }

        request.accept(MediaType.APPLICATION_JSON)
                .requestAttr("userId", 1L)
                .requestAttr("currentCollegeId", 1L)
                .requestAttr("currentUser", sampleCurrentUser());
        if (!endpoint.multipart()) {
            request.contentType(MediaType.APPLICATION_JSON);
        }

        addRequestParams(endpoint.handler(), request);
        addRequestBody(endpoint, request);
        return request;
    }

    private static void addRequestParams(Method method, MockHttpServletRequestBuilder request) {
        for (Parameter parameter : method.getParameters()) {
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam == null) {
                continue;
            }
            String name = StringUtils.hasText(requestParam.name()) ? requestParam.name() : requestParam.value();
            if (!StringUtils.hasText(name)) {
                name = parameter.getName();
            }
            request.param(name, sampleRequestParam(name, parameter.getType()));
        }
    }

    private static String sampleRequestParam(String name, Class<?> type) {
        String lower = name.toLowerCase();
        if (type == Boolean.class || type == boolean.class) {
            return "true";
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return "1";
        }
        if (lower.contains("starttime")) {
            return "2026-06-09T10:00:00";
        }
        if (lower.contains("endtime")) {
            return "2026-06-09T12:00:00";
        }
        if (lower.contains("time")) {
            return "2026-06-09T10:00:00";
        }
        if (lower.contains("periodstart") || lower.contains("periodend")) {
            return "2026-06-09";
        }
        if (lower.contains("date")) {
            return "2026-06-09";
        }
        if (lower.contains("status")) {
            return "APPROVED";
        }
        if (lower.contains("type")) {
            return "SYSTEM";
        }
        if (lower.contains("objectname")) {
            return "sample.txt";
        }
        return "demo";
    }

    private static void addRequestBody(EndpointCase endpoint, MockHttpServletRequestBuilder request) throws Exception {
        Method method = endpoint.handler();
        if (endpoint.multipart()) {
            return;
        }
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getAnnotation(RequestBody.class) == null) {
                continue;
            }
            request.content(sampleBody(endpoint, parameter));
            return;
        }
        if (method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PutMapping.class) != null
                || method.getAnnotation(PatchMapping.class) != null) {
            request.content("{}");
        }
    }

    private static String sampleBody(EndpointCase endpoint, Parameter parameter) throws Exception {
        Class<?> type = parameter.getType();
        if (type == String.class) {
            return "\"demo\"";
        }
        if (Map.class.isAssignableFrom(type)) {
            return OBJECT_MAPPER.writeValueAsString(sampleMapBody(endpoint, parameter));
        }
        if (Collection.class.isAssignableFrom(type)) {
            if (endpoint.path().contains("/batch-analyze")) {
                return """
                        [
                          {
                            "text": "demo",
                            "activityCategory": "demo"
                          }
                        ]
                        """;
            }
            return "[]";
        }
        if (type.getName().endsWith("ImportConfirmRequest")) {
            return """
                    {
                      "studentNos": ["20260001"]
                    }
                    """;
        }
        Object sample = sampleValue(type);
        return OBJECT_MAPPER.writeValueAsString(sample == null ? new HashMap<>() : sample);
    }

    private static Map<String, Object> sampleMapBody(EndpointCase endpoint, Parameter parameter) {
        if (mapValueType(parameter.getParameterizedType()) == Boolean.class) {
            Map<String, Object> body = new HashMap<>();
            body.put("completed", true);
            return body;
        }
        if (mapValueType(parameter.getParameterizedType()) == String.class) {
            return sampleStringMapBody(endpoint);
        }
        if (endpoint.path().equals("/api/schedule/conflict/filter")) {
            Map<String, Object> body = new HashMap<>();
            body.put("1", List.of("2026-06-09T10:00:00", "2026-06-09T12:00:00"));
            return body;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("activityId", 1L);
        body.put("userId", 1L);
        body.put("toUserId", 2L);
        body.put("taskId", 1L);
        body.put("productId", 1L);
        body.put("amount", 1);
        body.put("hours", 1);
        body.put("durationHours", 1);
        body.put("increment", 1);
        body.put("quantity", 1);
        body.put("sourceId", 1L);
        body.put("source", "demo");
        body.put("sourceType", "demo");
        body.put("description", "demo");
        body.put("shippingAddress", "demo address");
        body.put("teamName", "demo team");
        body.put("maxMembers", 10);
        body.put("location", "demo location");
        body.put("gps", "106.5516,29.5630");
        body.put("userGps", "106.5516,29.5630");
        body.put("qrCodeToken", "demo-token");
        body.put("faceData", "demo-face");
        body.put("nfcCardId", "demo-nfc");
        body.put("bluetoothDeviceId", "demo-bluetooth");
        body.put("reason", "demo reason");
        body.put("proof", "demo proof");
        body.put("violationType", "LATE");
        body.put("violationReason", "demo reason");
        body.put("restrictionType", "ACTIVITY");
        body.put("restrictionDays", 7);
        body.put("approved", true);
        body.put("status", "APPROVED");
        body.put("comment", "ok");
        body.put("key", "demo.key");
        body.put("value", "demo");
        body.put("title", "demo title");
        body.put("content", "demo content");
        body.put("text", "demo text");
        body.put("activityName", "demo activity");
        body.put("volunteerName", "demo volunteer");
        body.put("keywords", "demo");
        body.put("activityType", "service");
        body.put("activityCategory", "service");
        body.put("serviceHours", 1);
        body.put("date", "2026-06-09");
        body.put("personalExperience", "demo experience");
        body.put("autoPolish", true);
        body.put("category", "demo category");
        body.put("source", "demo source");
        body.put("platformCode", "demo-platform");
        body.put("platformName", "demo platform");
        body.put("activityDate", "2026-06-09");
        body.put("certificateNumber", "CERT-001");
        body.put("rejectReason", "demo reason");
        body.put("days", 1);
        body.put("volunteers", List.of(Map.of("userId", 1L, "name", "demo volunteer")));
        body.put("activityInfo", Map.of("activityName", "demo activity"));
        return body;
    }

    private static Map<String, Object> sampleStringMapBody(EndpointCase endpoint) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "APPROVED");
        body.put("comment", "ok");
        body.put("reason", "ok");
        body.put("key", "demo.key");
        body.put("value", "demo");
        body.put("title", "demo title");
        body.put("content", "demo content");
        body.put("text", "demo text");
        body.put("activityName", "demo activity");
        body.put("volunteerName", "demo volunteer");
        body.put("keywords", "demo");
        body.put("activityType", "service");
        body.put("activityCategory", "service");
        body.put("date", "2026-06-09");
        body.put("personalExperience", "demo experience");
        body.put("category", "demo category");
        body.put("source", "demo source");
        if (endpoint.controllerClass() == TodoControllerClassHolder.TODO_CONTROLLER) {
            body.clear();
            body.put("title", "demo title");
        }
        return body;
    }

    private static Class<?> mapValueType(Type type) {
        if (!(type instanceof ParameterizedType mapType)) {
            return Object.class;
        }
        Type[] typeArguments = mapType.getActualTypeArguments();
        if (typeArguments.length < 2 || !(typeArguments[1] instanceof Class<?> valueType)) {
            return Object.class;
        }
        return valueType;
    }

    private static final class TodoControllerClassHolder {
        private static final Class<?> TODO_CONTROLLER = loadTodoControllerClass();

        private static Class<?> loadTodoControllerClass() {
            try {
                return Class.forName("com.volunteer.report.controller.TodoController");
            } catch (ClassNotFoundException ignored) {
                return Void.class;
            }
        }
    }

    private static SysUser sampleCurrentUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setRealName("Admin");
        user.setStudentId("20260001");
        user.setCollegeId(1L);
        user.setCollegeName("Demo College");
        user.setAccessLevel(1);
        user.setStatus(1);
        return user;
    }

    private static Object sampleValue(Method method) {
        if (Map.class.isAssignableFrom(method.getReturnType()) && mapValueType(method.getGenericReturnType()) == Double.class) {
            return Map.of("demo", 1.0D);
        }
        return sampleValue(method.getReturnType());
    }

    private static Object sampleValue(Class<?> type) {
        if (type == Void.TYPE || type == Void.class) {
            return null;
        }
        if (type == String.class) {
            return "demo";
        }
        if (type == Boolean.TYPE || type == Boolean.class) {
            return true;
        }
        if (type == Integer.TYPE || type == Integer.class) {
            return 1;
        }
        if (type == Long.TYPE || type == Long.class) {
            return 1L;
        }
        if (type == Double.TYPE || type == Double.class) {
            return 1.0D;
        }
        if (type == Float.TYPE || type == Float.class) {
            return 1.0F;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 6, 9);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2026, 6, 9, 10, 0);
        }
        if (type == Date.class) {
            return new Date();
        }
        if (type == byte[].class) {
            return "sample".getBytes();
        }
        if (type == InputStream.class) {
            return new ByteArrayInputStream("sample".getBytes());
        }
        if (Page.class.isAssignableFrom(type) || IPage.class.isAssignableFrom(type)) {
            Page<Object> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        }
        if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        if (Map.class.isAssignableFrom(type)) {
            Map<String, Object> value = new HashMap<>();
            value.put("success", true);
            value.put("message", "ok");
            return value;
        }
        if (Optional.class.isAssignableFrom(type)) {
            return Optional.empty();
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return Mockito.mock(type, Mockito.withSettings().defaultAnswer(SAMPLE_ANSWER));
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object value = constructor.newInstance();
            populateSampleBean(value);
            return value;
        } catch (Exception ignored) {
            return Mockito.mock(type, Mockito.withSettings().defaultAnswer(SAMPLE_ANSWER));
        }
    }

    private static void populateSampleBean(Object value) {
        setIfPresent(value, "setId", Long.class, 1L);
        setIfPresent(value, "setTaskId", String.class, "1");
        setIfPresent(value, "setTaskName", String.class, "demo task");
        setIfPresent(value, "setFileName", String.class, "sample.xlsx");
        setIfPresent(value, "setFileSize", Long.class, 6L);
        setIfPresent(value, "setStatus", String.class, "COMPLETED");
        setIfPresent(value, "setProgress", Integer.class, 100);
        setIfPresent(value, "setExportType", String.class, "ACTIVITY_STATS");
        setIfPresent(value, "setFormat", String.class, "EXCEL");
        setIfPresent(value, "setStartTime", String.class, "2026-06-09T10:00:00");
        setIfPresent(value, "setEndTime", String.class, "2026-06-09T12:00:00");
        setIfPresent(value, "setIncludeSummary", Boolean.class, true);
    }

    private static void setIfPresent(Object target, String methodName, Class<?> parameterType, Object argument) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            method.invoke(target, argument);
        } catch (ReflectiveOperationException ignored) {
            // Only fill optional sample fields when the bean exposes a matching setter.
        }
    }

    private record Mapping(String[] paths, Set<HttpMethod> methods, boolean multipart) {
    }

    private record EndpointCase(Class<?> controllerClass, Method handler, HttpMethod method, String path,
                                boolean multipart) {
        @Override
        public String toString() {
            return method + " " + path + " -> " + controllerClass.getSimpleName() + "." + handler.getName();
        }
    }
}
