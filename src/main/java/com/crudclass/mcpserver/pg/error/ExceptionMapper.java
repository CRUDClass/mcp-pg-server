package com.crudclass.mcpserver.pg.error;

import com.crudclass.mcpserver.pg.enums.McpErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler that translates Java exceptions into structured
 * JSON error responses the MCP client (LLM) can interpret.
 * <p>
 * {@link McpBusinessException} → HTTP 400 with {@code {success, errorCode, message}}.
 * Unrecognized {@link Exception} → HTTP 500 with generic error body.
 *
 * @author CRUDClass
 */
@RestControllerAdvice
public class ExceptionMapper {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMapper.class);

    @ExceptionHandler(McpBusinessException.class)
    public ResponseEntity<Map<String, Object>> handleMcpBusiness(McpBusinessException ex) {
        if (log.isErrorEnabled()) {
            log.error("MCP error: code={} detail={}", ex.getErrorCode(), ex.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", ex.getErrorCode().name());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex, HttpServletRequest request) {
        if (ex instanceof AsyncRequestNotUsableException || isSseRequest(request)) {
            log.warn("Skipping error response (client disconnected or SSE transport): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("Unexpected error", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", McpErrorCode.EXECUTION_FAILED.name());
        body.put("message", "Internal error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }
}
