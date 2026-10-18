package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;
import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;
import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpEnvioEmailAdapterTest {
    private static final String TOKEN = "TokenSecreto_123-abc";
    private static final String LINK = "https://kellyacademy.com.br/verificar-email#token=" + TOKEN;
    private static final MensagemVerificacaoEmail MENSAGEM = new MensagemVerificacaoEmail("ana@exemplo.com", "Ana", LINK);
    private static final String LINK_REDEFINICAO = "https://kellyacademy.com.br/redefinir-senha#token=" + TOKEN;
    private static final MensagemRedefinicaoSenha REDEFINICAO =
            new MensagemRedefinicaoSenha("ana@exemplo.com", "Ana", LINK_REDEFINICAO);
    private static final MensagemSenhaAlterada AVISO = new MensagemSenhaAlterada("ana@exemplo.com", "Ana",
            OffsetDateTime.of(2026, 10, 18, 17, 5, 0, 0, ZoneOffset.UTC), "https://kellyacademy.com.br/esqueci-senha");
    private static final ModelosEmail MODELOS = new ModelosEmail(
            new ModeloEmailVerificacao(Duration.ofHours(24)),
            new ModeloEmailRedefinicaoSenha(Duration.ofMinutes(30)),
            new ModeloEmailSenhaAlterada()
    );

    /** Não abre conexão: guarda as mensagens ou falha como um SMTP fora do ar. */
    static class MailSenderFalso extends JavaMailSenderImpl {
        final List<MimeMessage> enviadas = new ArrayList<>();
        boolean falhar;

        @Override
        protected void doSend(MimeMessage[] mensagens, Object[] originais) {
            if (falhar) {
                throw new MailSendException("535 credenciais recusadas para ana@exemplo.com " + LINK);
            }
            enviadas.addAll(List.of(mensagens));
        }
    }

    private final MailSenderFalso sender = new MailSenderFalso();

    private SmtpEnvioEmailAdapter adapterSincrono() {
        return new SmtpEnvioEmailAdapter(sender, Runnable::run, "kellyacademy@gmail.com", "Kelly Academy",
                MODELOS);
    }

    @AfterEach
    void limparTransacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void enviaComRemetenteDestinatarioAssuntoTextoEHtml() throws Exception {
        adapterSincrono().enviarVerificacao(MENSAGEM);

        assertThat(sender.enviadas).hasSize(1);
        MimeMessage enviada = sender.enviadas.get(0);
        var de = (InternetAddress) enviada.getFrom()[0];
        assertThat(de.getAddress()).isEqualTo("kellyacademy@gmail.com");
        assertThat(de.getPersonal()).isEqualTo("Kelly Academy");
        assertThat(enviada.getAllRecipients()).hasSize(1);
        assertThat(((InternetAddress) enviada.getAllRecipients()[0]).getAddress()).isEqualTo("ana@exemplo.com");
        assertThat(enviada.getSubject()).isEqualTo(ModeloEmailVerificacao.ASSUNTO);

        List<String> partes = new ArrayList<>();
        coletarTextos(enviada, partes);
        assertThat(partes).hasSize(2);
        assertThat(partes).anySatisfy(p -> assertThat(p).startsWith("Olá, Ana!").contains(LINK));
        assertThat(partes).anySatisfy(p -> assertThat(p).contains("<html").contains("href=\"" + LINK + "\""));
    }

    @Test
    void falhaDoSmtpNaoSobeENaoVazaTokenNemEmailNoLog() {
        sender.falhar = true;

        try (var logs = new LogsCapturados(SmtpEnvioEmailAdapter.class)) {
            assertThatCode(() -> adapterSincrono().enviarVerificacao(MENSAGEM)).doesNotThrowAnyException();

            assertThat(logs.tudo())
                    .contains("Falha ao enviar")
                    .contains("***@exemplo.com")
                    .contains("MailSendException")
                    .doesNotContain(TOKEN)
                    .doesNotContain("ana@exemplo.com")
                    .doesNotContain("535");
        }
    }

    @Test
    void envioComSucessoNaoRegistraTokenNoLog() {
        try (var logs = new LogsCapturados(SmtpEnvioEmailAdapter.class)) {
            adapterSincrono().enviarVerificacao(MENSAGEM);

            assertThat(logs.tudo()).contains("enviado para ***@exemplo.com").doesNotContain(TOKEN);
        }
    }

    @Test
    void comTransacaoAbertaSoEnviaDepoisDoCommit() {
        TransactionSynchronizationManager.initSynchronization();

        adapterSincrono().enviarVerificacao(MENSAGEM);
        assertThat(sender.enviadas).as("antes do commit").isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(sender.enviadas).as("depois do commit").hasSize(1);
    }

    @Test
    void transacaoDesfeitaNaoEnvia() {
        TransactionSynchronizationManager.initSynchronization();

        adapterSincrono().enviarVerificacao(MENSAGEM);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(sender.enviadas).isEmpty();
    }

    @Test
    void linkInvalidoFalhaNaHoraSemAgendarNada() {
        var ruim = new MensagemVerificacaoEmail("ana@exemplo.com", "Ana", "javascript:alert(1)");

        assertThatThrownBy(() -> adapterSincrono().enviarVerificacao(ruim)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sender.enviadas).isEmpty();
    }

    @Test
    void executorRecusandoNaoDerrubaQuemChamou() {
        var adapter = new SmtpEnvioEmailAdapter(sender, tarefa -> {
            throw new java.util.concurrent.RejectedExecutionException("cheio");
        }, "kellyacademy@gmail.com", "Kelly Academy", MODELOS);

        assertThatCode(() -> adapter.enviarVerificacao(MENSAGEM)).doesNotThrowAnyException();
        assertThat(sender.enviadas).isEmpty();
    }

    @Test
    void filaCheiaDescartaSemLancarExcecao() throws Exception {
        var executor = SmtpEnvioEmailAdapter.criarExecutor(1);
        var liberar = new CountDownLatch(1);
        var ocupada = new CountDownLatch(1);
        try {
            executor.execute(() -> {           // ocupa a única thread
                ocupada.countDown();
                aguardar(liberar);
            });
            assertThat(ocupada.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> { });        // ocupa a única vaga da fila

            assertThatCode(() -> executor.execute(() -> { })).doesNotThrowAnyException();
            assertThat(executor.getQueue()).hasSize(1);
        } finally {
            liberar.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closeEsperaOsEnviosDaFilaEEncerraOExecutor() {
        var executor = SmtpEnvioEmailAdapter.criarExecutor(10);
        var adapter = new SmtpEnvioEmailAdapter(sender, executor, "kellyacademy@gmail.com", "Kelly Academy",
                MODELOS);

        adapter.enviarVerificacao(MENSAGEM);
        adapter.close();

        assertThat(executor.isShutdown()).isTrue();
        assertThat(sender.enviadas).hasSize(1);
    }

    @Test
    void exigeRemetente() {
        assertThatThrownBy(() -> new SmtpEnvioEmailAdapter(sender, Runnable::run, " ", "Kelly Academy",
                MODELOS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAIL_USERNAME");
    }

    @Test
    void filaPrecisaDePeloMenosUmaVaga() {
        assertThatThrownBy(() -> SmtpEnvioEmailAdapter.criarExecutor(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void coletarTextos(Part parte, List<String> textos) throws Exception {
        Object conteudo = parte.getContent();
        if (conteudo instanceof String texto) {
            textos.add(texto);
        } else if (conteudo instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart filha = multipart.getBodyPart(i);
                coletarTextos(filha, textos);
            }
        }
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- redefinição de senha e aviso de senha alterada ----------

    @Test
    void redefinicaoSaiComAssuntoELinkSoDepoisDoCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        adapterSincrono().enviarRedefinicaoSenha(REDEFINICAO);
        assertThat(sender.enviadas).as("antes do commit").isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(sender.enviadas).hasSize(1);
        assertThat(sender.enviadas.get(0).getSubject()).isEqualTo(ModeloEmailRedefinicaoSenha.ASSUNTO);
        List<String> partes = new ArrayList<>();
        coletarTextos(sender.enviadas.get(0), partes);
        assertThat(partes).anySatisfy(p -> assertThat(p).contains(LINK_REDEFINICAO));
    }

    @Test
    void avisoDeSenhaAlteradaSaiSoDepoisDoCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        adapterSincrono().enviarAvisoSenhaAlterada(AVISO);
        assertThat(sender.enviadas).as("antes do commit").isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(sender.enviadas).hasSize(1);
        assertThat(sender.enviadas.get(0).getSubject()).isEqualTo(ModeloEmailSenhaAlterada.ASSUNTO);
    }

    @Test
    void transacaoDesfeitaNaoEnviaRedefinicaoNemAviso() {
        TransactionSynchronizationManager.initSynchronization();

        adapterSincrono().enviarRedefinicaoSenha(REDEFINICAO);
        adapterSincrono().enviarAvisoSenhaAlterada(AVISO);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(sender.enviadas).isEmpty();
    }

    @Test
    void falhaNaRedefinicaoNaoSobeENaoVazaTokenNemEmail() {
        sender.falhar = true;

        try (var logs = new LogsCapturados(SmtpEnvioEmailAdapter.class)) {
            assertThatCode(() -> adapterSincrono().enviarRedefinicaoSenha(REDEFINICAO)).doesNotThrowAnyException();

            assertThat(logs.tudo())
                    .contains("Falha ao enviar e-mail de redefinição de senha")
                    .contains("***@exemplo.com")
                    .doesNotContain(TOKEN)
                    .doesNotContain("ana@exemplo.com");
        }
    }
}
