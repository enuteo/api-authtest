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
