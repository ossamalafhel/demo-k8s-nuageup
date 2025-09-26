package com.bankcore.aspect;

import com.bankcore.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Aspect-Oriented Programming for comprehensive audit logging
 * Automatically captures method executions, security events, and performance metrics
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Pointcut for all controller methods
     */
    @Pointcut("execution(* com.bankcore.controller..*.*(..))")
    public void controllerMethods() {}

    /**
     * Pointcut for all service methods that modify data
     */
    @Pointcut("execution(* com.bankcore.service..*.create*(..)) || " +
              "execution(* com.bankcore.service..*.update*(..)) || " +
              "execution(* com.bankcore.service..*.delete*(..))")
    public void dataModifyingMethods() {}

    /**
     * Pointcut for security-sensitive operations
     */
    @Pointcut("@annotation(org.springframework.security.access.prepost.PreAuthorize)")
    public void securitySensitiveMethods() {}

    /**
     * Around advice for controller methods - captures request/response
     */
    @Around("controllerMethods()")
    @Timed("audit.controller.execution")
    public Object auditControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = generateTraceId();
        MDC.put("traceId", traceId);
        
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIP(request);
        String userAgent = getUserAgent(request);
        String userId = getCurrentUserId();

        try {
            log.info("AUDIT: Controller method execution started - {}#{}, user: {}, ip: {}", 
                className, methodName, userId, clientIp);

            // Record method entry
            auditService.recordMethodEntry(
                className + "#" + methodName,
                Arrays.toString(args),
                userId,
                clientIp,
                userAgent,
                traceId
            );

            // Execute the actual method
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("AUDIT: Controller method execution completed - {}#{}, duration: {}ms, user: {}", 
                className, methodName, executionTime, userId);

            // Record successful execution
            auditService.recordMethodSuccess(
                className + "#" + methodName,
                objectMapper.writeValueAsString(result),
                executionTime,
                userId,
                traceId
            );

            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.error("AUDIT: Controller method execution failed - {}#{}, duration: {}ms, error: {}", 
                className, methodName, executionTime, e.getMessage());

            // Record failed execution
            auditService.recordMethodFailure(
                className + "#" + methodName,
                e.getMessage(),
                executionTime,
                userId,
                traceId
            );

            throw e;
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * Before advice for data-modifying methods
     */
    @Before("dataModifyingMethods()")
    public void auditDataModification(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        String userId = getCurrentUserId();
        String clientIp = getClientIP(getCurrentRequest());

        log.info("AUDIT: Data modification attempt - {}#{}, user: {}, args: {}", 
            className, methodName, userId, Arrays.toString(args));

        auditService.recordDataModificationAttempt(
            className + "#" + methodName,
            Arrays.toString(args),
            userId,
            clientIp,
            LocalDateTime.now()
        );
    }

    /**
     * AfterReturning advice for successful data modifications
     */
    @AfterReturning(pointcut = "dataModifyingMethods()", returning = "result")
    public void auditSuccessfulDataModification(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String userId = getCurrentUserId();

        try {
            String resultJson = objectMapper.writeValueAsString(result);
            
            log.info("AUDIT: Data modification successful - {}#{}, user: {}", 
                className, methodName, userId);

            auditService.recordDataModificationSuccess(
                className + "#" + methodName,
                resultJson,
                userId,
                LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to serialize audit result for {}#{}: {}", 
                className, methodName, e.getMessage());
        }
    }

    /**
     * AfterThrowing advice for failed data modifications
     */
    @AfterThrowing(pointcut = "dataModifyingMethods()", throwing = "exception")
    public void auditFailedDataModification(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String userId = getCurrentUserId();

        log.error("AUDIT: Data modification failed - {}#{}, user: {}, error: {}", 
            className, methodName, userId, exception.getMessage());

        auditService.recordDataModificationFailure(
            className + "#" + methodName,
            exception.getMessage(),
            exception.getClass().getSimpleName(),
            userId,
            LocalDateTime.now()
        );

        // Trigger security alert for suspicious failures
        if (isSuspiciousFailure(exception)) {
            auditService.recordSecurityAlert(
                "SUSPICIOUS_DATA_MODIFICATION_FAILURE",
                String.format("Multiple failed attempts by user %s on %s#%s", 
                    userId, className, methodName),
                userId,
                getClientIP(getCurrentRequest()),
                "HIGH"
            );
        }
    }

    /**
     * Around advice for security-sensitive methods
     */
    @Around("securitySensitiveMethods()")
    @Timed("audit.security.check")
    public Object auditSecuritySensitiveMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String userId = getCurrentUserId();
        String clientIp = getClientIP(getCurrentRequest());
        
        long startTime = System.currentTimeMillis();

        // Record security check attempt
        log.info("AUDIT: Security-sensitive method access - {}#{}, user: {}, ip: {}", 
            className, methodName, userId, clientIp);

        auditService.recordSecurityCheck(
            className + "#" + methodName,
            userId,
            clientIp,
            "ACCESS_ATTEMPT",
            LocalDateTime.now()
        );

        try {
            // Check if user has required permissions
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                auditService.recordSecurityViolation(
                    "UNAUTHORIZED_ACCESS",
                    className + "#" + methodName,
                    userId,
                    clientIp,
                    "User not authenticated"
                );
                throw new SecurityException("User not authenticated");
            }

            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Record successful access
            auditService.recordSecurityCheck(
                className + "#" + methodName,
                userId,
                clientIp,
                "ACCESS_GRANTED",
                LocalDateTime.now()
            );

            log.info("AUDIT: Security-sensitive method access granted - {}#{}, user: {}, duration: {}ms", 
                className, methodName, userId, executionTime);

            return result;

        } catch (SecurityException e) {
            // Record security violation
            auditService.recordSecurityViolation(
                "ACCESS_DENIED",
                className + "#" + methodName,
                userId,
                clientIp,
                e.getMessage()
            );

            log.warn("AUDIT: Security-sensitive method access denied - {}#{}, user: {}, reason: {}", 
                className, methodName, userId, e.getMessage());

            throw e;
        }
    }

    // Helper methods

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String getClientIP(HttpServletRequest request) {
        if (request == null) return "unknown";
        
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        if (request == null) return "unknown";
        return request.getHeader("User-Agent");
    }

    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not get current user: {}", e.getMessage());
        }
        return "system";
    }

    private boolean isSuspiciousFailure(Throwable exception) {
        // Define patterns that indicate suspicious activity
        return exception instanceof SecurityException ||
               exception instanceof IllegalArgumentException ||
               exception.getMessage().toLowerCase().contains("injection") ||
               exception.getMessage().toLowerCase().contains("unauthorized");
    }
}