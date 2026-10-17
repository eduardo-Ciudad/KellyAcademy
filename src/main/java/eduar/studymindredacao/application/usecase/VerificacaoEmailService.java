package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.EnvioEmailPort;
import eduar.studymindredacao.domain.port.GeradorTokenPort;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class VerificacaoEmailService {
    /** O token real tem 43 caracteres; qualquer coisa muito maior nem é processada. */
    static final int TAMANHO_MAXIMO_TOKEN = 128;
    private static final TipoTokenUsuario TIPO = TipoTokenUsuario.VERIFICACAO_EMAIL;

    private final UsuarioRepositoryPort usuarioRepository;
    private final TokenUsuarioRepositoryPort tokenRepository;
    private final GeradorTokenPort geradorToken;
    private final EnvioEmailPort envioEmail;
    private final PoliticaVerificacaoEmail politica;
    private final Clock clock;

    public VerificacaoEmailService(
            UsuarioRepositoryPort usuarioRepository,
            TokenUsuarioRepositoryPort tokenRepository,
            GeradorTokenPort geradorToken,
            EnvioEmailPort envioEmail,
            PoliticaVerificacaoEmail politica,
            Clock clock
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.geradorToken = geradorToken;
        this.envioEmail = envioEmail;
        this.politica = politica;
        this.clock = clock;
    }

    /** Com a verificação desligada a conta já nasce verificada e o login não exige confirmação. */
    public boolean verificacaoObrigatoria() {
        return politica.obrigatoria();
    }

    /**
     * Gera um link novo e manda por e-mail. Invalida os links anteriores: só o último enviado funciona.
     * Não faz nada com a verificação desligada ou se a conta já foi confirmada.
     */
    @Transactional
    public void emitirPara(Usuario usuario) {
        Objects.requireNonNull(usuario, "usuario é obrigatório");
        Objects.requireNonNull(usuario.id(), "usuario precisa estar salvo");
        if (!politica.obrigatoria() || usuario.emailVerificado()) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        tokenRepository.invalidarAtivos(usuario.id(), TIPO, agora);

        var token = geradorToken.emitir();
        tokenRepository.salvar(TokenUsuario.novo(usuario.id(), TIPO, token.hash(), agora, politica.validade()));
        envioEmail.enviarVerificacao(new MensagemVerificacaoEmail(
                usuario.email(), usuario.nome(), politica.linkPara(token.valorEmClaro())
        ));
    }

    /**
     * Pedido de novo link. Nunca diz se a conta existe: para e-mail desconhecido, já verificado ou vazio,
     * simplesmente não faz nada, e o endpoint responde igual em todos os casos.
     */
    @Transactional
    public void reenviar(String email) {
        if (!politica.obrigatoria() || email == null || email.isBlank()) {
            return;
        }
        usuarioRepository.buscarPorEmail(email.trim())
                .filter(usuario -> !usuario.emailVerificado())
                .ifPresent(this::emitirPara);
    }

    /**
     * Confirma o e-mail pelo token do link. Inexistente, expirado, já usado ou malformado: sempre a mesma
     * {@link TokenInvalidoException}. O consumo é atômico no banco, então o link só vale uma vez.
     */
    @Transactional
    public void confirmar(String token) {
        if (token == null || token.isBlank() || token.length() > TAMANHO_MAXIMO_TOKEN) {
            throw new TokenInvalidoException();
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        var usuarioId = tokenRepository.consumirValido(geradorToken.hash(token.strip()), TIPO, agora)
                .orElseThrow(TokenInvalidoException::new);
        // Conta apagada depois do envio: o ON DELETE CASCADE já levaria o token, mas fica a defesa
        var usuario = usuarioRepository.buscarPorId(usuarioId).orElseThrow(TokenInvalidoException::new);
        if (!usuario.emailVerificado()) {
            usuarioRepository.salvar(usuario.comEmailVerificado(agora));
        }
    }
}
