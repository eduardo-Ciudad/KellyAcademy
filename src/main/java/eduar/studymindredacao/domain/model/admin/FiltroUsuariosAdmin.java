package eduar.studymindredacao.domain.model.admin;

import eduar.studymindredacao.domain.model.Validacoes;
import eduar.studymindredacao.domain.model.enums.Plano;

/**
 * Filtros da listagem de usuários do painel admin. Todos opcionais (null = sem filtro).
 *
 * @param busca           texto livre procurado em nome e e-mail, sem diferenciar acento nem maiúscula; em branco vira null
 * @param plano           GRATIS ou PRO (pela mesma regra do plano: assinatura válida ou concessão vigente)
 * @param emailVerificado true = só verificados; false = só pendentes
 * @param pagina          índice da página, começando em 0
 * @param tamanho         itens por página, de 1 a TAMANHO_MAXIMO
 */
public record FiltroUsuariosAdmin(String busca, Plano plano, Boolean emailVerificado, int pagina, int tamanho) {
    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 50;
    public static final int BUSCA_MAXIMO = 100;

    public FiltroUsuariosAdmin {
        busca = buscaOpcional(busca);
        if (pagina < 0) {
            throw new IllegalArgumentException("pagina não pode ser negativa");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("tamanho deve estar entre 1 e " + TAMANHO_MAXIMO);
        }
    }

    /** Sem filtros, com o tamanho padrão. */
    public static FiltroUsuariosAdmin semFiltros(int pagina) {
        return new FiltroUsuariosAdmin(null, null, null, pagina, TAMANHO_PADRAO);
    }

    /** Primeiro item da página (para o OFFSET). Em long para página alta não estourar int. */
    public long inicio() {
        return (long) pagina * tamanho;
    }

    private static String buscaOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        var limpa = valor.strip();
        Validacoes.validarTamanhoMaximo(limpa, "busca", BUSCA_MAXIMO);
        return limpa;
    }
}
