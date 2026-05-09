# Changelog

Todas as mudanças relevantes do PDF Shield serão documentadas neste arquivo.

## [1.5] - Estavel

### Destaques
- Endurecimento geral de seguranca do aplicativo.
- Fluxo de autenticacao mais consistente com PIN e biometria.
- Melhor compatibilidade de instalacao entre builds debug e release.

### Seguranca
- O PIN deixou de ser armazenado em texto puro e passou a usar hash com salt via PBKDF2.
- Validacao do PIN padronizada por comparacao de hash derivado.
- Migracao automatica e proativa de PINs legados para o novo formato seguro.
- Deteccao de invalidacao biometrica quando ha mudanca no cadastro do dispositivo.
- Desativacao automatica da biometria quando a chave vinculada ao hardware deixa de ser valida.
- Bloqueio de captura de tela mantido nas telas sensiveis.
- Exclusao segura reforcada para arquivos temporarios e PDFs removidos.

### Privacidade e armazenamento
- Reducao das permissoes para o minimo necessario.
- Ajuste no pedido de permissao para evitar falso aviso de negacao ao abrir o app.
- Desabilitacao efetiva de backup em nuvem e transferencia automatica de dados sensiveis entre dispositivos.
- Separacao correta entre build debug e release para evitar conflitos de instalacao.
- FileProvider ajustado para usar authority dinamica por variante do app.

### Correcao de bugs
- Corrigido conflito de pacote na instalacao simultanea entre versao instalada e build de desenvolvimento.
- Corrigido conflito de provider durante instalacao do APK debug.
- Corrigido fluxo de abertura/importacao de PDFs quando a permissao ainda nao foi concedida.
- Corrigidos pontos de logging inseguro com substituicao de stack traces expostos.

### Qualidade e manutencao
- Logging centralizado com sanitizacao para evitar exposicao de caminhos, hashes e dados sensiveis.
- Configuracao de minificacao e obfuscacao fortalecida para release.
- Remocao de codigo obsoleto e limpeza de componentes sem uso.
- Inclusao de testes unitarios e de integracao para PIN, biometria, criptografia e fluxo de login.

### Observacoes
- Builds de release devem continuar usando a mesma keystore para permitir atualizacoes sobre versoes ja instaladas.
- O build debug utiliza identificacao separada para coexistir com a versao instalada sem conflito.