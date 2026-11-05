# api-authtest
Ferramenta de testagem de vulnerabilidades BOLA e BFLA

## Keycloak e PostgreSQL

Para iniciar containers locais do PostgreSQL e do Keycloak, defina as variáveis
`POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD` e `KEYCLOAK_USER_PASSWORD` e execute:

```bash
./scripts/setup/start-keycloak-postgres.sh
```

O script cria o usuário administrativo no realm `master` e um usuário comum no
realm `authtest`. Os nomes de usuário, realm, portas e imagens podem ser
personalizados pelas variáveis `KEYCLOAK_ADMIN_USERNAME`,
`KEYCLOAK_USER_USERNAME`, `KEYCLOAK_REALM`, `KEYCLOAK_PORT`,
`KEYCLOAK_MANAGEMENT_PORT`, `POSTGRES_USER`, `POSTGRES_DB`, `POSTGRES_IMAGE` e
`KEYCLOAK_IMAGE`. Os containers usam volumes persistentes e o script não
sobrescreve containers existentes com os nomes configurados.

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
