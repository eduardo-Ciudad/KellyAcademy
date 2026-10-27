package eduar.studymindredacao.adapter.in.web.security;

import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Regra de /admin na SecurityConfig real. Usa um controller falso porque o que se testa é a regra de acesso,
 * não o painel: qualquer rota nova sob /admin herda a mesma proteção.
 */
@WebMvcTest(SecurityAdminTest.RotasAdminDeTeste.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, SecurityAdminTest.Relogio.class,
        SecurityAdminTest.RotasAdminDeTeste.class})
class SecurityAdminTest {

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @RestController
    static class RotasAdminDeTeste {
        @GetMapping("/admin")
        String raiz() {
            return "admin-raiz";
        }

        @GetMapping("/admin/usuarios")
        String listar() {
            return "admin-usuarios";
        }

        @PostMapping("/admin/usuarios/{id}/zerar-cota-hoje")
        String acao() {
            return "admin-acao";
        }

        @GetMapping("/administrador")
        String rotaParecida() {
            return "rota-parecida";
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ValidarSessaoService validarSessao;

    private static final String JWT_ALUNO = "jwt-aluno";
    private static final String JWT_ADMIN = "jwt-admin";

    @BeforeEach
    void abrirSessoes() {
        when(validarSessao.validar(JWT_ALUNO))
                .thenReturn(Optional.of(new DadosToken(UUID.randomUUID(), "aluno@exemplo.com", Role.ALUNO, 0)));
        when(validarSessao.validar(JWT_ADMIN))
                .thenReturn(Optional.of(new DadosToken(UUID.randomUUID(), "admin@exemplo.com", Role.ADMIN, 0)));
    }

    @Test
    void semLoginResponde401() throws Exception {
        mvc.perform(get("/admin/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenRecusadoResponde401() throws Exception {
        // validarSessao devolve vazio para token desconhecido (ex.: senha trocada derrubou a sessão)
        mvc.perform(get("/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer jwt-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alunoLogadoResponde403NaListagem() throws Exception {
        mvc.perform(get("/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT_ALUNO))
                .andExpect(status().isForbidden());
    }

    @Test
    void alunoLogadoResponde403NaRaizSemBarra() throws Exception {
        mvc.perform(get("/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT_ALUNO))
                .andExpect(status().isForbidden());
    }

    @Test
    void alunoLogadoResponde403NasAcoes() throws Exception {
        mvc.perform(post("/admin/usuarios/" + UUID.randomUUID() + "/zerar-cota-hoje")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT_ALUNO))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAcessaAsRotasDoPainel() throws Exception {
        mvc.perform(get("/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-raiz"));
        mvc.perform(get("/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-usuarios"));
        mvc.perform(post("/admin/usuarios/" + UUID.randomUUID() + "/zerar-cota-hoje")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-acao"));
    }

    @Test
    void rotaQueSoComecaComAdminNaoEhPainel() throws Exception {
        // /administrador não casa com /admin/**: continua só exigindo login
        mvc.perform(get("/administrador").header(HttpHeaders.AUTHORIZATION, "Bearer " + JWT_ALUNO))
                .andExpect(status().isOk());
    }
}
