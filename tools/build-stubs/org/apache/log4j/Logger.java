package org.apache.log4j;
/** Compile-only stub. */
public class Logger {
    public static Logger getLogger(String name) { return new Logger(); }
    public static Logger getLogger(Class<?> clazz) { return new Logger(); }
    public boolean isEnabledFor(Priority level) { return false; }
    public boolean isTraceEnabled() { return false; }
    public String getName() { return ""; }
    public boolean isDebugEnabled() { return false; }
    public boolean isInfoEnabled() { return false; }
    public void log(String fqcn, Priority level, Object message, Throwable t) { }
}
