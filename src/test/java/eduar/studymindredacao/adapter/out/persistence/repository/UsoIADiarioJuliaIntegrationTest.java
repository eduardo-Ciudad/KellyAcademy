package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contador diário da Júlia (V23) direto no Postgres.
 * Usa uma data bem no futuro para a soma global não misturar com dados reais do banco local.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UsoIADiarioRepositoryAdapter.class, UsuarioRepositoryAdapter.class})
class UsoIADiarioJuliaIntegrationTest {
    private static final LocalDate DIA = LocalDate.of(2099, 1, 15);
    private static final int LIMITE = 20;

    @Autowired
    private UsoIADiarioRepositoryPort usos;
    @Autowired
    private UsuarioRepositoryPort usuarios;

    private UUID usuarioId;

    @BeforeEach
    void criarUsuario() {
        usuarioId = novoUsuario();
    }

    @Test
    void semUsoNoDiaContaZero() {
        assertThat(usos.contarMensagensJulia(usuarioId, DIA)).isZero();
    }

    @Test
    void primeiraReservaCriaALinhaSemMexerNosOutrosContadores() {
        assertThat(usos.reservarMensagemJulia(usuarioId, DIA, LIMITE)).isTrue();

        assertThat(usos.contarMensagensJulia(usuarioId, DIA)).isEqualTo(1);
        var uso = usos.buscarPorUsuarioIdEData(usuarioId, DIA).orElseThrow();
        assertThat(uso.qtdCorrecoes()).isZero();
        assertThat(uso.qtdTranscricoes()).isZero();
    }

    @Test
    void recusaAReservaAlemDoLimiteDoDia() {
        for (int i = 0; i < LIMITE; i++) {
            assertThat(usos.reservarMensagemJulia(usuarioId, DIA, LIMITE)).isTrue();
        }

        assertThat(usos.reservarMensagemJulia(usuarioId, DIA, LIMITE)).isFalse();
        assertThat(usos.contarMensagensJulia(usuarioId, DIA)).isEqualTo(LIMITE);
    }

    @Test
    void reservaDaJuliaNaoConsomeACotaDeCorrecao() {
        usos.reservarCorrecao(usuarioId, DIA, 3);

        assertThat(usos.reservarMensagemJulia(usuarioId, DIA, LIMITE)).isTrue();
        assertThat(usos.contarMensagensJulia(usuarioId, DIA)).isEqualTo(1);
        assertThat(usos.buscarPorUsuarioIdEData(usuarioId, DIA).orElseThrow().qtdCorrecoes()).isEqualTo(1);
    }

    @Test
    void liberarDevolveUmENuncaFicaNegativo() {
        usos.reservarMensagemJulia(usuarioId, DIA, LIMITE);

        usos.liberarMensagemJulia(usuarioId, DIA);
        usos.liberarMensagemJulia(usuarioId, DIA);

        assertThat(usos.contarMensagensJulia(usuarioId, DIA)).isZero();
    }

    @Test
    void limiteZeroDesligaAJulia() {
        assertThat(usos.reservarMensagemJulia(usuarioId, DIA, 0)).isFalse();
        assertThat(usos.contarMensagensJulia(usuarioId, DIA)).isZero();
    }

    @Test
    void somaDoDiaJuntaTodosOsUsuariosESoAqueleDia() {
        var outro = novoUsuario();
        usos.reservarMensagemJulia(usuarioId, DIA, LIMITE);
        usos.reservarMensagemJulia(usuarioId, DIA, LIMITE);
        usos.reservarMensagemJulia(outro, DIA, LIMITE);
        usos.reservarMensagemJulia(outro, DIA.plusDays(1), LIMITE);

        assertThat(usos.somarMensagensJuliaDoDia(DIA)).isEqualTo(3);
        assertThat(usos.contarMensagensJulia(outro, DIA.plusDays(1))).isEqualTo(1);
    }

    private UUID novoUsuario() {
        String email = "julia-uso-" + UUID.randomUUID() + "@exemplo.com";
        return usuarios.salvar(new Usuario(null, "Teste", email, "hash", Role.ALUNO, null, null)).id();
    }
}