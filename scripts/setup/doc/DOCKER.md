# Docker: serviços TCC

## Construir as imagens

Na pasta `tcc`, execute:

```bash
./api-authtest/scripts/setup/doc/build-images.sh
```

O script compila os quatro projetos e cria imagens locais com a tag `local`.
Para usar outra tag, defina `IMAGE_TAG`, por exemplo `IMAGE_TAG=dev ./build-images.sh`.

## Subir os serviços

Antes de subir os serviços seguros, inicie o Keycloak pelo setup existente em
`api-authtest/scripts/setup/start-keycloak-postgres.sh`. O Keycloak deve estar
acessível pelo host na porta `8080`, com o realm e client configurados. Os
containers seguros usam `host.docker.internal` para acessar esse Keycloak. Cada
stack Compose cria seu próprio PostgreSQL para `base_objects`.

```bash
docker compose -f docker-compose.secure.yml up -d
docker compose -f docker-compose.insecure.yml up -d
docker compose -f docker-compose.all.yml up -d
```

As pilhas seguras e inseguras têm nomes de projeto separados e podem rodar
simultaneamente.

Também é possível iniciar o stack local integrado a partir de
`api-authtest/scripts/setup` com `docker compose up`. Esse arquivo constrói as
imagens `tcc/*:local` se necessário e inclui PostgreSQL e Keycloak com o realm
`authtest` importado.

| Serviço | Porta local |
|---|---:|
| no-secure-api | 8070 |
| secure-api | 8091 |
| auth-api | 8092 |

`api-authtest` é um utilitário de execução única, não um servidor HTTP. Ele é
incluído no Compose completo como perfil opcional e autentica as contas
configuradas em `api-authtest/src/main/resources/users.yaml`. Para endpoints
com `{id}`, a chave `objects` de cada conta define os IDs consultados. Os
serviços `no-secure-api` e `secure-api` usam a tabela `base_objects`, criada e
populada no PostgreSQL do Compose:

```bash
AUTHTEST_USER_PASSWORD='<senha-user>' \
AUTHTEST_ADMIN_PASSWORD='<senha-admin>' \
docker compose -f docker-compose.all.yml --profile test run --rm api-authtest
```

As senhas devem corresponder às usadas ao iniciar o Keycloak. O Compose define
a URL interna `http://auth-api:8081/auth/login`; localmente, a URL padrão é
`http://localhost:8081/auth/login`. `AUTHTEST_AUTH_URL` e os nomes de usuário
também podem ser configurados pelo ambiente.

Para encerrar cada pilha, use `docker compose -f <arquivo> down`.
