# Correção do contorno de foco nos campos do Keycloak — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remover o contorno branco interno dos campos de usuário e senha sem
remover a moldura ciano que indica foco.

**Architecture:** O `<input>` interno deixa de desenhar seu próprio `outline`.
O wrapper PatternFly continua responsável por todo o estado visual de foco por
meio de `:focus-within`, preservando borda ciano e halo.

**Tech Stack:** CSS, tema `keycloak.v2` do Keycloak 25.0.6, PatternFly v5,
PowerShell para a verificação estática e Chrome para a verificação visual.

**Spec:** `docs/superpowers/specs/2026-07-27-keycloak-input-focus-outline-design.md`

## Global Constraints

- Modificar somente
  `infra/keycloak/themes/blackice/login/resources/css/blackice.css`.
- Não alterar templates FreeMarker, configuração do realm, integração do
  1Password, tokens visuais ou outros componentes PatternFly.
- O indicador de foco permanece no wrapper
  `.pf-v5-c-form-control:focus-within`.
- A regra deve cobrir igualmente os inputs de usuário e senha.
- Não criar commit sem pedido explícito do dono do repositório.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
| :-- | :-- |
| `infra/keycloak/themes/blackice/login/resources/css/blackice.css` | Neutralizar o outline do input interno e manter o foco no wrapper PatternFly. |

### Task 1: Centralizar o indicador de foco no wrapper PatternFly

**Files:**

- Modify:
  `infra/keycloak/themes/blackice/login/resources/css/blackice.css:253`
- Test: verificação estática descartável executada diretamente no PowerShell.

**Interfaces:**

- Consumes: a regra existente `.pf-v5-c-form-control > input` e o estado de
  foco existente `.pf-v5-c-form-control:focus-within`.
- Produces: inputs internos com `outline: none`; o wrapper continua com
  `border-color: var(--bi-accent)` e halo
  `0 0 0 3px rgba(86, 200, 232, .11)`.

- [ ] **Step 1: Executar a verificação estática antes da mudança**

```powershell
$cssPath = 'infra/keycloak/themes/blackice/login/resources/css/blackice.css'
$css = Get-Content -Raw $cssPath
$inputRule = [regex]::Match(
  $css,
  '(?s)\.pf-v5-c-form-control\s*>\s*input\s*\{(?<body>.*?)\}'
)
if (-not $inputRule.Success) {
  throw 'Regra dos inputs internos não encontrada'
}
if ($inputRule.Groups['body'].Value -notmatch '(?m)^\s*outline:\s*none\s*;') {
  throw 'O input interno ainda pode desenhar um outline próprio'
}
```

Expected: FAIL com
`O input interno ainda pode desenhar um outline próprio`.

- [ ] **Step 2: Implementar a alteração mínima**

Na regra existente `.pf-v5-c-form-control > input`, adicionar somente a
declaração e o comentário abaixo depois de `border: 0;`:

```css
  /* O wrapper já fornece o indicador de foco; neutralizar o outline do input
     evita uma segunda moldura branca, inclusive com gerenciadores de senha. */
  outline: none;
```

- [ ] **Step 3: Reexecutar a verificação estática**

Executar exatamente o script PowerShell do Step 1.

Expected: exit code 0, sem mensagem de erro.

- [ ] **Step 4: Verificar que o foco acessível permanece definido**

```powershell
$cssPath = 'infra/keycloak/themes/blackice/login/resources/css/blackice.css'
$css = Get-Content -Raw $cssPath
$focusRule = [regex]::Match(
  $css,
  '(?s)\.pf-v5-c-form-control:focus-within\s*\{(?<body>.*?)\}'
)
if (-not $focusRule.Success) {
  throw 'Regra de foco do wrapper não encontrada'
}
$body = $focusRule.Groups['body'].Value
if ($body -notmatch 'border-color:\s*var\(--bi-accent\)\s*;') {
  throw 'A borda ciano de foco foi perdida'
}
if ($body -notmatch 'box-shadow:\s*0 0 0 3px rgba\(86,\s*200,\s*232,\s*\.11\)\s*;') {
  throw 'O halo de foco foi perdido'
}
```

Expected: exit code 0, sem mensagem de erro.

- [ ] **Step 5: Conferir o diff e a integridade do arquivo**

```powershell
git diff --check -- infra/keycloak/themes/blackice/login/resources/css/blackice.css
git diff -- infra/keycloak/themes/blackice/login/resources/css/blackice.css
```

Expected: `git diff --check` sem saída; o diff contém somente o comentário e
`outline: none` dentro da regra dos inputs.

- [ ] **Step 6: Recarregar e verificar no Chrome**

Com a stack local já em execução:

1. Recarregar a página `Acesso ao BlackICE`.
2. Focar `#username` por clique.
3. Focar `#password` usando `Tab`.
4. Confirmar em ambos:
   - nenhuma moldura branca interna;
   - borda ciano e halo visíveis no wrapper;
   - ícone do 1Password visível;
   - painel do 1Password abre normalmente.

Expected: os dois campos exibem somente o indicador ciano do tema durante o
foco, com o 1Password funcional.

- [ ] **Step 7: Não criar commit**

Encerrar com o CSS e os documentos de spec/plano como mudanças locais. Só
adicionar ao índice ou criar commit se o usuário pedir explicitamente.

