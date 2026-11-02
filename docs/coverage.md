# Cobertura de linhas e ramificações — fase 1

`mvn clean verify` executa a suíte JUnit existente com JaCoCo 0.8.16 e gera
`target/site/jacoco/index.html`, `jacoco.xml` e `jacoco.csv`.
O relatório inclui todos os arquivos de produção compilados de `src/main/java`,
inclusive classes sem execução. Não há exclusão de controllers, entidades,
configuração ou regras de negócio para melhorar o percentual.

O percentual combina os testes isolados e as integrações internas com PostgreSQL.
Os fluxos HTTP e Android de `onze-automation` continuam com seus inventários
funcionais e não alimentam este arquivo de execução JaCoCo.

Para gerar também o resumo por arquivo:

```bash
python3 scripts/coverage_summary.py --test-status success
```

A CI publica o resumo na página da execução e o artifact `api-code-coverage`
por 30 dias, com HTML, XML, CSV, JSON e relatórios Surefire. Em falha dos testes,
uma medição parcial pode ser publicada, identificada pelo resultado dos testes;
ausência de dados de instrumentação falha explicitamente.

Linhas indicam instruções executadas naquela linha; ramificações (`BRANCH`)
indicam os caminhos de `if` e `switch`, incluindo os desvios gerados pelo Java.
JaCoCo não conta tratamento de exceções como branches. Esta métrica não comprova
MC/DC nem todas as combinações de condições booleanas.

Nesta fase medimos a referência inicial sem impor um percentual mínimo arbitrário.
Os relatórios por arquivo e as ramificações não cobertas orientarão as próximas fases.
