# BlackICE — Correção do contorno de foco nos campos do Keycloak

## Objetivo

Remover o contorno branco interno que aparece nos campos de usuário e senha
quando recebem foco, inclusive enquanto o painel do 1Password está aberto,
preservando a moldura ciano do tema como indicador visual de foco.

## Diagnóstico

O tema estiliza o foco no wrapper PatternFly:

```css
.pf-v5-c-form-control:focus-within {
  border-color: var(--bi-accent);
  box-shadow: 0 0 0 3px rgba(86, 200, 232, .11);
}
```

O `<input>` interno remove a borda, mas não declara seu `outline`. Durante o
foco, navegador e extensão podem desenhar um segundo contorno no elemento
interno. Isso produz a moldura branca observada dentro da borda ciano. Ao perder
o foco, o contorno desaparece.

## Decisão

Adicionar `outline: none` à regra já existente para os inputs diretos de
`.pf-v5-c-form-control`.

O indicador de foco não será removido da interface: ele continuará no wrapper
por meio de `:focus-within`, com borda ciano e halo de contraste. A mudança vale
igualmente para o campo de usuário e para o campo de senha e não altera os
elementos injetados pelo 1Password.

## Alternativas descartadas

- Tornar o outline transparente: produz aparência semelhante, mas comunica a
  intenção com menos clareza e pode ter diferenças entre navegadores.
- Recolorir o outline interno: manteria dois indicadores de foco sobrepostos e
  deixaria o controle visualmente carregado.

## Escopo

Modificar somente:

`infra/keycloak/themes/blackice/login/resources/css/blackice.css`

Não alterar templates FreeMarker, configuração do realm, integração do
1Password, tokens visuais ou outros componentes PatternFly.

## Verificação

1. Uma verificação automatizada do CSS deve falhar antes da mudança e comprovar
   que a regra dos inputs internos neutraliza o outline depois dela.
2. Os campos de usuário e senha devem continuar recebendo a borda ciano e o halo
   do wrapper quando focados.
3. A página deve ser recarregada com o tema atualizado.
4. A inspeção visual deve confirmar ausência do contorno branco:
   - com foco por clique;
   - com foco por teclado;
   - com o painel do 1Password aberto.
5. O ícone e o painel do 1Password devem continuar funcionais.

