# Onze API

Backend do **Onze — Organizador de Pelada**.

> Estado revisado em 08/09/2026 contra o código, os testes e os workflows. Esta página separa o que está implementado do que continua planejado.

## Estado por branch

| Branch | Estado |
|---|---|
| `development` | Integração atual do backend e fonte do deploy no Render. Contém autenticação, grupos, jogos, pagamentos, créditos, prazos, notificações e reposições. |
| `master` | Branch de release estável; recebe somente versões validadas em `development` e autorizadas explicitamente. |

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

**Ainda não estão implementados:** OpenAPI/Swagger, lista de espera, estatísticas avançadas e assinatura Free/Premium. A atualização do jogo ao vivo usa SSE.

## Linguagem do produto

- Respostas, erros e notificações destinadas ao usuário usam **jogo** ou **jogos**, em alinhamento com o aplicativo.
- Identificadores técnicos existentes, como `Match`, rotas `/matches` e nomes de banco, permanecem inalterados.
- Mensagens escritas nas exceções de domínio são exclusivamente internas, para log e depuração. Nenhum endpoint deve devolver `exception.getMessage()` ao cliente.
- Erros destinados ao cliente usam `ApiErrorResponse` e texto em português definido por um `@ExceptionHandler`. Falhas inesperadas são registradas no servidor e retornam somente uma mensagem genérica, sem detalhes internos.

## Conta e autenticação

- `POST /api/auth/register`: cadastro e emissão imediata de access token.
- `POST /api/auth/login`: autenticação por e-mail e senha.
- `GET /api/auth/me`: dados do usuário autenticado.
- Recuperação de senha por código numérico de seis dígitos.
- Código válido por 15 minutos, reenvio limitado a uma solicitação por minuto e bloqueio após cinco tentativas inválidas.
- Senha entre 8 e 72 caracteres e armazenamento com BCrypt.
- O login limita tentativas por conta em uma janela de 15 minutos. A partir da quinta falha, aplica bloqueio progressivo de 1 a 15 minutos; um login válido ou a troca de senha limpa o bloqueio.
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
- Promoção exige `PROMOTE_MEMBERS`; convites exigem `ADD_MEMBERS`; remoção exige `REMOVE_MEMBERS`; edição exige `EDIT_GROUP`; jogos e financeiro administrativo exigem `SCHEDULE_GAMES`; perfis esportivos de terceiros exigem `EDIT_PLAYER_PROFILES`.
- Somente o Principal pode editar permissões, rebaixar administradores e transferir o cargo principal.
- O substituto do Principal precisa já ser `ADMIN`.
- Após a transferência, o antigo Principal permanece como `ADMIN` **sem permissões automáticas**.
- Membros e administradores comuns podem sair; o Principal precisa transferir o cargo antes de sair.

### Perfil esportivo por grupo

- Cada participação no grupo mantém suas próprias posições, disponibilidade para atuar no gol, pé dominante e nível técnico.
- Posições de linha aceitam múltipla seleção: `DEFENDER`, `MIDFIELDER`, `WINGER` e `STRIKER`.
- Atuar como goleiro é uma opção independente e pode coexistir com posições de linha.
- Pé dominante aceita `RIGHT`, `LEFT` ou `BOTH`.
- O jogador edita as próprias posições, opção de goleiro e pé dominante; o nível técnico de 1 a 5 é definido pelo Principal ou por um administrador com `EDIT_PLAYER_PROFILES`.
- Perfis antigos começam sem dados inferidos. Um perfil é considerado completo quando possui ao menos uma posição de linha ou goleiro e um pé dominante.

## Jogos e presença

- Jogo avulso ou série semanal com ocorrências independentes.
- Data, horário, fuso IANA, local, limite de 2 a 100 jogadores e observações.
- Estados de jogo atuais: `SCHEDULED` e `CANCELLED`.
- Estados de presença: `PENDING`, `GOING` e `NOT_GOING`.
- No aplicativo o jogador escolhe apenas **Vou jogar** ou **Não vou**; a opção **Talvez** não existe atualmente.
- Somente `GOING` ocupa vaga.
- Ao completar as vagas, o backend gera o evento de **Time fechado**. Se o jogo deixar de estar completo e voltar a completar, um novo evento pode ser gerado.
- Cada série semanal mantém uma janela móvel de quatro jogos ativos para a Home e para a tela do grupo. Ao finalizar ou cancelar o mais próximo, a API cria a ocorrência seguinte depois da quarta; séries ativas antigas são completadas automaticamente ao serem processadas.
- Na série semanal, a presença da próxima rodada é aberta às 09:00 do dia seguinte à ocorrência anterior.
- É possível cancelar uma ocorrência ou encerrar toda a série antes do início.
- Lista de espera e promoção automática continuam planejadas.

## Prazos

- Toda entidade de jogo armazena prazo de inscrição; a tela mobile exige o preenchimento explícito.
- Como proteção de compatibilidade, a API usa o início do jogo como prazo quando data e hora não são enviadas.
- O prazo informado precisa estar no futuro e antes do início do jogo.
- Depois do prazo de inscrição, o membro não entra por conta própria.
- Em jogo cobrado, a entidade também armazena prazo de pagamento; o prazo não pode ser anterior ao de inscrição nem alcançar o início do jogo.
- Depois do prazo de pagamento, somente presença `GOING` com pagamento `PENDING` é removida automaticamente.
- Pagamento `REPORTED` ou `PAID` não é removido automaticamente pelo prazo.
- Uma reposição adicionada pelo administrador após o prazo pode informar pagamento normalmente.

## Pagamentos, créditos e reposições

- Cobrança opcional com valor e chave PIX; o Onze registra estados, mas não movimenta dinheiro.
- O jogador informa **Já paguei** e o administrador com `SCHEDULE_GAMES` confirma.
- Estados de pagamento: `PENDING`, `REPORTED`, `PAID` e `CANCELLED`.
- Acertos: `REVIEW_REQUIRED`, `PENDING`, `NOT_RECEIVED`, `REFUNDED`, `CREDITED` e `RETAINED`.
- Crédito disponível pode ser reservado e aplicado no próximo jogo elegível do grupo.
- Acertos podem ser resolvidos individualmente ou em lote.
- Se um jogador com pagamento informado ou confirmado sair, a vaga é liberada e o acerto fica bloqueado até ser preenchida.
- Enquanto aguarda reposição, `REFUNDED`, `CREDITED` e `RETAINED` ficam bloqueados; `NOT_RECEIVED` continua permitido quando o pagamento apenas foi informado.
- O jogador que saiu não retorna sozinho. Um administrador autorizado pode recolocá-lo ou selecionar outro membro.
- Uma entrada elegível antes do prazo também pode preencher automaticamente a vaga mais antiga aguardando reposição.
- Cancelar o jogo remove a exigência de reposição para resolver os acertos.
- Jogadores veem somente seus próprios dados financeiros; Principal e `ADMIN` com `SCHEDULE_GAMES` veem e gerenciam os dados de todos.

## Notificações

- Cadastro e remoção de Expo Push Token por dispositivo.
- Jobs persistidos, deduplicados e processados em segundo plano.
- Eventos: jogo criado, presença liberada, lembretes, remoção por prazo, pagamento informado/confirmado, acerto, crédito, reposição, jogo no dia seguinte, time fechado e cancelamento.
- Lembretes são avaliados diariamente a partir das 09:00 no fuso do jogo.
- Jobs inválidos após mudança de presença, pagamento ou estado são ignorados.
- Cada lote aceito pelo Expo registra em `INFO` as quantidades enviadas e desativadas, sem expor o token.
- Uma tentativa com erro registra `WARN`; a desistência após a quinta tentativa registra `ERROR` com jogo, destinatário, tipo e motivo.
- Tokens rejeitados de forma síncrona como `DeviceNotRegistered` são desativados e registrados em `INFO`.
- A consulta assíncrona de recibos do Expo/FCM ainda não está implementada.

## Jogo ao vivo e SSE

- O feed geral mantém somente as permissões dos grupos que o usuário pode acompanhar.
- Uma conexão aberta para um jogo específico mantém apenas a permissão do grupo daquele jogo e valida a participação antes de ser aceita.
- Conexões específicas são indexadas por `matchId`; um gol, cartão ou placar percorre somente o feed geral e as conexões do jogo alterado.
- O heartbeat continua percorrendo todas as conexões ativas porque cada cliente precisa receber o keepalive.
- Abertura e encerramento de conexão registram em `INFO` a contagem total e a contagem do escopo, permitindo acompanhar o uso no Render.

### Tempos, prorrogação e pênaltis

- Um jogo novo pode controlar de 1 a 4 tempos normais de duração igual. Jogos antigos continuam com o cronômetro original.
- Cada tempo aceita acréscimos de 0 a 180 minutos. O administrador encerra o tempo após a duração e os acréscimos; os intervalos param o relógio até iniciar o próximo.
- Com exatamente dois times, prorrogação opcional (1 a 4 tempos) começa apenas se o placar normal terminar empatado. Os pênaltis são opcionais e podem ser ativados mesmo sem prorrogação.
- Na disputa, o administrador define cinco batedores por time e registra cobranças alternadas. Após a quinta rodada, escolhe o próximo batedor a cada rodada até haver vencedor. A confirmação do vencedor encerra o jogo.
- As cobranças e o vencedor ficam em tabelas próprias: não alteram o placar de gols, os gols de jogadores ou as métricas de gols dos times. A vitória nos pênaltis conta como vitória nas estatísticas.

## Estatísticas básicas

- Os totais e rankings são derivados de todo o histórico de jogos encerrados, sem contadores paralelos.
- O histórico retornado ainda não possui paginação. Uma evolução futura deve paginar somente os itens históricos sem limitar os dados usados nos totais e rankings.

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
| `GET` | `/api/groups/{groupId}/statistics` | Consultar resumo, rankings e histórico de jogos encerrados |
| `GET` | `/api/groups/{groupId}/statistics/players/{userId}` | Consultar estatísticas e histórico de um jogador |
| `GET` / `PUT` | `/api/groups/{groupId}/members/me/sports-profile` | Consultar ou editar o próprio perfil esportivo |
| `GET` / `PUT` | `/api/groups/{groupId}/members/{memberId}/sports-profile` | Consultar ou editar perfil como administrador autorizado |
| `PUT` | `/api/groups/{groupId}/members/{memberId}/promote` | Promover membro |
| `PUT` | `/api/groups/{groupId}/members/{memberId}/demote` | Rebaixar administrador |
| `PUT` | `/api/groups/{groupId}/members/{memberId}/permissions` | Editar permissões |
| `DELETE` | `/api/groups/{groupId}/members/{memberId}` | Remover membro |
| `PUT` | `/api/groups/{groupId}/primary-admin` | Transferir cargo principal |
| `DELETE` | `/api/groups/{groupId}/members/me` | Sair do grupo |

### Jogos, financeiro e dispositivos

| Método | Endpoint | Finalidade |
|---|---|---|
| `POST` | `/api/groups/{groupId}/matches` | Criar jogo ou série |
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
| `PUT` | `/api/matches/{matchId}/live/period/added-time` | Definir acréscimos do tempo atual |
| `PUT` | `/api/matches/{matchId}/live/period/finish` | Encerrar tempo após o relógio previsto; concluir jogo ou preparar etapa seguinte |
| `PUT` | `/api/matches/{matchId}/live/period/start-next` | Iniciar o próximo tempo normal ou de prorrogação |
| `PUT` | `/api/matches/{matchId}/live/penalties/lineup` | Definir os primeiros cinco batedores por time |
| `POST` | `/api/matches/{matchId}/live/penalties/attempts` | Registrar gol ou erro da próxima cobrança |
| `PUT` | `/api/matches/{matchId}/live/penalties/confirm-winner` | Confirmar vencedor e encerrar jogo |
| `PUT` / `DELETE` | `/api/devices/push-token` | Registrar ou remover token de push |

## Migrações Flyway

| Versão | Escopo |
|---|---|
| V1–V2 | Usuários e recuperação de senha |
| V3–V6 | Grupos, convites, Administrador Principal e permissões |
| V7 | Jogos e dispositivos de push |
| V8 | Pagamentos e eventos de notificação |
| V9 | Acertos após saída ou cancelamento |
| V10 | Carteira de créditos |
| V11 | Prazos de inscrição e pagamento |
| V12 | Reposições após saída paga |
| V13 | Perfil esportivo por participação no grupo |
| V14–V27 | Formação de times, convidados, goleiros, jogo ao vivo e identidades persistentes dos times |
| V28 | Proteção persistente contra tentativas repetidas de login |
| V29 | Tempos, acréscimos, prorrogação e disputa por pênaltis |
| V30 | Goleiros excedentes de escalações antigas passam para a reserva |

## Qualidade e execução

- A suíte JUnit é executada integralmente pelo Maven; a contagem efetiva e eventuais testes ignorados ficam registrados no sumário da CI.
- Integrações usam um PostgreSQL 18 compartilhado durante a suíte; os dados são limpos entre classes e as migrações Flyway são preservadas.
- `API CI` executa `mvn verify`.
- `Docker CI` constrói a imagem de produção.
- Spotless está disponível para formatar Java com `mvn spotless:apply`. A verificação automática de todo o código será ativada após a formatação da base existente.

```bash
export JWT_SECRET='use-um-segredo-local-com-pelo-menos-32-caracteres'
mvn verify
mvn spring-boot:run
docker build -t onze-api:local .
```

Valores locais padrão do banco: `jdbc:postgresql://localhost:5432/onze`, usuário `onze` e senha `onze`. Sobrescreva com `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`.

API de desenvolvimento: <https://onze-organizador-de-pelada.onrender.com>

Health check: `GET /actuator/health/readiness`
