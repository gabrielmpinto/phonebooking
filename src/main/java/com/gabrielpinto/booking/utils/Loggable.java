package com.gabrielpinto.booking.utils;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public interface Loggable {
    default Logger log() {
        return LoggerFactory.getLogger(this.getClass());
    }
}
