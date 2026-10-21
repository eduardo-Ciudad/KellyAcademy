package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.LimitesJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.PapelMensagem;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import eduar.studymindredacao.domain.julia.StatusConversa;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.TipoRedacao;
import eduar.studymindredacao.domain.port.ConversaJuliaRepositoryPort;
import eduar.studymindredacao.domain.port.RedacaoRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConversaJuliaRepositoryAdapter.class, UsuarioRepositoryAdapter.class, RedacaoRepositoryAdapter.class})
class ConversaJuliaRepositoryAdapterIntegrationTest {
    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final LimitesJulia LIMITES = new LimitesJulia(10, 500, 6, Duration.ofHours(24));

    @Autowired
    private ConversaJuliaRepositoryPort conversas;
    @Autowired
    private UsuarioRepositoryPort usuarios;
    @Autowired
    private RedacaoRepositoryPort redacoes;
    @Autowired
    private TemaJpaRepository temas;
    @Autowired
    private ConversaJuliaJpaRepository jpa;

    private UUID temaId;
    private UUID usuarioId;
    private UUID redacaoId;

    @BeforeEach
    void criarUsuarioERedacao() {
        temaId = temas.findAll(PageRequest.of(0, 1)).getContent().get(0).getId();
        usuarioId = novoUsuario();
        redacaoId = novaRedacao(usuarioId);
    }

    @Test
    void salvarNovaGeraIdsEBuscarAtivaTrazMensagensNaOrdem() {
        var conversa = ConversaJulia.iniciar(usuarioId, redacaoId, AGORA);
        trocar(conversa, 1);
        trocar(conversa, 2);

        var salva = conversas.salvar(conversa);
        jpa.flush();

        assertThat(salva.getId()).isNotNull();
        var lida = conversas.buscarAtiva(usuarioId, redacaoId).orElseThrow();
        assertThat(lida.getId()).isEqualTo(salva.getId());
        assertThat(lida.getMensagens()).extracting(MensagemJulia::conteudo)
                .containsExactly("pergunta 1", "resposta 1", "pergunta 2", "resposta 2");
        assertThat(lida.getMensagens()).allMatch(m -> m.id() != null);
        var pergunta = lida.getMensagens().get(0);
        var resposta = lida.getMensagens().get(1);
        assertThat(pergunta.papel()).isEqualTo(PapelMensagem.USUARIO);
        assertThat(pergunta.tokensEntrada()).isNull();
        assertThat(resposta.tokensEntrada()).isEqualTo(900);
        assertThat(resposta.tokensSaida()).isEqualTo(120);
    }

    @Test
    void salvarDeNovoAcrescentaMensagensNaMesmaConversa() {
        var conversa = ConversaJulia.iniciar(usuarioId, redacaoId, AGORA);
        trocar(conversa, 1);
        var salva = conversas.salvar(conversa);
        jpa.flush();

        var lida = conversas.buscarAtiva(usuarioId, redacaoId).orElseThrow();
        trocar(lida, 2);
        conversas.salvar(lida);
        jpa.flush();

        var relida = conversas.buscarAtiva(usuarioId, redacaoId).orElseThrow();
        assertThat(relida.getId()).isEqualTo(salva.getId());
        assertThat(relida.getMensagens()).hasSize(4);
        assertThat(relida.getUltimaAtividadeEm()).isEqualTo(AGORA.plusSeconds(2));
    }

    @Test
    void segundaConversaAtivaParaAMesmaRedacaoViolaOIndice() {
        conversas.salvar(ConversaJulia.iniciar(usuarioId, redacaoId, AGORA));
        jpa.flush();

        conversas.salvar(ConversaJulia.iniciar(usuarioId, redacaoId, AGORA));

        assertThatThrownBy(() -> jpa.flush()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void conversaEncerradaSomeDaBuscaEPermiteUmaNova() {
        var conversa = conversas.salvar(ConversaJulia.iniciar(usuarioId, redacaoId, AGORA));
        conversa.encerrar(AGORA.plusSeconds(10));
        conversas.salvar(conversa);
        jpa.flush();

        assertThat(conversas.buscarAtiva(usuarioId, redacaoId)).isEmpty();

        conversas.salvar(ConversaJulia.iniciar(usuarioId, redacaoId, AGORA.plusSeconds(20)));
        jpa.flush();
        assertThat(conversas.buscarAtiva(usuarioId, redacaoId)).isPresent();
    }

    @Test
    void encerrarAtivasDoUsuarioNaoMexeEmOutroUsuario() {
        var outroUsuario = novoUsuario();
        var outraRedacao = novaRedacao(outroUsuario);
        conversas.salvar(ConversaJulia.iniciar(usuarioId, redacaoId, AGORA));
        conversas.salvar(ConversaJulia.iniciar(usuarioId, novaRedacao(usuarioId), AGORA));
        conversas.salvar(ConversaJulia.iniciar(outroUsuario, outraRedacao, AGORA));
        jpa.flush();

        int encerradas = conversas.encerrarAtivasDoUsuario(usuarioId, AGORA.plusSeconds(30));

        assertThat(encerradas).isEqualTo(2);
        assertThat(conversas.buscarAtiva(usuarioId, redacaoId)).isEmpty();
        var doOutro = conversas.buscarAtiva(outroUsuario, outraRedacao).orElseThrow();
        assertThat(doOutro.getStatus()).isEqualTo(StatusConversa.ATIVA);
    }

    private UUID novoUsuario() {
        String email = "julia-" + UUID.randomUUID() + "@exemplo.com";
        return usuarios.salvar(new Usuario(null, "Teste", email, "hash", Role.ALUNO, null, null)).id();
    }

    private UUID novaRedacao(UUID dono) {
        return redacoes.salvar(new Redacao(null, dono, temaId, TipoRedacao.PRATICA, "texto", null, null, null)).id();
    }

    private static void trocar(ConversaJulia conversa, int i) {
        conversa.registrarTroca(
                MensagemJulia.doUsuario("pergunta " + i, 500, AGORA),
                MensagemJulia.daJulia(new RespostaJulia("resposta " + i, 900, 120), AGORA),
                LIMITES, AGORA.plusSeconds(i));
    }
}