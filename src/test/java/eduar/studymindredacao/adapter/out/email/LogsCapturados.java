package eduar.studymindredacao.adapter.out.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Captura o que uma classe escreve no log, para os testes provarem que token e link não vazam. */
final class LogsCapturados implements AutoCloseable {
    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogsCapturados(Class<?> origem) {
        logger = (Logger) LoggerFactory.getLogger(origem);
        appender.start();
        logger.addAppender(appender);
    }

    List<String> mensagens() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    String tudo() {
        return String.join("\n", mensagens());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
