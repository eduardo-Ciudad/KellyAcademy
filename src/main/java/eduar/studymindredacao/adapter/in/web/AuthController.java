package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.CadastroRequest;
import eduar.studymindredacao.adapter.in.web.dto.LoginRequest;
import eduar.studymindredacao.adapter.in.web.dto.MensagemResponse;
import eduar.studymindredacao.adapter.in.web.dto.ReenviarVerificacaoRequest;
import eduar.studymindredacao.adapter.in.web.dto.TokenResponse;
import eduar.studymindredacao.adapter.in.web.dto.UsuarioResponse;
import eduar.studymindredacao.adapter.in.web.dto.VerificarEmailRequest;
import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.application.usecase.AutenticarUsuarioService;
import eduar.studymindredacao.application.usecase.CadastrarUsuarioService;
import eduar.studymindredacao.application.usecase.VerificacaoEmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/auth")
public class AuthController {
    static final int LOGIN_POR_IP_POR_MINUTO = 10;
    static final int LOGIN_POR_EMAIL_POR_MINUTO = 5;
    static final int CADASTRO_POR_IP_POR_HORA = 5;
    static final int VERIFICAR_POR_IP_POR_MINUTO = 10;
    static final int REENVIO_POR_IP_POR_HORA = 5;
    static final int REENVIO_POR_EMAIL_POR_HORA = 3;
    /** Mesma resposta exista a conta ou não, esteja verificada ou não: o endpoint não serve para descobrir e-mails. */
    static final String MENSAGEM_REENVIO =
            "Se existir uma conta aguardando confirmação com esse e-mail, enviamos um novo link. Confira também o spam.";

    private final CadastrarUsuarioService cadastrarUsuario;
    private final AutenticarUsuarioService autenticarUsuario;
    private final VerificacaoEmailService verificacaoEmail;
    private final LimitadorDeTentativas limitador;

    public AuthController(
            CadastrarUsuarioService cadastrarUsuario,
            AutenticarUsuarioService autenticarUsuario,
            VerificacaoEmailService verificacaoEmail,
            LimitadorDeTentativas limitador
    ) {
        this.cadastrarUsuario = cadastrarUsuario;
        this.autenticarUsuario = autenticarUsuario;
        this.verificacaoEmail = verificacaoEmail;
        this.limitador = limitador;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@RequestBody @Valid CadastroRequest request, HttpServletRequest http) {
        limitador.consumir("cadastro-ip:" + http.getRemoteAddr(), CADASTRO_POR_IP_POR_HORA, Duration.ofHours(1));
        var usuario = cadastrarUsuario.cadastrar(request.nome(), request.email(), request.senha());
        return UsuarioResponse.de(usuario);
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest http) {
        // Por IP segura um atacante só; por e-mail protege a conta mesmo com IPs variados
        limitador.consumir("login-ip:" + http.getRemoteAddr(), LOGIN_POR_IP_POR_MINUTO, Duration.ofMinutes(1));
        limitador.consumir("login-email:" + request.email().trim().toLowerCase(Locale.ROOT),
                LOGIN_POR_EMAIL_POR_MINUTO, Duration.ofMinutes(1));
        return TokenResponse.de(autenticarUsuario.autenticar(request.email(), request.senha()));
    }

    /**
     * Confirma o e-mail com o token do link. POST (não GET) de propósito: leitores de e-mail abrem links GET
     * sozinhos para checar segurança e queimariam o token antes do aluno; e o token fica fora de URL e log.
     */
    @PostMapping("/verificar-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verificarEmail(@RequestBody @Valid VerificarEmailRequest request, HttpServletRequest http) {
        limitador.consumir("verificar-ip:" + http.getRemoteAddr(), VERIFICAR_POR_IP_POR_MINUTO, Duration.ofMinutes(1));
        verificacaoEmail.confirmar(request.token());
    }

    /** Pede um novo link. Responde sempre 202 com a mesma mensagem; o envio sai em segundo plano. */
    @PostMapping("/reenviar-verificacao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensagemResponse reenviarVerificacao(@RequestBody @Valid ReenviarVerificacaoRequest request, HttpServletRequest http) {
        limitador.consumir("reenvio-ip:" + http.getRemoteAddr(), REENVIO_POR_IP_POR_HORA, Duration.ofHours(1));
        limitador.consumir("reenvio-email:" + request.email().trim().toLowerCase(Locale.ROOT),
                REENVIO_POR_EMAIL_POR_HORA, Duration.ofHours(1));
        verificacaoEmail.reenviar(request.email());
        return new MensagemResponse(MENSAGEM_REENVIO);
    }
}
