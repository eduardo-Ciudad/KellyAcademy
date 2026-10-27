package eduar.studymindredacao.adapter.in.web.dto.admin;

/** Arredondamentos de exibição do painel admin (o domínio guarda o valor com 2 casas). */
final class Arredondamento {
    private Arredondamento() {
    }

    /** Nota total: inteiro mais próximo; null continua null. */
    static Integer inteiro(Double valor) {
        return valor == null ? null : (int) Math.round(valor);
    }

    /** Média por competência: 1 casa decimal; null continua null. */
    static Double umaCasa(Double valor) {
        return valor == null ? null : Math.round(valor * 10) / 10.0;
    }
}
