package com.medai.audit;

import com.medai.audit.service.AuditService;
import com.medai.auth.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records an audit entry for every controller call.
 *
 * <p>Deliberately an aspect rather than explicit calls. {@code AuditService} was fully implemented
 * and had zero call sites — an audit trail that depends on remembering to log is an audit trail
 * that is empty when it matters. Coverage here is structural: a new endpoint is audited the moment
 * it exists, without anyone choosing to.
 *
 * <p>Both reads and writes are recorded. Under HIPAA §164.312(b) and the DPDP Act, who <em>looked
 * at</em> a patient record is as much of an audit event as who changed it.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @Around("within(com.medai..controller..*) && execution(public * *(..))")
    public Object auditControllerCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result;
        String outcome = "SUCCESS";
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            outcome = "DENIED_OR_FAILED";
            throw t;
        } finally {
            try {
                write(joinPoint, outcome);
            } catch (Exception e) {
                log.error("Could not assemble audit entry for {}: {}",
                        joinPoint.getSignature().toShortString(), e.getMessage());
            }
        }
    }

    private void write(ProceedingJoinPoint joinPoint, String outcome) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            // Unauthenticated calls (login, tenant registration) have no tenant to file the entry
            // under. AuthService logs those separately; audit_logs.tenant_id is NOT NULL.
            return;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String controller = signature.getDeclaringType().getSimpleName().replace("Controller", "");
        HttpServletRequest request = currentRequest();

        Map<String, Object> details = new HashMap<>();
        details.put("method", signature.getName());
        details.put("outcome", outcome);
        if (request != null) {
            details.put("http", request.getMethod());
            details.put("path", request.getRequestURI());
        }

        auditService.record(
                principal.tenantId(),
                principal.userId(),
                action(request, signature.getName()),
                controller,
                firstUuidArgument(joinPoint.getArgs()),
                details,
                request != null ? clientIp(request) : null,
                request != null ? request.getHeader("User-Agent") : null);
    }

    /** e.g. {@code GET_downloadFile}, so the action reads usefully in a log review. */
    private String action(HttpServletRequest request, String methodName) {
        String verb = request != null ? request.getMethod() : "CALL";
        return verb + "_" + methodName;
    }

    /**
     * The entity the call was about, as far as the signature reveals. Path variables are declared
     * before body parameters, so the first UUID is the most specific identifier available.
     */
    private UUID firstUuidArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID id) {
                return id;
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Left-most entry is the original client; the rest are proxies.
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private UserPrincipal currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }
}
