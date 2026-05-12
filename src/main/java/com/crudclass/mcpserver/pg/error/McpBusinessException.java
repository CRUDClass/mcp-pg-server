package com.crudclass.mcpserver.pg.error;

import com.crudclass.mcpserver.pg.enums.McpErrorCode;

import java.io.Serial;

/**
 * Business exception that carries an {@link McpErrorCode}.
 * <p>
 * Thrown by {@link com.crudclass.mcpserver.pg.service.SqlValidator} and
 * {@link com.crudclass.mcpserver.pg.service.SqlExecutor} to signal
 * validation failures, execution errors, and security rejections.
 * Caught globally by {@link ExceptionMapper}.
 *
 * @author CRUDClass
 */
public class McpBusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -2582293232143204024L;

    private final McpErrorCode errorCode;

    public McpBusinessException(McpErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public McpBusinessException(McpErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public McpErrorCode getErrorCode() {
        return errorCode;
    }
}
