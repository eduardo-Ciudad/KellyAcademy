package eduar.studymindredacao.domain.service;

import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.EvidenciaCompetencia;
import eduar.studymindredacao.domain.model.ResultadoAvaliacaoIA;

import java.util.ArrayList;
import java.util.List;

/**
 * Confere a nota da IA contra as regras objetivas da grade de correção do INEP (issue #27).
 * <p>
 * Usa as evidências que a IA devolve ({@link EvidenciaCompetencia}) e só LIMITA a nota: nunca aumenta.
 * Cada limitação vira um problema visível para o aluno na competência, com o motivo.
 * Evidência não informada (null) não aplica a regra correspondente.
 */
public final class RegrasDaGrade {

    /** Nota da competência limitada por uma regra da grade. */
    public record Ajuste(int competencia, int notaIA, int notaFinal, String motivo) {
    }

    /** Avaliação já ajustada, os ajustes feitos e as incoerências só registradas (sem mudar a nota). */
    public record Resultado(ResultadoAvaliacaoIA avaliacao, List<Ajuste> ajustes, List<String> incoerencias) {
        public Resultado {
            ajustes = List.copyOf(ajustes);
            incoerencias = List.copyOf(incoerencias);
        }
    }

    /** Limite calculado para uma competência e os motivos que o definem. */
    private static final class Limite {
        private int valor = 200;
        private final List<String> motivos = new ArrayList<>();

        void aplicar(int teto, String motivo) {
            if (teto < valor) {
                valor = teto;
                motivos.clear();
                motivos.add(motivo);
            } else if (teto == valor && valor < 200) {
                motivos.add(motivo);
            }
        }
    }

    private RegrasDaGrade() {
    }

    public static Resultado aplicar(ResultadoAvaliacaoIA resultado) {
        if (resultado.anulada()) {
            return new Resultado(resultado, List.of(), List.of());
        }
        List<CompetenciaAvaliada> competencias = new ArrayList<>();
        List<Ajuste> ajustes = new ArrayList<>();
        List<String> incoerencias = new ArrayList<>();

        for (CompetenciaAvaliada competencia : resultado.competencias()) {
            Limite limite = limiteDa(competencia);
            int notaFinal = Math.min(competencia.nota(), limite.valor);

            if (notaFinal < competencia.nota()) {
                String motivo = String.join("; ", limite.motivos);
                ajustes.add(new Ajuste(competencia.numero(), competencia.nota(), notaFinal, motivo));
                List<String> problemas = new ArrayList<>(competencia.problemasIdentificados());
                problemas.add("Nota limitada a " + notaFinal + " pela grade do INEP: " + motivo + ".");
                competencias.add(new CompetenciaAvaliada(competencia.numero(), notaFinal,
                        competencia.nivelReferencia(), competencia.resumo(), problemas, competencia.evidencia()));
            } else {
                competencias.add(competencia);
            }

            if (competencia.nota() < 200 && competencia.problemasIdentificados().isEmpty()) {
                incoerencias.add("C" + competencia.numero() + ": nota " + competencia.nota() + " sem problema listado");
            }
        }

        if (ajustes.isEmpty()) {
            return new Resultado(resultado, ajustes, incoerencias);
        }
        var ajustado = new ResultadoAvaliacaoIA(
                resultado.anulada(),
                resultado.motivoAnulacao(),
                competencias,
                resultado.pontosFortes(),
                resultado.pontosDesenvolvimento(),
                resultado.diagnostico(),
                resultado.modelo(),
                resultado.tokensEntrada(),
                resultado.tokensSaida(),
                resultado.respostaBrutaJson());
        return new Resultado(ajustado, ajustes, incoerencias);
    }

    private static Limite limiteDa(CompetenciaAvaliada competencia) {
        EvidenciaCompetencia e = competencia.evidencia();
        Limite limite = new Limite();
        switch (competencia.numero()) {
            case 1 -> {
                if (maiorQue(e.falhasEstruturais(), 1)) {
                    limite.aplicar(160, "o nível 200 admite no máximo 1 falha estrutural e foram encontradas "
                            + e.falhasEstruturais());
                }
                if (maiorQue(e.desvios(), 2)) {
                    limite.aplicar(160, "o nível 200 admite no máximo 2 desvios e foram encontrados " + e.desvios());
                }
            }
            case 2 -> {
                if (e.repertoriosProdutivos() != null && e.repertoriosProdutivos() == 0) {
                    limite.aplicar(160, "o nível 200 exige ao menos um repertório com uso produtivo, "
                            + "ligado explicitamente ao argumento");
                }
            }
            case 3 -> {
                if (maiorQue(e.lacunas(), 0)) {
                    limite.aplicar(160, "o nível 200 exige desenvolvimento sem lacunas e foram encontradas "
                            + e.lacunas());
                }
            }
            case 4 -> {
                Integer operadores = e.operadoresInterparagrafos();
                if (operadores != null && operadores == 0) {
                    limite.aplicar(120, "não há operador argumentativo entre os parágrafos "
                            + "(o nível 160 exige ao menos 1)");
                } else if (operadores != null && operadores < 2) {
                    limite.aplicar(160, "o nível 200 exige operadores argumentativos entre parágrafos "
                            + "pelo menos 2 vezes e foi encontrado " + operadores);
                }
                if (maiorQue(e.inadequacoes(), 0)) {
                    limite.aplicar(160, "o nível 200 não admite inadequações coesivas e foram encontradas "
                            + e.inadequacoes());
                }
                if (maiorQue(e.repeticoes(), 1)) {
                    limite.aplicar(160, "o nível 200 exige repetições raras ou ausentes e foram encontradas "
                            + e.repeticoes());
                }
            }
            case 5 -> {
                Integer elementos = e.elementosProposta();
                if (elementos != null && elementos < 5) {
                    int teto = Math.max(elementos, 1) * 40;
                    limite.aplicar(teto, "a proposta tem " + elementos + " de 5 elementos válidos "
                            + "(agente, ação, meio, finalidade e detalhamento)");
                }
            }
            default -> {
            }
        }
        return limite;
    }

    private static boolean maiorQue(Integer valor, int limite) {
        return valor != null && valor > limite;
    }
}
