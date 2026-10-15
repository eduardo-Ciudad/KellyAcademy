package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.ResultadoAvaliacaoIA;
import eduar.studymindredacao.domain.model.SolicitacaoAvaliacao;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Mede o desvio da correção por IA contra redações com nota humana conhecida (issue #28).
 * <p>
 * Chama a API real do Gemini: fica fora do {@code mvn test} e do CI (tag "calibracao").
 * Não reprova: só gera o relatório. Ver {@code src/test/resources/calibracao/README.md}.
 */
@Tag("calibracao")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class CalibracaoRunnerTest {

    private static final Path PASTA_REDACOES = Path.of("src/test/resources/calibracao");
    private static final Path PASTA_SAIDA = Path.of("target/calibracao");
    private static final int TOLERANCIA_COMPETENCIA = 20;
    private static final int META_ERRO_MEDIO_TOTAL = 40;
    private static final int LIMITE_1000_INDEVIDO = 940;

    private final JsonMapper json = JsonMapper.builder().build();

    record RedacaoCalibracao(String id, String tema, String texto, int[] notasOficiais,
                             int totalOficial, boolean conferido) {
    }

    /** Uma execução da correção: notas C1..C5, ou erro se a chamada falhou. */
    record Execucao(int[] notas, String erro) {
        boolean ok() {
            return erro == null;
        }

        int total() {
            int soma = 0;
            for (int nota : notas) {
                soma += nota;
            }
            return soma;
        }
    }

    @Test
    void medirDesvioDaCorrecao() throws IOException {
        List<RedacaoCalibracao> redacoes = carregarRedacoes();
        assumeFalse(redacoes.isEmpty(), "nenhuma redação em " + PASTA_REDACOES.toAbsolutePath());

        // O IntelliJ desativa as condições @Enabled* ao rodar uma classe direto: confere a chave aqui também.
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY não definida: configure na Run Configuration (Environment variables) ou no terminal");
        }

        Properties app = carregarApplicationProperties();
        int repeticoes = Integer.getInteger("calibracao.repeticoes", 3);
        String modelo = System.getProperty("calibracao.modelo", app.getProperty("gemini.modelo"));
        double temperatura = Double.parseDouble(
                System.getProperty("calibracao.temperatura", app.getProperty("gemini.temperatura")));
        String baseUrl = app.getProperty("gemini.base-url");

        var adapter = new GeminiAvaliacaoAdapter(
                new PromptAvaliacaoBuilder(),
                new GeminiClient(baseUrl, apiKey, 180),
                modelo,
                temperatura);

        String carimbo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path pastaRespostas = PASTA_SAIDA.resolve("respostas").resolve(carimbo);
        Files.createDirectories(pastaRespostas);

        List<List<Execucao>> resultados = new ArrayList<>();
        for (RedacaoCalibracao redacao : redacoes) {
            List<Execucao> execucoes = new ArrayList<>();
            for (int i = 1; i <= repeticoes; i++) {
                System.out.printf("[calibracao] %s — execução %d/%d%n", redacao.id(), i, repeticoes);
                execucoes.add(executar(adapter, redacao, pastaRespostas.resolve(redacao.id() + "-" + i + ".json")));
            }
            resultados.add(execucoes);
        }

        String relatorio = montarRelatorio(redacoes, resultados, modelo, temperatura, repeticoes, carimbo);
        Path arquivo = PASTA_SAIDA.resolve("relatorio-" + carimbo + ".md");
        Files.writeString(arquivo, relatorio, StandardCharsets.UTF_8);
        System.out.println(relatorio);
        System.out.println("[calibracao] relatório salvo em " + arquivo.toAbsolutePath());
    }

    private Execucao executar(GeminiAvaliacaoAdapter adapter, RedacaoCalibracao redacao, Path arquivoResposta) {
        try {
            ResultadoAvaliacaoIA resultado = adapter.avaliar(new SolicitacaoAvaliacao(redacao.tema(), redacao.texto()));
            Files.writeString(arquivoResposta, resultado.respostaBrutaJson(), StandardCharsets.UTF_8);
            int[] notas = new int[5];
            for (CompetenciaAvaliada competencia : resultado.competencias()) {
                notas[competencia.numero() - 1] = competencia.nota();
            }
            return new Execucao(notas, null);
        } catch (RuntimeException | IOException e) {
            return new Execucao(new int[5], e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- relatório

    private String montarRelatorio(List<RedacaoCalibracao> redacoes, List<List<Execucao>> resultados,
                                   String modelo, double temperatura, int repeticoes, String carimbo) {
        var md = new StringBuilder();
        md.append("# Calibração da correção — ").append(carimbo).append("\n\n");
        md.append("- Prompt: `").append(nomeDoPrompt()).append("`\n");
        md.append("- Modelo: `").append(modelo).append("` · temperatura ").append(temperatura).append("\n");
        md.append("- Redações: ").append(redacoes.size()).append(" · repetições: ").append(repeticoes).append("\n\n");

        md.append("| Redação | Oficial | Média IA | Desvio | C1 | C2 | C3 | C4 | C5 | Min–Máx | Obs. |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|\n");

        double somaErroTotal = 0;
        int redacoesMedidas = 0;
        int acertos = 0;
        int comparacoes = 0;
        int indevidos1000 = 0;
        int falhas = 0;
        double[] somaVies = new double[5];
        int[] contagemVies = new int[5];

        for (int r = 0; r < redacoes.size(); r++) {
            RedacaoCalibracao redacao = redacoes.get(r);
            List<Execucao> validas = resultados.get(r).stream().filter(Execucao::ok).toList();
            falhas += resultados.get(r).size() - validas.size();

            String obs = redacao.conferido() ? "" : "transcrição não conferida";
            if (validas.isEmpty()) {
                md.append(String.format("| %s | %d | — | — | — | — | — | — | — | — | todas as execuções falharam |%n",
                        redacao.id(), redacao.totalOficial()));
                continue;
            }

            double mediaTotal = validas.stream().mapToInt(Execucao::total).average().orElse(0);
            int min = validas.stream().mapToInt(Execucao::total).min().orElse(0);
            int max = validas.stream().mapToInt(Execucao::total).max().orElse(0);
            double desvio = mediaTotal - redacao.totalOficial();
            somaErroTotal += Math.abs(desvio);
            redacoesMedidas++;

            String[] colunas = new String[5];
            for (int c = 0; c < 5; c++) {
                final int indice = c;
                double media = validas.stream().mapToInt(e -> e.notas()[indice]).average().orElse(0);
                colunas[c] = String.format(Locale.ROOT, "%.0f (%s)", media, redacao.notasOficiais()[c]);
                for (Execucao execucao : validas) {
                    int diferenca = execucao.notas()[c] - redacao.notasOficiais()[c];
                    somaVies[c] += diferenca;
                    contagemVies[c]++;
                    comparacoes++;
                    if (Math.abs(diferenca) <= TOLERANCIA_COMPETENCIA) {
                        acertos++;
                    }
                }
            }
            for (Execucao execucao : validas) {
                if (execucao.total() == 1000 && redacao.totalOficial() < LIMITE_1000_INDEVIDO) {
                    indevidos1000++;
                }
            }

            md.append(String.format(Locale.ROOT, "| %s | %d | %.0f | %+.0f | %s | %s | %s | %s | %s | %d–%d | %s |%n",
                    redacao.id(), redacao.totalOficial(), mediaTotal, desvio,
                    colunas[0], colunas[1], colunas[2], colunas[3], colunas[4], min, max, obs));
        }

        md.append("\nEntre parênteses: nota oficial da competência.\n\n");

        md.append("## Viés médio por competência (IA − oficial)\n\n");
        md.append("| C1 | C2 | C3 | C4 | C5 |\n|---|---|---|---|---|\n|");
        for (int c = 0; c < 5; c++) {
            double vies = contagemVies[c] == 0 ? 0 : somaVies[c] / contagemVies[c];
            md.append(String.format(Locale.ROOT, " %+.0f |", vies));
        }
        md.append("\n\nPositivo = IA dá nota maior que o corretor humano.\n\n");

        double erroMedio = redacoesMedidas == 0 ? 0 : somaErroTotal / redacoesMedidas;
        double taxaAcerto = comparacoes == 0 ? 0 : 100.0 * acertos / comparacoes;
        boolean metaOk = redacoesMedidas > 0 && erroMedio <= META_ERRO_MEDIO_TOTAL && indevidos1000 == 0;

        md.append("## Resumo\n\n");
        if (redacoesMedidas == 0) {
            md.append("- **Nenhuma execução válida**: métricas indisponíveis (ver Falhas).\n");
        }
        md.append(redacoesMedidas == 0
                ? String.format("- Erro médio no total: **—** (meta ≤ %d)%n", META_ERRO_MEDIO_TOTAL)
                : String.format(Locale.ROOT, "- Erro médio no total: **%.0f** (meta ≤ %d)%n", erroMedio, META_ERRO_MEDIO_TOTAL));
        md.append(String.format(Locale.ROOT, "- Acertos por competência (erro ≤ %d): **%.0f%%** (%d de %d)%n",
                TOLERANCIA_COMPETENCIA, taxaAcerto, acertos, comparacoes));
        md.append(String.format("- Notas 1000 em redação oficial abaixo de %d: **%d**%n", LIMITE_1000_INDEVIDO, indevidos1000));
        md.append(String.format("- Execuções com falha: %d%n", falhas));
        md.append("- Meta da #28: **").append(metaOk ? "OK" : "NÃO ATINGIDA").append("**\n");

        if (falhas > 0) {
            md.append("\n## Falhas\n\n");
            for (int r = 0; r < redacoes.size(); r++) {
                for (Execucao execucao : resultados.get(r)) {
                    if (!execucao.ok()) {
                        md.append("- ").append(redacoes.get(r).id()).append(": ").append(execucao.erro()).append("\n");
                    }
                }
            }
        }
        return md.toString();
    }

    // ---------------------------------------------------------------- carga

    private List<RedacaoCalibracao> carregarRedacoes() throws IOException {
        if (!Files.isDirectory(PASTA_REDACOES)) {
            return List.of();
        }
        List<Path> arquivos;
        try (Stream<Path> stream = Files.list(PASTA_REDACOES)) {
            arquivos = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        List<RedacaoCalibracao> redacoes = new ArrayList<>();
        for (Path arquivo : arquivos) {
            redacoes.add(lerRedacao(arquivo));
        }
        return redacoes;
    }

    private RedacaoCalibracao lerRedacao(Path arquivo) throws IOException {
        JsonNode no = json.readTree(Files.readString(arquivo, StandardCharsets.UTF_8));
        JsonNode notas = no.path("notas_oficiais");
        int[] notasOficiais = new int[5];
        int soma = 0;
        for (int c = 0; c < 5; c++) {
            JsonNode nota = notas.path("c" + (c + 1));
            if (!nota.isInt()) {
                throw new IllegalStateException(arquivo + ": notas_oficiais.c" + (c + 1) + " ausente");
            }
            notasOficiais[c] = nota.asInt();
            soma += notasOficiais[c];
        }
        int total = no.path("total_oficial").asInt(-1);
        if (total != soma) {
            throw new IllegalStateException(arquivo + ": total_oficial (" + total + ") difere da soma (" + soma + ")");
        }
        String id = no.path("id").asString("");
        String tema = no.path("tema").asString("");
        String texto = no.path("texto").asString("");
        if (id.isBlank() || tema.isBlank() || texto.isBlank()) {
            throw new IllegalStateException(arquivo + ": id, tema e texto são obrigatórios");
        }
        return new RedacaoCalibracao(id, tema, texto, notasOficiais, total, no.path("conferido").asBoolean(false));
    }

    private static Properties carregarApplicationProperties() throws IOException {
        var propriedades = new Properties();
        try (InputStream in = CalibracaoRunnerTest.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException("application.properties não encontrado no classpath");
            }
            propriedades.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return propriedades;
    }

    /** Lido do PromptAvaliacaoBuilder para o relatório registrar qual versão do prompt foi medida. */
    private static String nomeDoPrompt() {
        try {
            var campo = PromptAvaliacaoBuilder.class.getDeclaredField("RECURSO");
            campo.setAccessible(true);
            return (String) campo.get(null);
        } catch (ReflectiveOperationException e) {
            return "desconhecido";
        }
    }
}
