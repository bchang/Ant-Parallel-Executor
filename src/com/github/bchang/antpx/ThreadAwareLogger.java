package com.github.bchang.antpx;

import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.util.StringUtils;

import java.io.PrintStream;

/**
 */
public class ThreadAwareLogger extends DefaultLogger {
    @Override
    protected void printMessage(String message, PrintStream stream, int priority) {
        long tid = Thread.currentThread().getId();
        StringBuilder header = new StringBuilder("Thread");
        header.append(tid).append(':');
        while (header.length() < 10) {
            header.append(' ');
        }
        message = message.replace(StringUtils.LINE_SEP, StringUtils.LINE_SEP + header);
        stream.println(header + message);
    }
}
