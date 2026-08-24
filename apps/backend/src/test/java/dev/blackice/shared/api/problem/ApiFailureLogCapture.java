package dev.blackice.shared.api.problem;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Captures emitted API failure events without replacing the real logging backend. */
public final class ApiFailureLogCapture implements AutoCloseable {

    private static final Formatter FORMATTER = new Formatter() {
        @Override
        public String format(LogRecord record) {
            return formatMessage(record);
        }
    };

    private final Logger logger;
    private final Handler handler;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    private ApiFailureLogCapture(String loggerName) {
        logger = Logger.getLogger(loggerName);
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    public static ApiFailureLogCapture start(String loggerName) {
        return new ApiFailureLogCapture(loggerName);
    }

    public List<LogRecord> containing(String text) {
        return records.stream()
            .filter(record -> FORMATTER.format(record).contains(text))
            .toList();
    }

    public String formatted(LogRecord record) {
        return FORMATTER.format(record);
    }

    public String fullyFormatted(LogRecord record) {
        String message = formatted(record);
        if (record.getThrown() == null) {
            return message;
        }
        StringWriter rendered = new StringWriter();
        try (PrintWriter writer = new PrintWriter(rendered)) {
            record.getThrown().printStackTrace(writer);
        }
        return message + System.lineSeparator() + rendered;
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
    }
}
