# Onze API

Backend do **Onze — Organizador de Pelada**.

> Estado revisado em 04/09/2026 contra o código, os testes e os workflows. Esta página separa o que está implementado do que continua planejado.

## Estado por branch

| Branch | Estado |
|---|---|
| `development` | Integração atual do backend e fonte do deploy no Render. Contém autenticação, grupos, partidas, pagamentos, créditos, prazos, notificações e reposições. |
| `docs/documentation-alignment-2026-09-04` | Correções documentais baseadas em `development`; não altera regras nem código funcional. |
| `master` | Baseline inicial com somente README. **Ainda não é um backup funcional da aplicação.** |

A base funcional mais recente foi introduzida pelo commit `13cbc1a`; commits posteriores à base podem conter somente documentação. A `master` não deve receber promoção sem autorização explícita.

## Stack implementada

- Java 25
- Spring Boot 4.1.1
- Maven
- PostgreSQL 18
- Spring Data JPA e Flyway
- Spring Security, BCrypt e JWT
- Docker e Render
- Expo Push Service para notificações

**Ainda não estão implementados:** OpenAPI/Swagger, WebSocket, jogo ao vivo, lista de espera, formação de times, estatísticas e assinatura Free/Premium.

## Conta e autenticação

- `POST /api/auth/register`: cadastro e emissão imediata de access token.
- `POST /api/auth/login`: autenticação por e-mail e senha.
- `GET /api/auth/me`: dados do usuário autenticado.
- Recuperação de senha por código numérico de seis dígitos.
- Código válido por 15 minutos, reenvio limitado a uma solicitação por minuto e bloqueio após cinco tentativas inválidas.
- Senha entre 8 e 72 caracteres e armazenamento com BCrypt.
- JWT stateless com duração padrão de duas horas.
- Não existem refresh token, revogação server-side de sessão ou endpoint de logout; o aplicativo encerra a sessão removendo o token local.
- O campo `emailVerified` existe no modelo, mas o fluxo de verificação de e-mail ainda não está implementado nem é exigido no login.

## Grupos, convites e administração

- Criação, listagem e edição de grupos.
- Nome obrigatório; descrição, cidade, mascote, local e horários habituais opcionais.
- Foto por Cloudinary, limitada a 5 MB e a arquivos com tipo `image/*`.
- Convite reutilizável com código de oito caracteres, link HTTPS público e deep link `onze://`.
- Regenerar o convite invalida o código anterior sem remover membros existentes.
- Entrada idempotente: reutilizar um convite não duplica o membro.
- Valor por jogador e chave PIX podem ser definidos como padrão do grupo.

### Hierarquia administrativa

- O criador entra como `PRIMARY_ADMIN`.
- Cada grupo possui exatamente um Administrador Principal.
- Um `ADMIN` novo começa sem permissões automáticas.
- O Principal seleciona individualmente as permissões do administrador.
- Promoção exige `PROMOTE_MEMBERS`; convites exigem `ADD_MEMBERS`; remoção exige `REMOVE_MEMBERS`; edição exige `EDIT_GROUP`; partidas e financeiro administrativo exigem `SCHEDULE_GAMES`.
- Somente o Principal pode editar permissões, rebaixar administradores e transferir o cargo principal.
- O substituto do Principal precisa já ser `ADMIN`.
- Após a transferência, o antigo Principal permanece como `ADMIN` **sem permissões automáticas**.
- Membros e administradores comuns podem sair; o Principal precisa transferir o cargo antes de sair.

## Partidas e presença

- Partida avulsa ou série semanal com ocorrências independentes.
- Data, horário, fuso IANA, local, limite de 2 a 100 jogadores e observações.
- Estados de partida atuais: `SCHEDULED` e `CANCELLED`.
- Estados de presença: `PENDING`, `GOING` e `NOT_GOING`.
- No aplicativo o jogador escolhe apenas **Vou jogar** ou **Não vou**; a opção **Talvez** não existe atualmente.
- Somente `GOING` ocupa vaga.
- Ao completar as vagas, o backend gera o evento de **Time fechado**. Se a partida deixar de estar completa e voltar a completar, um novo evento pode ser gerado.
- Na série semanal, a presença da próxima rodada é aberta às 09:00 do dia seguinte à ocorrência anterior.
- É possível cancelar uma ocorrência ou encerrar toda a série antes do início.
- Lista de espera e promoção automática continuam planejadas.

## Prazos

- Toda entidade de partida armazena prazo de inscrição; a tela mobile exige o preenchimento explícito.
- Como proteção de compatibilidade, a API usa o início do jogo como prazo quando data e hora não são enviadas.
- O prazo informado precisa estar no futuro e antes do início da partida.
- Depois do prazo de inscrição, o membro não entra por conta própria.
- Em partida cobrada, a entidade também armazena prazo de pagamento; o prazo não pode ser anterior ao de inscrição nem alcançar o início do jogo.
- Depois do prazo de pagamento, somente presença `GOING` com pagamento `PENDING` é removida automaticamente.
- Pagamento `REPORTED` ou `PAID` não é removido automaticamente pelo prazo.
- Uma reposição adicionada pelo administrador após o prazo pode informar pagamento normalmente.

## Pagamentos, créditos e reposições

- Cobrança opcional com valor e chave PIX; o Onze registra estados, mas não movimenta dinheiro.
- O jogador informa **Já paguei** e o administrador com `SCHEDULE_GAMES` confirma.
- Estados de pagamento: `PENDING`, `REPORTED`, `PAID` e `CANCELLED`.
- Acertos: `REVIEW_REQUIRED`, `PENDING`, `NOT_RECEIVED`, `REFUNDED`, `CREDITED` e `RETAINED`.
- Crédito disponível pode ser reservado e aplicado na próxima partida elegível do grupo.
- Acertos podem ser resolvidos individualmente ou em lote.
- Se um jogador com pagamento informado ou confirmado sair, a vaga é liberada e o acerto fica bloqueado até ser preenchida.
- Enquanto aguarda reposição, `REFUNDED`, `CREDITED` e `RETAINED` ficam bloqueados; `NOT_RECEIVED` continua permitido quando o pagamento apenas foi informado.
- O jogador que saiu não retorna sozinho. Um administrador autorizado pode recolocá-lo ou selecionar outro membro.
- Uma entrada elegível antes do prazo também pode preencher automaticamente a vaga mais antiga aguardando reposição.
- Cancelar a partida remove a exigência de reposição para resolver os acertos.
- Jogadores veem somente seus próprios dados financeiros; Principal e `ADMIN` com `SCHEDULE_GAMES` veem e gerenciam os dados de todos.

## Notificações

- Cadastro e remoção de Expo Push Token por dispositivo.
- Jobs persistidos, deduplicados e processados em segundo plano.
- Eventos: jogo criado, presença liberada, lembretes, remoção por prazo, pagamento informado/confirmado, acerto, crédito, reposição, jogo no dia seguinte, time fechado e cancelamento.
- Lembretes são avaliados diariamente a partir das 09:00 no fuso da partida.
- Jobs inválidos após mudança de presença, pagamento ou estado são ignorados.
- Recibos do Expo/FCM e invalidação automática de tokens rejeitados ainda não estão implementados.

## Endpoints atuais

### Autenticação

| Método | Endpoint | Finalidade |
|---|---|---|
| `POST` | `/api/auth/register` | Criar conta |
| `POST` | `/api/auth/login` | Entrar |
| `GET` | `/api/auth/me` | Consultar conta autenticada |
| `POST` | `/api/auth/password-reset/request` | Solicitar código de recuperação |
| `POST` | `/api/auth/password-reset/confirm` | Confirmar código e trocar senha |

### Grupos e convites

| Método | Endpoint | Finalidade |
|---|---|---|
| `POST` / `GET` | `/api/groups` | Criar ou listar grupos |
| `PUT` | `/api/groups/{groupId}/details` | Atualizar configurações |
| `POST` | `/api/groups/{groupId}/photo` | Enviar foto |
| `POST` | `/api/groups/{groupId}/invite` | Obter/criar convite |
| `POST` | `/api/groups/{groupId}/invite/regenerate` | Regenerar convite |
| `POST` | `/api/groups/join` | Entrar pelo código |
| `GET` | `/join/{code}` | Abrir página pública do convite |
| `GET` | `/api/groups/{groupId}/members` | Listar membros |
| `PUT` | `/api/groups/{groupId}/members/{memberId}/promote` | Promover membro |
| `PUT` | `/api/groups/{groupId}/members/{memberId}/demote` | Rebaixar administrador |
| `PUT` | `/api/groups/{groupId}/members/{memberId}/permissions` | Editar permissões |
| `DELETE` | `/api/groups/{groupId}/members/{memberId}` | Remover membro |
| `PUT` | `/api/groups/{groupId}/primary-admin` | Transferir cargo principal |
| `DELETE` | `/api/groups/{groupId}/members/me` | Sair do grupo |

### Partidas, financeiro e dispositivos

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
| `GET` | `/api/groups/{groupId}/credits` | Consultar créditos |
| `DELETE` | `/api/matches/{matchId}` | Cancelar ocorrência |
| `DELETE` | `/api/match-series/{seriesId}` | Encerrar série |
| `PUT` / `DELETE` | `/api/devices/push-token` | Registrar ou remover token de push |

## Migrações Flyway

| Versão | Escopo |
|---|---|
| V1–V2 | Usuários e recuperação de senha |
| V3–V6 | Grupos, convites, Administrador Principal e permissões |
| V7 | Partidas e dispositivos de push |
| V8 | Pagamentos e eventos de notificação |
| V9 | Acertos após saída ou cancelamento |
| V10 | Carteira de créditos |
| V11 | Prazos de inscrição e pagamento |
| V12 | Reposições após saída paga |

## Qualidade e execução

- A suíte atual possui 50 testes JUnit.
- Integrações usam PostgreSQL 18 por Testcontainers e executam as migrações Flyway.
- `API CI` executa `mvn verify`.
- `Docker CI` constrói a imagem de produção.

```bash
export JWT_SECRET='use-um-segredo-local-com-pelo-menos-32-caracteres'
mvn verify
mvn spring-boot:run
docker build -t onze-api:local .
```

Valores locais padrão do banco: `jdbc:postgresql://localhost:5432/onze`, usuário `onze` e senha `onze`. Sobrescreva com `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`.

API de desenvolvimento: <https://onze-organizador-de-pelada.onrender.com>

Health check: `GET /actuator/health/readiness`