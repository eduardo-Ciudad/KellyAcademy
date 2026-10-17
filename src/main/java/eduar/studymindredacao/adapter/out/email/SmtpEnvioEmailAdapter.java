package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import eduar.studymindredacao.domain.port.EnvioEmailPort;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Envio por SMTP (Gmail com senha de app). Três cuidados:
 * <ul>
 *   <li>com transação aberta, só agenda depois do commit: cadastro desfeito não manda link morto;</li>
 *   <li>o envio roda num executor próprio e limitado: SMTP lento não prende as threads da API, e o tempo de
 *       resposta não muda se a conta existe ou não;</li>
 *   <li>falha nunca sobe para o caso de uso e o log não leva link, token nem o e-mail inteiro.</li>
 * </ul>
 */
public class SmtpEnvioEmailAdapter implements EnvioEmailPort, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SmtpEnvioEmailAdapter.class);

    private final JavaMailSender mailSender;
    private final Executor executor;
    private final String remetente;
    private final String nomeRemetente;
    private final ModeloEmailVerificacao modelo;

    public SmtpEnvioEmailAdapter(
            JavaMailSender mailSender,
            Executor executor,
            String remetente,
            String nomeRemetente,
            ModeloEmailVerificacao modelo
    ) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender é obrigatório");
        this.executor = Objects.requireNonNull(executor, "executor é obrigatório");
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalArgumentException("remetente (MAIL_USERNAME) é obrigatório");
        }
        this.remetente = remetente.strip();
        this.nomeRemetente = nomeRemetente == null || nomeRemetente.isBlank() ? "Kelly Academy" : nomeRemetente.strip();
        this.modelo = Objects.requireNonNull(modelo, "modelo é obrigatório");
    }

    /**
     * Executor de uma thread com fila limitada. Com a fila cheia o e-mail é descartado com log (o aluno pode pedir
     * outro link), em vez de acumular memória ou bloquear quem chamou.
     */
    public static ThreadPoolExecutor criarExecutor(int filaMaxima) {
        if (filaMaxima < 1) {
            throw new IllegalArgumentException("fila do e-mail deve ser pelo menos 1");
        }
        var contador = new AtomicInteger();
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(filaMaxima),
                tarefa -> {
                    var thread = new Thread(tarefa, "email-envio-" + contador.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                (tarefa, pool) -> log.warn("Fila de e-mail cheia ({} aguardando): envio descartado", pool.getQueue().size())
        );
    }

    @Override
    public void enviarVerificacao(MensagemVerificacaoEmail mensagem) {
        // Monta antes de agendar: link inválido é erro de programação e deve aparecer já, não na thread do e-mail
        EmailMontado email = modelo.montar(mensagem);
        Runnable envio = () -> enviarAgora(email);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agendar(envio, email);
                }
            });
        } else {
            agendar(envio, email);
        }
    }

    private void agendar(Runnable envio, EmailMontado email) {
        try {
            executor.execute(envio);
        } catch (RejectedExecutionException e) {
            log.warn("E-mail de verificação para {} não agendado: executor recusou", EnderecosEmail.mascarar(email.destinatario()));
        }
    }

    private void enviarAgora(EmailMontado email) {
        String destino = EnderecosEmail.mascarar(email.destinatario());
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(remetente, nomeRemetente);
            helper.setTo(email.destinatario());
            helper.setSubject(email.assunto());
            helper.setText(email.texto(), email.html());
            mailSender.send(mensagem);
            log.info("E-mail de verificação enviado para {}", destino);
        } catch (Exception e) {
            // Só a classe da exceção: a mensagem do SMTP pode repetir o destinatário
            log.warn("Falha ao enviar e-mail de verificação para {}: {}", destino, e.getClass().getSimpleName());
        }
    }

    /** Chamado pelo Spring no desligamento: espera os e-mails da fila por até 10 s. */
    @Override
    public void close() {
        if (executor instanceof ExecutorService servico) {
            servico.shutdown();
            try {
                if (!servico.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Desligando com e-mails ainda na fila: {} descartados", servico.shutdownNow().size());
                }
            } catch (InterruptedException e) {
                servico.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
