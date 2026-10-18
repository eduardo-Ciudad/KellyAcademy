package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.adapter.out.security.JwtTokenAdapter;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.TokenGerado;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.TokenPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidarSessaoServiceTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 18, 14, 0, 0, 0, ZoneOffset.ofHours(-3));

    /** Repositório em memória que conta as consultas de versão. */
    static class RepositorioContando extends UsuarioRepositoryEmMemoria {
        int consultasDeVersao;

        @Override
        public Optional<Integer> buscarVersaoSessao(UUID id) {
            consultasDeVersao++;
            return super.buscarVersaoSessao(id);
        }
    }

    /** TokenPort fake: só reconhece os JWT cadastrados no mapa. */
    static class TokenFake implements TokenPort {
        final Map<String, DadosToken> validos = new HashMap<>();

        @Override
        public TokenGerado gerar(Usuario usuario) {
            throw new UnsupportedOperationException("não usado aqui");
        }

        @Override
        public Optional<DadosToken> validar(String token) {
            return Optional.ofNullable(validos.get(token));
        }
    }

    private RepositorioContando usuarios;
    private TokenFake tokens;
    private ValidarSessaoService service;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        usuarios = new RepositorioContando();
        tokens = new TokenFake();
        service = new ValidarSessaoService(tokens, usuarios);
        ana = usuarios.salvar(new Usuario(null, "Ana", "ana@exemplo.com", "hash", Role.ALUNO, null, AGORA));
    }

    private String jwtNaVersao(int versao) {
        String jwt = "jwt-v" + versao;
        tokens.validos.put(jwt, new DadosToken(ana.id(), ana.email(), Role.ALUNO, versao));
        return jwt;
    }

    @Test
    void versaoIgualADoBancoAbreASessao() {
        var dados = service.validar(jwtNaVersao(0));

        assertThat(dados).isPresent();
        assertThat(dados.get().usuarioId()).isEqualTo(ana.id());
        assertThat(dados.get().email()).isEqualTo("ana@exemplo.com");
        assertThat(dados.get().role()).isEqualTo(Role.ALUNO);
    }

    @Test
    void senhaTrocadaDepoisDaEmissaoDerrubaOToken() {
        String emitidoAntes = jwtNaVersao(0);

        usuarios.alterarSenha(ana.id(), "hash-novo", AGORA);

        assertThat(service.validar(emitidoAntes)).isEmpty();
        assertThat(service.validar(jwtNaVersao(1))).as("o token da versão nova vale").isPresent();
    }

    @Test
    void versaoAcimaDaDoBancoTambemNaoVale() {
        assertThat(service.validar(jwtNaVersao(5))).isEmpty();
    }

    @Test
    void contaApagadaDerrubaOToken() {
        String jwt = jwtNaVersao(0);

        usuarios.excluirPorId(ana.id());

        assertThat(service.validar(jwt)).isEmpty();
    }

    @Test
    void jwtInvalidoNemConsultaOBanco() {
        assertThat(service.validar("qualquer-coisa")).isEmpty();
        assertThat(service.validar(null)).isEmpty();

        assertThat(usuarios.consultasDeVersao).isZero();
    }

    @Test
    void jwtValidoConsultaABancoUmaVez() {
        service.validar(jwtNaVersao(0));

        assertThat(usuarios.consultasDeVersao).isEqualTo(1);
    }

    @Test
    void fluxoCompletoComOJwtDeVerdade() {
        var jwt = new JwtTokenAdapter("segredo-de-teste-com-pelo-menos-32-caracteres", "studymind-redacao", 60);
        var real = new ValidarSessaoService(jwt, usuarios);
        String antigo = jwt.gerar(ana).token();
        assertThat(real.validar(antigo)).isPresent();

        var alterado = usuarios.alterarSenha(ana.id(), "hash-novo", AGORA).orElseThrow();
        String novo = jwt.gerar(alterado).token();

        assertThat(real.validar(antigo)).as("token de antes da troca").isEmpty();
        assertThat(real.validar(novo)).as("token emitido depois da troca").isPresent();
    }
}
