# api-authtest
Ferramenta de testagem de vulnerabilidades BOLA e BFLA

## Autenticação das chamadas

O utilitário lê as operações de `openapi.yaml` e a lista de contas de
`users.yaml`. Para cada usuário, autentica na API de login e percorre todos os
endpoints do OpenAPI usando o respectivo `access_token` como
`Authorization: Bearer ...`. As roles listadas no YAML identificam as
permissões atribuídas à conta no Keycloak.

`users.yaml` configura a URL `AUTHTEST_AUTH_URL` (padrão
`http://localhost:8092/auth/login`, porta publicada do Compose), os usuários e
suas roles. Dentro do Compose, o serviço `api-authtest` usa a URL interna
`http://auth-api:8081/auth/login`. As senhas são
configuráveis pelas variáveis `AUTHTEST_USER_PASSWORD`,
`AUTHTEST_ADMIN_PASSWORD` e `AUTHTEST_NOTUSER_PASSWORD`. O usuário `notuser`
não possui roles de aplicação. Depois das execuções autenticadas, o utilitário
faz também uma chamada a cada endpoint sem token para registrar as respostas
anônimas. `AUTHTEST_USERS_SPEC` e `AUTHTEST_SPEC` permitem
indicar outros arquivos YAML; `AUTHTEST_BASE_URL` define a URL base das
operações. Ao final da execução, um relatório de texto é salvo com timestamp,
por exemplo `authtest-report-20261105T215500.123456789Z.txt`, no diretório de
trabalho (ou junto ao caminho-base definido por `AUTHTEST_REPORT_PATH`). Cada
execução cria um arquivo novo; se houver colisão de timestamp, acrescenta um
sufixo numérico. O relatório registra usuário, roles, endpoints, status HTTP,
headers e corpo das respostas, sem incluir senhas ou tokens. Cada execução
mantém os relatórios das execuções anteriores.

A ferramenta usa por padrão a especificação OpenAPI em `openapi.yaml`, alinhada
ao `secure-api` (`GET /object/{id}`), e aponta para `http://localhost:8091`.
No Docker Compose, a URL interna é `http://secure-api:8080`.

## Configuração de BOLA (dono de cada objeto)

Para testar BOLA, a ferramenta precisa saber quem é o dono de cada objeto. Isso fica
em `src/main/resources/objects.yaml` (ou no arquivo indicado por
`AUTHTEST_OBJECTS_SPEC`, por exemplo `file:/config/objects.yaml`). Para cada operação do
OpenAPI, no formato `MÉTODO /caminho`, o arquivo lista os objetos usados no teste, os
parâmetros que identificam cada objeto e o usuário dono:

```yaml
objects:
  - operation: GET /object/{id}
    instances:
      - id: objeto-do-user
        owner: "${AUTHTEST_USER_USERNAME:user}"
        pathParameters:
          id: "1"
      - id: objeto-do-admin
        owner: "${AUTHTEST_ADMIN_USERNAME:admin}"
        sharedWith: []          # opcional: outros usuários com acesso
        pathParameters:
          id: "2"
        queryParameters: {}     # opcional: parâmetros de consulta
```

Cada usuário de `users.yaml` chama cada objeto declarado, e o relatório registra, antes
da resposta, o objeto, o dono e o acesso esperado: `ALLOWED (owner)` para o dono,
`ALLOWED (shared with user)` para quem está em `sharedWith`, `DENIED (not owner)`
para os demais usuários e `DENIED (no credentials)` na chamada sem token. Um usuário que
não é dono e recebe sucesso indica BOLA. A ferramenta ainda não emite o veredito; o
relatório mostra o esperado ao lado da resposta recebida.

O arquivo é validado antes de qualquer requisição: a operação precisa existir no OpenAPI,
o dono e os usuários de `sharedWith` precisam existir em `users.yaml`, todos os
parâmetros de caminho da operação precisam ter valor e não pode haver operação nem `id`
de objeto repetidos. Operações que não aparecem no arquivo, ou a ausência do arquivo,
mantêm o comportamento anterior (parâmetros padrão).

## Keycloak e PostgreSQL

Para iniciar containers locais do PostgreSQL e do Keycloak, defina as variáveis
`POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD` e `KEYCLOAK_USER_PASSWORD` e execute:

```bash
./scripts/setup/start-keycloak-postgres.sh
```

O script cria o administrador de bootstrap no realm `master` e três contas de
teste no realm `authtest`: `user` com as roles `user` e `objectrole`, `admin`
com as roles `admin` e `objectrole`, e `notuser` sem roles de aplicação. Os
nomes de usuário, senhas, realm, portas
e imagens podem ser personalizados pelas variáveis `KEYCLOAK_ADMIN_USERNAME`,
`KEYCLOAK_USER_USERNAME`, `KEYCLOAK_TEST_ADMIN_USERNAME`,
`KEYCLOAK_TEST_ADMIN_PASSWORD`, `KEYCLOAK_NOTUSER_USERNAME`,
`KEYCLOAK_NOTUSER_PASSWORD`, `KEYCLOAK_REALM`, `KEYCLOAK_PORT`,
`KEYCLOAK_MANAGEMENT_PORT`, `POSTGRES_USER`, `POSTGRES_DB`, `POSTGRES_IMAGE` e
`KEYCLOAK_IMAGE`. Os containers usam volumes persistentes e o script não
sobrescreve containers existentes com os nomes configurados.

Como alternativa, `docker compose up` neste diretório usa
`docker-compose.yaml`, sobe PostgreSQL, Keycloak e os serviços a partir das
imagens `tcc/*:local` (construindo-as quando necessário) e importa o realm de
`scripts/setup/authtest-realm.json`. Um serviço de inicialização configura as
senhas das contas importadas a partir de `AUTHTEST_USER_PASSWORD`,
`AUTHTEST_ADMIN_PASSWORD` e `AUTHTEST_NOTUSER_PASSWORD`, cria `notuser` se
necessário e preenche os atributos básicos do perfil exigidos para o login por
senha. Para executar os testes após iniciar os
serviços, use `docker compose --profile test run --rm api-authtest` no diretório
`scripts/setup`. O relatório do Compose fica em
`scripts/setup/reports/authtest-report-<timestamp>.txt`. O runner usa UID/GID `1000` por
padrão para permitir a gravação no diretório montado; defina `AUTHTEST_UID` e
`AUTHTEST_GID` conforme o usuário local, se necessário. Esse Compose usa
credenciais locais de desenvolvimento por padrão; configure as variáveis de
ambiente antes de expô-lo fora da sua máquina.

O Swagger UI do `secure-api` fica disponível em
`http://localhost:8091/swagger-ui/index.html`; a especificação OpenAPI YAML
pode ser consultada em `http://localhost:8091/v3/api-docs.yaml`. Os endpoints
de documentação são públicos; `GET /object/{id}` continua exigindo a role
`objectrole`.

Para remover os containers `base-mysql`, `keycloak` e `keycloak-postgres`, apagar
os volumes persistentes do Keycloak e PostgreSQL e remover a rede do setup,
execute `./scripts/setup/clean-setup.sh`. O script pede confirmação; passe
`--yes` para confirmar sem prompt. Se os nomes dos containers ou da rede foram
personalizados, use os mesmos valores de `POSTGRES_CONTAINER_NAME`,
`KEYCLOAK_CONTAINER_NAME` e `NETWORK_NAME`.

## Reconstruir imagens Docker

Para remover as imagens locais de todos os serviços TCC e reconstruí-las, execute
na pasta `api-authtest`:

```bash
./scripts/setup/rebuild-all-images.sh
```

O script remove somente a tag `tcc/<serviço>:local` de cada serviço e então
chama `build-images.sh` na raiz de `tcc`. Para outra tag, defina `IMAGE_TAG`
antes de executar o script.
