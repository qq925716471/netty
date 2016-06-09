/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.unix;

import io.netty.util.internal.EmptyArrays;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;

import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.errnoEAGAIN;
import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.errnoEBADF;
import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.errnoECONNRESET;
import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.errnoEINPROGRESS;
import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.errnoENOTCONN;
import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.errnoEPIPE;
import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.errnoEWOULDBLOCK;
import static io.netty.channel.unix.ErrorsStaticallyReferencedJniMethods.strError;

/**
 * <strong>Internal usage only!</strong>
 * <p>Static members which call JNI methods must be defined in {@link ErrorsStaticallyReferencedJniMethods}.
 */
public final class Errors {
    // As all our JNI methods return -errno on error we need to compare with the negative errno codes.
    public static final int ERRNO_ENOTCONN_NEGATIVE = -errnoENOTCONN();
    public static final int ERRNO_EBADF_NEGATIVE = -errnoEBADF();
    public static final int ERRNO_EPIPE_NEGATIVE = -errnoEPIPE();
    public static final int ERRNO_ECONNRESET_NEGATIVE = -errnoECONNRESET();
    public static final int ERRNO_EAGAIN_NEGATIVE = -errnoEAGAIN();
    public static final int ERRNO_EWOULDBLOCK_NEGATIVE = -errnoEWOULDBLOCK();
    public static final int ERRNO_EINPROGRESS_NEGATIVE = -errnoEINPROGRESS();

    /**
     * Holds the mappings for errno codes to String messages.
     * This eliminates the need to call back into JNI to get the right String message on an exception
     * and thus is faster.
     *
     * The array length of 512 should be more then enough because errno.h only holds < 200 codes.
     */
    private static final String[] ERRORS = new String[512];

    /**
     * <strong>Internal usage only!</strong>
     */
    public static final class NativeIoException extends IOException {
        private static final long serialVersionUID = 8222160204268655526L;
        private final int expectedErr;
        public NativeIoException(String method, int expectedErr) {
            super(method);
            this.expectedErr = expectedErr;
        }

        public int expectedErr() {
            return expectedErr;
        }
    }

    static {
        for (int i = 0; i < ERRORS.length; i++) {
            // This is ok as strerror returns 'Unknown error i' when the message is not known.
            ERRORS[i] = strError(i);
        }
    }

    static ConnectException newConnectException(String method, int err) {
        return new ConnectException(method + "() failed: " + ERRORS[-err]);
    }

    public static NativeIoException newConnectionResetException(String method, int errnoNegative) {
        NativeIoException exception = newIOException(method, errnoNegative);
        exception.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        return exception;
    }

    public static NativeIoException newIOException(String method, int err) {
        return new NativeIoException(method + "() failed: " + ERRORS[-err], err);
    }

    public static int ioResult(String method, int err, NativeIoException resetCause,
                               ClosedChannelException closedCause) throws IOException {
        // network stack saturated... try again later
        if (err == ERRNO_EAGAIN_NEGATIVE || err == ERRNO_EWOULDBLOCK_NEGATIVE) {
            return 0;
        }
        if (err == resetCause.expectedErr()) {
            throw resetCause;
        }
        if (err == ERRNO_EBADF_NEGATIVE || err == ERRNO_ENOTCONN_NEGATIVE) {
            throw closedCause;
        }
        // TODO: We could even go further and use a pre-instantiated IOException for the other error codes, but for
        //       all other errors it may be better to just include a stack trace.
        throw newIOException(method, err);
    }

    private Errors() { }
}
