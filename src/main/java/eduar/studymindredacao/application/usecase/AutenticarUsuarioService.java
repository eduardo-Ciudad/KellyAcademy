package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.CredenciaisInvalidasException;
import eduar.studymindredacao.domain.exception.EmailNaoVerificadoException;
import eduar.studymindredacao.domain.model.TokenGerado;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.port.SenhaEncoderPort;
import eduar.studymindredacao.domain.port.TokenPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticarUsuarioService {
    private final UsuarioRepositoryPort usuarioRepository;
    private final SenhaEncoderPort senhaEncoder;
    private final TokenPort tokenPort;
    private final VerificacaoEmailService verificacaoEmail;
    /**
     * Hash de uma senha aleatória, gerado uma vez na subida (SEC-12). Para e-mail inexistente a senha é
     * conferida contra ele: o login gasta o mesmo tempo de BCrypt exista a conta ou não.
     */
    private final String hashFalso;

    public AutenticarUsuarioService(
            UsuarioRepositoryPort usuarioRepository,
            SenhaEncoderPort senhaEncoder,
            TokenPort tokenPort,
            VerificacaoEmailService verificacaoEmail
    ) {
        this.usuarioRepository = usuarioRepository;
        this.senhaEncoder = senhaEncoder;
        this.tokenPort = tokenPort;
        this.verificacaoEmail = verificacaoEmail;
        this.hashFalso = senhaEncoder.codificar("conta-inexistente-" + UUID.randomUUID());
    }

    public TokenGerado autenticar(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isEmpty()) {
            throw new CredenciaisInvalidasException();
        }
        Optional<Usuario> encontrado = usuarioRepository.buscarPorEmail(email);
        // Sempre roda o BCrypt (contra o hash falso quando a conta não existe): mesma resposta e mesmo custo
        boolean senhaConfere = senhaEncoder.confere(senha, encontrado.map(Usuario::senhaHash).orElse(hashFalso));
        if (encontrado.isEmpty() || !senhaConfere) {
            throw new CredenciaisInvalidasException();
        }

        Usuario usuario = encontrado.get();
        // Só depois da senha correta: quem não sabe a senha não descobre se a conta existe nem se está verificada
        if (verificacaoEmail.verificacaoObrigatoria() && !usuario.emailVerificado()) {
            throw new EmailNaoVerificadoException();
        }
        return tokenPort.gerar(usuario);
    }
}
