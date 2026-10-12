package com.volunteer.common.aspect;

import com.volunteer.common.annotation.Log;
import com.volunteer.common.entity.SysOperationLog;
import com.volunteer.common.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * #15 操作审计日志 - AOP切面
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {

    private final SysOperationLogMapper operationLogMapper;

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        SysOperationLog opLog = new SysOperationLog();
        opLog.setOpType(logAnnotation.opType());
        opLog.setOpModule(logAnnotation.opModule());
        opLog.setOpContent(logAnnotation.opContent());
        opLog.setOpTime(LocalDateTime.now());

        try {
            // 获取当前用户信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                Long userId = (Long) request.getAttribute("userId");
                if (userId != null) {
                    opLog.setOperatorId(userId);
                }
                opLog.setIpAddress(getClientIp(request));
            }

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String methodName = signature.getMethod().getName();
            String className = signature.getDeclaringTypeName();
            if (opLog.getOpContent() == null || opLog.getOpContent().isEmpty()) {
                opLog.setOpContent(className + "." + methodName);
            }

            Object result = joinPoint.proceed();
            opLog.setResult("SUCCESS");
            operationLogMapper.insert(opLog);
            return result;
        } catch (Exception e) {
            opLog.setResult("FAILURE");
            opLog.setErrorMsg(e.getMessage());
            try {
                operationLogMapper.insert(opLog);
            } catch (Exception ex) {
                log.warn("保存操作日志失败: {}", ex.getMessage());
            }
            throw e;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}