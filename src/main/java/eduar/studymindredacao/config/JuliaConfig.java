package eduar.studymindredacao.config;

import eduar.studymindredacao.application.usecase.PoliticaJulia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Limites da Júlia lidos das properties julia.limites.*.
 * Um valor incoerente (ex.: histórico ímpar, inatividade 0) derruba a subida, em vez de aparecer só na primeira pergunta.
 */
@Configuration
public class JuliaConfig {

    @Bean
    PoliticaJulia politicaJulia(
            @Value("${julia.limites.por-conversa}") int porConversa,
            @Value("${julia.limites.tamanho-pergunta}") int tamanhoPergunta,
            @Value("${julia.limites.historico}") int historico,
            @Value("${julia.limites.inatividade-horas}") int inatividadeHoras,
            @Value("${julia.limites.diario}") int limiteDiario,
            @Value("${julia.limites.global}") int tetoGlobal
    ) {
        return PoliticaJulia.de(porConversa, tamanhoPergunta, historico, inatividadeHoras, limiteDiario, tetoGlobal);
    }
}
