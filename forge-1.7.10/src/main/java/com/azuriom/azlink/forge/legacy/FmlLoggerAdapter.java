package com.azuriom.azlink.forge.legacy;

import com.azuriom.azlink.common.logger.LoggerAdapter;
import cpw.mods.fml.common.FMLLog;
import org.apache.logging.log4j.Level;

/**
 * FML / Log4j bridge for Forge 1.7.10 (no SLF4J on the classpath by default).
 */
public final class FmlLoggerAdapter implements LoggerAdapter {

    private static final String NAME = "AzLink";

    @Override
    public void info(String message) {
        FMLLog.log(NAME, Level.INFO, "%s", message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        FMLLog.log(NAME, Level.INFO, throwable, "%s", message);
    }

    @Override
    public void warn(String message) {
        FMLLog.log(NAME, Level.WARN, "%s", message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        FMLLog.log(NAME, Level.WARN, throwable, "%s", message);
    }

    @Override
    public void error(String message) {
        FMLLog.log(NAME, Level.ERROR, "%s", message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        FMLLog.log(NAME, Level.ERROR, throwable, "%s", message);
    }
}
