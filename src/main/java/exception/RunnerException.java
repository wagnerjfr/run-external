/* Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved. */

package exception;

/**
 * The RunnerException class is the generic exception class
 * to be used when something in the JET test engine fails. Create any
 * more specific Exception classes as subclasses of RunnerException
 */
public class RunnerException extends Exception {

    /**
     * Determines if a de-serialized file is compatible with this class,
     * bump this number if an incompatible change is made.
     */
    private static final long serialVersionUID = 5L;
    private RunnerError errorCode = RunnerError.SystemError;

    public static enum RunnerError {

        SystemError, UserError;

    }

    /**
     * Offset for message numbers in this class.
     */
    private static final int BASE = 90000;

    /**
     * UNKNOWN error.
     */
    public static final int UNKNOWN = BASE + 0;
    public static final int OFFSET_INTERNAL = 1;
    public static final int OFFSET_PROPERTY_NOT_SET = 2;
    public static final int OFFSET_NOT_IMPLEMENTED = 3;

    /**
     * INTERNAL error.
     */
    public static final int INTERNAL = BASE + OFFSET_INTERNAL;
    /**
     * PROPERTY NOT SET
     */
    public static final int PROPERTY_NOT_SET = BASE + OFFSET_PROPERTY_NOT_SET;
    /**
     * NOT implemented error
     */
    public static final int NOT_IMPLEMENTED = BASE + OFFSET_NOT_IMPLEMENTED;

    /**
     * Constructs MgtException with an error code
     *
     * @param errcode error code
     * @since 4.2.0
     */
    public RunnerException(int errcode) {
        super("JET ERR " + errcode);
    }

    public RunnerException(String msg, RunnerError code) {
        super(msg);
        errorCode = code;
    }

    /**
     * Constructor. No message is allocated, and no exception is linked
     */
    public RunnerException() {
        super();
    }

    /**
     * Constructor taking a message as parameter. No exception is linked.
     *
     * @param message message explaining why the exception was thrown.
     */
    public RunnerException(String message) {
        super(message);
    }

    /**
     * Constructor linking an existing exception to this exception.
     *
     * @param e linked exception.
     */
    public RunnerException(Exception e) {
        super(e);
    }

    /**
     * Constructor taking a message as parameter and links the exception
     * with another
     *
     * @param message message explaining why the exception was thrown.
     * @param e linked exception.
     */
    public RunnerException(String message, Exception e) {
        super(message, e);
        if (e instanceof RunnerException) {
            errorCode = ((RunnerException) e).getErrorCode();
        }
    }

    /**
     * Overrides getMessage to include linked exceptions' message.
     *
     * @return Message from all linked exceptions
     */
    @Override
    public String getMessage() {
        StringBuilder res = new StringBuilder(super.getMessage());
        Throwable linked = getCause();
        if (linked != null) {
            res.append(':');
            res.append(linked.getMessage());
        }
        return res.toString();
    }

    /**
     * Returns the error type.
     *
     * @return
     */
    public RunnerError getErrorCode() {
        return errorCode;
    }

    /**
     * Returns true if errorCode equals UserError.
     *
     * @return
     */
    public boolean isUserError() {
        return errorCode == RunnerError.UserError;
    }
}
