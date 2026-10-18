package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.CadastroFechadoException;
import eduar.studymindredacao.domain.exception.EmailJaCadastradoException;
import eduar.studymindredacao.domain.model.PoliticaSenha;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.SenhaEncoderPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class CadastrarUsuarioService {
    private final UsuarioRepositoryPort usuarioRepository;
    private final SenhaEncoderPort senhaEncoder;
    private final PoliticaCadastro politica;
    private final VerificacaoEmailService verificacaoEmail;
    private final Clock clock;

    public CadastrarUsuarioService(
            UsuarioRepositoryPort usuarioRepository,
            SenhaEncoderPort senhaEncoder,
            PoliticaCadastro politica,
            VerificacaoEmailService verificacaoEmail,
            Clock clock
    ) {
        this.usuarioRepository = usuarioRepository;
        this.senhaEncoder = senhaEncoder;
        this.politica = politica;
        this.verificacaoEmail = verificacaoEmail;
        this.clock = clock;
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha) {
        PoliticaSenha.validar(senha);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email é obrigatório");
        }

        String emailNormalizado = email.trim().toLowerCase();
        // Antes de consultar o banco: com o cadastro fechado, ninguém de fora descobre quais e-mails existem
        if (!politica.permite(emailNormalizado)) {
            throw new CadastroFechadoException();
        }
        if (usuarioRepository.existePorEmail(emailNormalizado)) {
            throw new EmailJaCadastradoException(emailNormalizado);
        }

        // Verificação desligada: a conta já nasce confirmada (comportamento de antes da feature)
        OffsetDateTime verificadoEm = verificacaoEmail.verificacaoObrigatoria() ? null : OffsetDateTime.now(clock);
        var usuario = new Usuario(
                null,
                nome == null ? null : nome.trim(),
                emailNormalizado,
                senhaEncoder.codificar(senha),
                Role.ALUNO,
                null,
                verificadoEm
        );
        var salvo = usuarioRepository.salvar(usuario);
        // Mesma transação: se o cadastro falhar depois daqui, o token some junto e o e-mail não sai (afterCommit)
        verificacaoEmail.emitirPara(salvo);
        return salvo;
    }
}
