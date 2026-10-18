package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.CredenciaisInvalidasException;
import eduar.studymindredacao.domain.exception.SenhaAtualIncorretaException;
import eduar.studymindredacao.domain.model.PoliticaSenha;
import eduar.studymindredacao.domain.model.TokenGerado;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.SenhaEncoderPort;
import eduar.studymindredacao.domain.port.TokenPort;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Troca de senha com a conta logada. Exige a senha atual, sobe a versão da sessão (os outros aparelhos caem)
 * e devolve um JWT novo para quem trocou continuar logado.
 */
@Service
public class AlterarSenhaService {
    static final String MENSAGEM_SENHA_IGUAL = "a nova senha deve ser diferente da atual";

    private final UsuarioRepositoryPort usuarioRepository;
    private final TokenUsuarioRepositoryPort tokenRepository;
    private final SenhaEncoderPort senhaEncoder;
    private final TokenPort tokenPort;
    private final RedefinicaoSenhaService redefinicaoSenha;
    private final Clock clock;

    public AlterarSenhaService(
            UsuarioRepositoryPort usuarioRepository,
            TokenUsuarioRepositoryPort tokenRepository,
            SenhaEncoderPort senhaEncoder,
            TokenPort tokenPort,
            RedefinicaoSenhaService redefinicaoSenha,
            Clock clock
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.senhaEncoder = senhaEncoder;
        this.tokenPort = tokenPort;
        this.redefinicaoSenha = redefinicaoSenha;
        this.clock = clock;
    }

    @Transactional
    public TokenGerado alterar(UUID usuarioId, String senhaAtual, String novaSenha) {
        // Caso raro: o filtro já recusaria o token de uma conta apagada
        Usuario usuario = usuarioRepository.buscarPorId(usuarioId).orElseThrow(CredenciaisInvalidasException::new);

        // A senha atual vem primeiro: quem tenta adivinhá-la recebe sempre a mesma resposta
        if (senhaAtual == null || senhaAtual.isEmpty() || !senhaEncoder.confere(senhaAtual, usuario.senhaHash())) {
            throw new SenhaAtualIncorretaException();
        }
        PoliticaSenha.validar(novaSenha);
        if (senhaEncoder.confere(novaSenha, usuario.senhaHash())) {
            throw new IllegalArgumentException(MENSAGEM_SENHA_IGUAL);
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        Usuario alterado = usuarioRepository.alterarSenha(usuarioId, senhaEncoder.codificar(novaSenha), agora)
                .orElseThrow(CredenciaisInvalidasException::new);
        // Um link de "Esqueci minha senha" pendente não pode mais trocar a senha que o aluno acabou de definir
        tokenRepository.invalidarAtivos(usuarioId, TipoTokenUsuario.REDEFINICAO_SENHA, agora);
        redefinicaoSenha.avisarSenhaAlterada(alterado, agora);

        // Gerado com a versão nova: este aparelho continua logado, os outros caem
        return tokenPort.gerar(alterado);
    }
}
