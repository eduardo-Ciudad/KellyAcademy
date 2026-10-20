package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.TentativasExcedidasException;
import eduar.studymindredacao.domain.exception.AssinaturaExistenteException;
import eduar.studymindredacao.domain.exception.AvaliacaoIAException;
import eduar.studymindredacao.domain.exception.CadastroFechadoException;
import eduar.studymindredacao.domain.exception.CredenciaisInvalidasException;
import eduar.studymindredacao.domain.exception.EmailJaCadastradoException;
import eduar.studymindredacao.domain.exception.EmailNaoVerificadoException;
import eduar.studymindredacao.domain.exception.ImagemInvalidaException;
import eduar.studymindredacao.domain.exception.ImagemNaoReconhecidaException;
import eduar.studymindredacao.domain.exception.LimiteDiarioAtingidoException;
import eduar.studymindredacao.domain.exception.LimiteGlobalAtingidoException;
import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import eduar.studymindredacao.domain.exception.RedacaoNaoEncontradaException;
import eduar.studymindredacao.domain.exception.RepertorioNaoEncontradoException;
import eduar.studymindredacao.domain.exception.SenhaAtualIncorretaException;
import eduar.studymindredacao.domain.exception.TemaNaoAdicionavelException;
import eduar.studymindredacao.domain.exception.TemaNaoEncontradoException;
import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.exception.TranscricaoIAException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    static final String CODIGO_EMAIL_NAO_VERIFICADO = "EMAIL_NAO_VERIFICADO";
    static final String CODIGO_TOKEN_INVALIDO = "TOKEN_INVALIDO";
    static final String CODIGO_SENHA_ATUAL_INCORRETA = "SENHA_ATUAL_INCORRETA";
    static final String CODIGO_ASSINATURA_EXISTENTE = "ASSINATURA_EXISTENTE";
    static final String CODIGO_PAGAMENTO_INDISPONIVEL = "PAGAMENTO_INDISPONIVEL";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException ex) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.putIfAbsent(erro.getField(), erro.getDefaultMessage()));

        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos");
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail corpoInvalido(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail argumentoInvalido(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TemaNaoAdicionavelException.class)
    public ProblemDetail temaNaoAdicionavel(TemaNaoAdicionavelException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ProblemDetail credenciaisInvalidas(CredenciaisInvalidasException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ProblemDetail emailJaCadastrado(EmailJaCadastradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Cobre corrida entre dois cadastros simultâneos com o mesmo e-mail (UNIQUE no banco)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflitoDeDados(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Os dados conflitam com um registro existente");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Parâmetro '" + ex.getName() + "' com formato inválido"
        );
    }

    @ExceptionHandler({
            TemaNaoEncontradoException.class,
            RedacaoNaoEncontradaException.class,
            RepertorioNaoEncontradoException.class
    })
    public ProblemDetail naoEncontrado(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 429 + Retry-After com os segundos até a meia-noite (no fuso da aplicação), quando o limite zera
    @ExceptionHandler(LimiteDiarioAtingidoException.class)
    public ResponseEntity<ProblemDetail> limiteDiario(LimiteDiarioAtingidoException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problema.setProperty("limite", ex.getLimite());
        problema.setProperty("recurso", ex.getRecurso().name());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosAteAmanha()))
                .body(problema);
    }

    // Teto do sistema: 503 (indisponível hoje para todos), não 429, que o front trata como limite do aluno
    @ExceptionHandler(LimiteGlobalAtingidoException.class)
    public ResponseEntity<ProblemDetail> limiteGlobal(LimiteGlobalAtingidoException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problema.setProperty("recurso", ex.getRecurso().name());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosAteAmanha()))
                .body(problema);
    }

    @ExceptionHandler(TentativasExcedidasException.class)
    public ResponseEntity<ProblemDetail> tentativasExcedidas(TentativasExcedidasException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosParaTentarDeNovo()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    // Senha certa, e-mail ainda não confirmado: o código separa este 403 do cadastro fechado e o front oferece reenviar
    @ExceptionHandler(EmailNaoVerificadoException.class)
    public ProblemDetail emailNaoVerificado(EmailNaoVerificadoException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problema.setProperty("codigo", CODIGO_EMAIL_NAO_VERIFICADO);
        return problema;
    }

    // Inexistente, expirado ou já usado: mesma resposta, para não ajudar quem testa tokens
    @ExceptionHandler(TokenInvalidoException.class)
    public ProblemDetail tokenInvalido(TokenInvalidoException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setProperty("codigo", CODIGO_TOKEN_INVALIDO);
        return problema;
    }

    // 400 e não 401: o aluno continua logado, o front só mostra o erro no campo da senha atual
    @ExceptionHandler(SenhaAtualIncorretaException.class)
    public ProblemDetail senhaAtualIncorreta(SenhaAtualIncorretaException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setProperty("codigo", CODIGO_SENHA_ATUAL_INCORRETA);
        return problema;
    }

    @ExceptionHandler(CadastroFechadoException.class)
    public ProblemDetail cadastroFechado(CadastroFechadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // A causa real vai para o log (adapter/caso de uso); o aluno recebe uma mensagem neutra
    @ExceptionHandler(AvaliacaoIAException.class)
    public ProblemDetail falhaNaCorrecao(AvaliacaoIAException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Não foi possível corrigir a redação agora. Tente novamente em alguns minutos; esta tentativa não foi descontada do seu limite."
        );
    }

    // Foto vazia, grande demais, formato não suportado ou quantidade errada: o "motivo" orienta a mensagem do front
    @ExceptionHandler(ImagemInvalidaException.class)
    public ProblemDetail imagemInvalida(ImagemInvalidaException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setProperty("motivo", ex.getMotivo().name());
        return problema;
    }

    // Passou de spring.servlet.multipart.*: mesmo motivo do arquivo grande demais validado no domínio
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail uploadGrandeDemais(MaxUploadSizeExceededException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(413), "Cada foto deve ter no máximo 5 MB.");
        problema.setProperty("motivo", ImagemInvalidaException.Motivo.GRANDE_DEMAIS.name());
        return problema;
    }

    // A IA leu a foto e não achou redação: o aluno precisa tirar outra (a vaga do dia já foi devolvida)
    @ExceptionHandler(ImagemNaoReconhecidaException.class)
    public ProblemDetail imagemNaoReconhecida(ImagemNaoReconhecidaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
    }

    // Mesma regra da correção: causa real no log, mensagem neutra para o aluno
    @ExceptionHandler(TranscricaoIAException.class)
    public ProblemDetail falhaNaTranscricao(TranscricaoIAException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Não foi possível ler a foto agora. Tente novamente em alguns minutos; esta tentativa não foi descontada do seu limite."
        );
    }

    // 409 com código: o front mostra "gerenciar assinatura" em vez do botão de assinar
    @ExceptionHandler(AssinaturaExistenteException.class)
    public ProblemDetail assinaturaExistente(AssinaturaExistenteException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setProperty("codigo", CODIGO_ASSINATURA_EXISTENTE);
        return problema;
    }

    // Mensagem fixa: a exceção carrega o motivo técnico do Stripe, que vai só para o log do adapter
    @ExceptionHandler(PagamentoIndisponivelException.class)
    public ProblemDetail pagamentoIndisponivel(PagamentoIndisponivelException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Pagamento indisponível no momento. Tente novamente em alguns minutos.");
        problema.setProperty("codigo", CODIGO_PAGAMENTO_INDISPONIVEL);
        return problema;
    }

    long segundosAteAmanha() {
        ZonedDateTime agora = ZonedDateTime.now(clock);
        ZonedDateTime meiaNoite = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.getZone());
        return Math.max(1, Duration.between(agora, meiaNoite).toSeconds());
    }
}
