# Convenções de commit

Este documento é a fonte de verdade para agentes que criam commits locais.

## Autoridade e branch

1. Examine a branch atual antes de agir.
2. Em uma branch de trabalho, crie o commit local ao concluir uma tarefa com
   alterações versionáveis e verificadas.
3. Em `main`, só crie um commit com autorização explícita do humano, exceto se
   houver autorização anterior, inequívoca e ainda aplicável no contexto ativo.
4. Não crie branches, não altere o histórico existente e nunca faça push.

## Seleção segura do escopo

1. Inspecione `git status` e os diffs antes de selecionar arquivos.
2. Inclua somente as mudanças comprovadamente relacionadas à tarefa concluída.
3. Não misture trabalho alheio ou sem relação em uma árvore compartilhada.
4. Quando não for possível separar o escopo com segurança, pare e explique o
   motivo; não crie um commit parcial ou ambíguo.

## Pré-requisitos

1. Execute as validações relevantes e registre somente resultados comprovados.
2. Antes de commitar arquivos versionados, atualize o Graphify conforme
   `AGENTS.md` e `docs/architecture/graphify.md`, revise o diff de
   `graphify-out/` e inclua apenas os artefatos portáveis que representem a
   mudança.

## Mensagem do commit

1. Consulte [Gitmoji](https://gitmoji.dev/) e escolha o emoji cujo significado
   publicado corresponde à mudança.
2. Crie o commit diretamente; não exiba nem proponha a mensagem para aprovação.
3. Escreva em português um título no formato:

   ```text
   gitmoji verbo resultado: contexto
   ```

4. Acrescente corpo em Markdown explicativo, usando somente as seções que se
   aplicarem:
   - `Novas funcionalidades`
   - `Melhorias na arquitetura`
   - `Boas práticas e validações`
   - `Permissões e controle de acesso`
   - `Resultado`
5. Não invente capacidades, testes ou garantias não demonstrados pelo diff e
   pelas validações executadas.

## Atribuição

É absolutamente proibido incluir trailers `Co-authored-by`, `Co-authored-by:`
ou qualquer outra atribuição de coautoria no commit.
