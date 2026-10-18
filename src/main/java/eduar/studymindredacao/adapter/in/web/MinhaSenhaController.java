package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.AlterarSenhaRequest;
import eduar.studymindredacao.adapter.in.web.dto.TokenResponse;
import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.UsuarioAutenticado;
import eduar.studymindredacao.application.usecase.AlterarSenhaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Troca de senha com a conta logada. Fica fora do UsuarioController para o teste da rota não depender dos
 * casos de uso de evolução, temas e uso diário.
 */
@RestController
@RequestMapping("/usuarios/me/senha")
public class MinhaSenhaController {
    static final int TENTATIVAS_POR_USUARIO = 5;
    static final Duration JANELA_TENTATIVAS = Duration.ofMinutes(15);

    private final AlterarSenhaService alterarSenha;
    private final LimitadorDeTentativas limitador;

    public MinhaSenhaController(AlterarSenhaService alterarSenha, LimitadorDeTentativas limitador) {
        this.alterarSenha = alterarSenha;
        this.limitador = limitador;
    }

    /**
     * Devolve um JWT novo: este aparelho continua logado e os outros caem. Senha atual errada responde 400
     * (codigo SENHA_ATUAL_INCORRETA), não 401, para o front não encerrar a sessão.
     */
    @PutMapping
    public TokenResponse alterar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestBody @Valid AlterarSenhaRequest request
    ) {
        // Por conta, contando também as certas: freia quem pegou um token e tenta adivinhar a senha atual
        limitador.consumir("alterar-senha:" + usuario.id(), TENTATIVAS_POR_USUARIO, JANELA_TENTATIVAS);
        return TokenResponse.de(alterarSenha.alterar(usuario.id(), request.senhaAtual(), request.novaSenha()));
    }
}
