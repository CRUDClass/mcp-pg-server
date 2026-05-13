package com.crudclass.mcpserver.pg.error;

import com.crudclass.mcpserver.pg.enums.McpErrorCode;

import java.io.Serial;

/**
 * Business exception that carries an {@link McpErrorCode} and locale.
 * <p>
 * The single-arg and two-arg constructors default to "zh" for backward
 * compatibility with callers that don't yet have ToolMessages injected.
 * The three-arg constructor allows locale-aware error messages.
 *
 * @author CRUDClass
 */
public class McpBusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -2582293232143204024L;

    private final McpErrorCode errorCode;
    private final String locale;

    public McpBusinessException(McpErrorCode errorCode) {
        this(errorCode, null, "zh");
    }

    public McpBusinessException(McpErrorCode errorCode, String detail) {
        this(errorCode, detail, "zh");
    }

    public McpBusinessException(McpErrorCode errorCode, String detail, String locale) {
        super(errorCode.getMessage(locale) + (detail != null ? ": " + detail : ""));
        this.errorCode = errorCode;
        this.locale = locale;
    }

    public McpErrorCode getErrorCode() {
        return errorCode;
    }

    public String getLocale() {
        return locale;
    }
}
