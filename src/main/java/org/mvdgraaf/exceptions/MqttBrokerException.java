package org.mvdgraaf.exceptions;

public class MqttBrokerException extends RuntimeException {

        private final String errorCode;

        public MqttBrokerException(String message, ExceptionTypes errorCode) {
            super(message);
            this.errorCode = errorCode.name();
        }

        public MqttBrokerException(String message, ExceptionTypes errorCode, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode.name();
        }

        public String getErrorCode() {
            return errorCode;
        }

}
