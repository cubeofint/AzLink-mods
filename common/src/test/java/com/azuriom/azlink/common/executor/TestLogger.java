package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.logger.LoggerAdapter;

import java.util.ArrayList;
import java.util.List;

final class TestLogger implements LoggerAdapter {

    final List<String> infos = new ArrayList<String>();
    final List<String> warns = new ArrayList<String>();
    final List<String> errors = new ArrayList<String>();

    @Override
    public void info(String message) {
        this.infos.add(message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        this.infos.add(message);
    }

    @Override
    public void warn(String message) {
        this.warns.add(message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        this.warns.add(message);
    }

    @Override
    public void error(String message) {
        this.errors.add(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        this.errors.add(message);
    }
}
