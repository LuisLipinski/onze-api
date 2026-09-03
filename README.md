# Onze API

Backend do **Onze — Organizador de Pelada**.

> Estado documentado em 03/09/2026: a branch `development` está publicada no Render e inclui autenticação, grupos, administração, partidas, presença, pagamentos, créditos, prazos, notificações e reposições após saída paga. A `master` permanece como referência estável.

## Stack

- Java 25
- Spring Boot 4.1.1
- Maven
- PostgreSQL 18
- Spring Data JPA e Flyway
- Spring Security, BCrypt e JWT
- Docker e Render
- Expo Push Service para notificações

## Funcionalidades implementadas

### Conta e autenticação

- Cadastro, login e endpoint da sessão autenticada.
- Recuperação de senha por código temporário.
- JWT stateless e senhas armazenadas com BCrypt.

### Grupos e administração

- Criação, listagem e edição de grupos.
- Foto via Cloudinary e convite reutilizável por link/código.
- Administrador Principal, promoção, rebaixamento e transferência do cargo.
- Permissões administrativas individuais.
- Valor por jogador, chave PIX, local e horários habituais como padrões do grupo.

### Partidas e presença

- Partida avulsa ou série semanal.
- Data, horário, fuso, local, limite de jogadores e observações.
- Presença `PENDING`, `GOING` ou `NOT_GOING`.
- Prazo de inscrição e prazo de pagamento.
- Cancelamento de uma ocorrência ou encerramento da série.
- Aviso quando o limite de jogadores é atingido.

### Pagamentos, créditos e reposições

- Cobrança opcional por partida com valor e chave PIX.
- Jogador informa o pagamento; administrador confirma.
- Crédito reservado e aplicado na próxima partida elegível do grupo.
- Acertos individuais ou em lote: não recebido, reembolso, crédito ou retenção.
- Remoção automática do jogador ainda pendente após o prazo de pagamento.
- Jogador pago pode sair, mas o acerto fica bloqueado até outra pessoa ocupar a vaga.
- O administrador pode adicionar o próprio jogador novamente ou selecionar outro membro como substituto.
- O cancelamento da partida remove a exigência de reposição para resolver o acerto.

### Notificações

- Jobs persistidos, deduplicados e processados em segundo plano.
- Push remoto pelo Expo para dispositivos cadastrados.
- Eventos de criação, abertura de presença, lembretes, prazo de pagamento, pagamento informado/confirmado, crédito, reposição, jogo no dia seguinte, time fechado e cancelamento.

## Regras de prazo

- O prazo de inscrição deve estar no futuro e antes do início da partida.
- Em partidas cobradas, o prazo de pagamento não pode ser anterior ao prazo de inscrição nem alcançar o início do jogo.
- Depois do prazo de inscrição, o membro não entra por conta própria.
- Depois do prazo de pagamento, uma presença confirmada ainda pendente é removida automaticamente.
- Uma reposição adicionada pelo administrador depois do prazo pode informar o pagamento normalmente.

## Principais endpoints de partidas

| Método | Endpoint | Finalidade |
|---|---|---|
| `POST` | `/api/groups/{groupId}/matches` | Criar partida ou série |
| `GET` | `/api/matches/upcoming` | Listar próximos jogos do usuário |
| `GET` | `/api/groups/{groupId}/matches` | Listar jogos do grupo |
| `GET` | `/api/matches/{matchId}` | Consultar detalhe e permissões efetivas |
| `PUT` | `/api/matches/{matchId}/attendance` | Responder presença |
| `PUT` | `/api/matches/{matchId}/payment/reported` | Informar pagamento |
| `PUT` | `/api/matches/{matchId}/payments/{playerUserId}/confirm` | Confirmar pagamento |
| `PUT` | `/api/matches/{matchId}/payments/{playerUserId}/settlement` | Resolver um acerto |
| `PUT` | `/api/matches/{matchId}/payment-settlements` | Resolver acertos em lote |
| `PUT` | `/api/matches/{matchId}/replacements/{departedUserId}` | Adicionar reposição |
| `GET` | `/api/groups/{groupId}/credits` | Consultar créditos do grupo |
| `DELETE` | `/api/matches/{matchId}` | Cancelar uma ocorrência |
| `DELETE` | `/api/match-series/{seriesId}` | Encerrar uma série |

## Migrações Flyway

| Versão | Escopo |
|---|---|
| V1–V2 | Usuários e recuperação de senha |
| V3–V6 | Grupos, convites, administrador Principal e permissões |
| V7 | Partidas e dispositivos de push |
| V8 | Pagamentos e eventos de notificação |
| V9 | Acertos após saída ou cancelamento |
| V10 | Carteira de créditos |
| V11 | Prazos de inscrição e pagamento |
| V12 | Reposições após saída paga |

## Executar localmente

Pré-requisitos: Java 25, Maven e PostgreSQL.

```bash
export JWT_SECRET='use-um-segredo-local-com-pelo-menos-32-caracteres'
mvn spring-boot:run
```

Valores locais padrão do banco:

- URL: `jdbc:postgresql://localhost:5432/onze`
- usuário: `onze`
- senha: `onze`

Sobrescreva-os com `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`.

Variáveis opcionais habilitam Resend, Cloudinary e personalização do Expo Push. Nenhuma credencial deve ser commitada.

## Validação

```bash
mvn verify
docker build -t onze-api:local .
```

O workflow **API CI** executa os testes com Java 25. Os testes de integração usam PostgreSQL 18 por Testcontainers e validam as migrações. O workflow **Docker CI** constrói a imagem de produção.

## Ambientes e branches

- `master`: versão estável; só recebe promoção após aprovação explícita.
- `development`: integração e deploy automático no Render.
- `feature/*`: funcionalidades isoladas e validadas antes do merge.
- `docs/*`: alterações documentais.

API de desenvolvimento: <https://onze-organizador-de-pelada.onrender.com>

Health check: `GET /actuator/health/readiness`
