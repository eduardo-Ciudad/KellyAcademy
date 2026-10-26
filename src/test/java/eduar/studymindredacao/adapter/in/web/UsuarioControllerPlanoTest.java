package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.ConsultarEvolucaoService;
import eduar.studymindredacao.application.usecase.ConsultarPlanoService;
import eduar.studymindredacao.application.usecase.ConsultarUsoDiarioService;
import eduar.studymindredacao.application.usecase.GerenciarMeusTemasService;
import eduar.studymindredacao.application.usecase.PlanoDoUsuario;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /usuarios/me/plano com a SecurityConfig real. */
@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, UsuarioControllerPlanoTest.Relogio.class})
class UsuarioControllerPlanoTest {

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarPlanoService consultarPlano;
    @MockitoBean
    private ConsultarUsoDiarioService consultarUsoDiario;
    @MockitoBean
    private ConsultarEvolucaoService consultarEvolucao;
    @MockitoBean
    private GerenciarMeusTemasService gerenciarMeusTemas;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    private UUID usuarioId;
    private String jwt;

    @BeforeEach
    void abrirSessao() {
        usuarioId = UUID.randomUUID();
        jwt = "jwt-" + usuarioId;
        when(validarSessao.validar(jwt))
                .thenReturn(Optional.of(new DadosToken(usuarioId, "ana@exemplo.com", Role.ALUNO, 0)));
    }

    @Test
    void semLoginResponde401SemConsultarOPlano() throws Exception {
        mvc.perform(get("/usuarios/me/plano"))
                .andExpect(status().isUnauthorized());

        verify(consultarPlano, never()).detalhar(any());
    }

    @Test
    void alunoGratisRecebeOPlanoSemAssinatura() throws Exception {
        when(consultarPlano.detalhar(usuarioId)).thenReturn(PlanoDoUsuario.gratis());

        mvc.perform(get("/usuarios/me/plano").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano").value("GRATIS"))
                .andExpect(jsonPath("$.limites.correcoesPorDia").value(3))
                .andExpect(jsonPath("$.limites.transcricoesPorDia").value(2))
                .andExpect(jsonPath("$.recursos").isEmpty())
                .andExpect(jsonPath("$.assinatura").doesNotExist());
    }

    @Test
    void alunoProRecebeRecursosEAssinaturaSemIdDoStripe() throws Exception {
        var agora = OffsetDateTime.of(2026, 10, 26, 15, 0, 0, 0, ZoneOffset.UTC);
        var assinatura = Assinatura.nova(usuarioId, "sub_segredo", CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, agora.plusMonths(1), false, agora);
        when(consultarPlano.detalhar(usuarioId)).thenReturn(PlanoDoUsuario.pro(assinatura));

        mvc.perform(get("/usuarios/me/plano").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano").value("PRO"))
                .andExpect(jsonPath("$.limites.correcoesPorDia").value(10))
                .andExpect(jsonPath("$.recursos.length()").value(3))
                .andExpect(jsonPath("$.assinatura.ciclo").value("MENSAL"))
                .andExpect(jsonPath("$.assinatura.cancelarNoFimDoPeriodo").value(false))
                .andExpect(content().string(not(containsString("sub_segredo"))));
    }
}
