
# Plano de Melhoria Técnica em 10 Dias - PDF Shield

## Objetivo

Elevar o projeto para um nível mais sólido de produção, priorizando segurança, confiabilidade e manutenção.

## Dia 1 - Segurança do PIN

- Substituir armazenamento do PIN em texto puro por hash com salt (ex.: PBKDF2 ou Argon2).
- Garantir validação por comparação de hash.
- Critério de aceite:
  PIN não aparece legível em SharedPreferences.

## Dia 2 - Fluxo de Biometria

- Alinhar fluxo da biometria com a preferência do usuário.
- Impedir biometria automática quando opção estiver desativada.
- Critério de aceite:
  Com biometria desativada, login ocorre apenas por PIN.

## Dia 3 - Permissões de Armazenamento

- Reduzir permissões sensíveis.
- Migrar para fluxo SAF (ACTION_OPEN_DOCUMENT) ponta a ponta.
- Critério de aceite:
  Aplicativo funcional sem permissão ampla de gerenciamento total de arquivos.

## Dia 4 - Backup e Extração de Dados

- Definir regras explícitas de inclusão e exclusão para backup.
- Excluir dados sensíveis (PIN hash, preferências críticas, arquivos temporários).
- Critério de aceite:
  Nenhum dado sensível entra em backup cloud ou transferência de dispositivo.

## Dia 5 - Atualização de Dependências

- Atualizar bibliotecas antigas e versões alpha/rc para estáveis.
- Resolver inconsistências de versão entre componentes de navegação e segurança.
- Critério de aceite:
  Build limpo, sem conflito de dependências e com smoke test aprovado.

## Dia 6 - Hardening de Release

- Ativar minificação e obfuscação para build release.
- Ajustar regras do shrinker (ProGuard/R8).
- Critério de aceite:
  Release funcionando com shrinker ativo e sem regressão funcional.

## Dia 7 - Refatoração e Coerência de Código

- Remover código morto e classes/componentes sem uso.
- Corrigir inconsistências de nomenclatura e responsabilidade de classes.
- Critério de aceite:
  Código mais coeso, sem componentes órfãos no projeto.

## Dia 8 - Testes de Segurança e Regressão

- Criar testes unitários para autenticação (PIN/hash).
- Criar testes instrumentados para fluxo crítico:
  setup PIN, login, abertura de PDF e limpeza de temporários.
- Critério de aceite:
  Cobertura mínima dos fluxos críticos implementada.

## Dia 9 - Observabilidade e Tratamento de Erros

- Padronizar logs de erro em criptografia/descriptografia e permissões.
- Melhorar mensagens de falha sem expor detalhes sensíveis.
- Critério de aceite:
  Falhas rastreáveis com logs úteis e sem vazamento de informação.

## Dia 10 - Validação Final e Pré-Release

- Rodar checklist completo em múltiplas versões Android (8, 10 e 13+).
- Validar autenticação, importação, leitura, limpeza de cache e backup.
- Critério de aceite:
  Release candidate estável e pronto para distribuição controlada.

## Resultado Esperado

- Segurança de autenticação significativamente melhor.
- Menor superfície de risco em permissões e backup.
- Build release mais robusto.
- Base de testes inicial para prevenir regressões.
- Projeto mais preparado para evolução e publicação.
