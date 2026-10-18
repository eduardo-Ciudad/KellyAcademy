package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.CadastroRequest;
import eduar.studymindredacao.adapter.in.web.dto.EsqueciSenhaRequest;
import eduar.studymindredacao.adapter.in.web.dto.LoginRequest;
import eduar.studymindredacao.adapter.in.web.dto.MensagemResponse;
import eduar.studymindredacao.adapter.in.web.dto.RedefinirSenhaRequest;
import eduar.studymindredacao.adapter.in.web.dto.ReenviarVerificacaoRequest;
import eduar.studymindredacao.adapter.in.web.dto.TokenResponse;
import eduar.studymindredacao.adapter.in.web.dto.UsuarioResponse;
import eduar.studymindredacao.adapter.in.web.dto.VerificarEmailRequest;
import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.application.usecase.AutenticarUsuarioService;
import eduar.studymindredacao.application.usecase.CadastrarUsuarioService;
import eduar.studymindredacao.application.usecase.RedefinicaoSenhaService;
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
    static final int ESQUECI_SENHA_POR_IP_POR_HORA = 5;
    static final int ESQUECI_SENHA_POR_EMAIL_POR_HORA = 3;
    static final int REDEFINIR_POR_IP_POR_MINUTO = 10;
    /** Mesma resposta exista a conta ou não, esteja verificada ou não: o endpoint não serve para descobrir e-mails. */
    static final String MENSAGEM_REENVIO =
            "Se existir uma conta aguardando confirmação com esse e-mail, enviamos um novo link. Confira também o spam.";
    /** Mesma resposta exista a conta ou não. */
    static final String MENSAGEM_ESQUECI_SENHA =
            "Se existir uma conta com esse e-mail, enviamos um link para redefinir a senha. Confira também o spam.";

    private final CadastrarUsuarioService cadastrarUsuario;
    private final AutenticarUsuarioService autenticarUsuario;
    private final VerificacaoEmailService verificacaoEmail;
    private final RedefinicaoSenhaService redefinicaoSenha;
    private final LimitadorDeTentativas limitador;

    public AuthController(
            CadastrarUsuarioService cadastrarUsuario,
            AutenticarUsuarioService autenticarUsuario,
            VerificacaoEmailService verificacaoEmail,
            RedefinicaoSenhaService redefinicaoSenha,
            LimitadorDeTentativas limitador
    ) {
        this.cadastrarUsuario = cadastrarUsuario;
        this.autenticarUsuario = autenticarUsuario;
        this.verificacaoEmail = verificacaoEmail;
        this.redefinicaoSenha = redefinicaoSenha;
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

    /** Pede o link de "Esqueci minha senha". Responde sempre 202 com a mesma mensagem; o envio sai em segundo plano. */
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensagemResponse esqueciSenha(@RequestBody @Valid EsqueciSenhaRequest request, HttpServletRequest http) {
        limitador.consumir("esqueci-senha-ip:" + http.getRemoteAddr(), ESQUECI_SENHA_POR_IP_POR_HORA, Duration.ofHours(1));
        limitador.consumir("esqueci-senha-email:" + request.email().trim().toLowerCase(Locale.ROOT),
                ESQUECI_SENHA_POR_EMAIL_POR_HORA, Duration.ofHours(1));
        redefinicaoSenha.solicitar(request.email());
        return new MensagemResponse(MENSAGEM_ESQUECI_SENHA);
    }

    /**
     * Troca a senha com o token do link. POST com o token no corpo, pelo mesmo motivo da verificação de e-mail.
     * Não devolve JWT: o front volta para o login.
     */
    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@RequestBody @Valid RedefinirSenhaRequest request, HttpServletRequest http) {
        limitador.consumir("redefinir-ip:" + http.getRemoteAddr(), REDEFINIR_POR_IP_POR_MINUTO, Duration.ofMinutes(1));
        redefinicaoSenha.redefinir(request.token(), request.novaSenha());
    }
}
