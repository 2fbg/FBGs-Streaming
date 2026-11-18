const test = require('node:test');
const assert = require('node:assert/strict');
const Keyboard = require('../mk21-tv/core/OnScreenKeyboard.js');

test('getRows devolve o grid do modo pedido com a linha de controle no fim', () => {
  const lower = Keyboard.getRows('lower');
  assert.equal(lower.length, 5);
  assert.equal(lower[0][0], 'q');
  assert.equal(lower[0][9], 'p');
  assert.equal(lower[1][9], 'ç');
  // ultima linha = controles (MODE, SPACE, BACKSPACE, CLEAR)
  assert.equal(lower[4][0].action, 'MODE');
  assert.equal(lower[4][1].action, 'SPACE');
  assert.equal(lower[4][2].action, 'BACKSPACE');
  assert.equal(lower[4][3].action, 'CLEAR');
});

test('getRows em modo upper devolve as letras maiusculas', () => {
  const upper = Keyboard.getRows('upper');
  assert.equal(upper[0][0], 'Q');
  assert.equal(upper[1][9], 'Ç');
  // os acentos tambem ganham caixa alta (busca PT-BR precisa de "Á")
  assert.equal(upper[3][0], 'Á');
});

test('getRows com modo invalido cai no lower', () => {
  assert.equal(Keyboard.getRows('inexistente')[0][0], 'q');
});

test('createState guarda texto, cursor e modo', () => {
  const state = Keyboard.createState('abc');
  assert.equal(state.value, 'abc');
  assert.equal(state.cursor, 3);
  assert.equal(state.mode, 'lower');

  const empty = Keyboard.createState(null, 'upper');
  assert.equal(empty.value, '');
  assert.equal(empty.cursor, 0);
  assert.equal(empty.mode, 'upper');
});

test('moveCursor navega no grid e nao deixa a coluna fora da linha', () => {
  let state = Keyboard.createState('');
  state = Keyboard.moveCursor(state, 'right');
  assert.equal(state.col, 1);

  // desce 4 linhas -> vai para a linha de controle (que tem 4 colunas)
  state = Keyboard.moveCursor(state, 'down');
  state = Keyboard.moveCursor(state, 'down');
  state = Keyboard.moveCursor(state, 'down');
  state = Keyboard.moveCursor(state, 'down');
  assert.equal(state.row, 4);
  // a coluna e preservada, mas nunca passa da largura da linha
  assert.equal(state.col, 1);

  // empurra a coluna alem do fim da linha de controle -> trava na ultima
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  assert.equal(state.col, 3);

  // desce de novo -> fica na ultima linha (nao ha wrap)
  state = Keyboard.moveCursor(state, 'down');
  assert.equal(state.row, 4);
  // e volta para a primeira linha quando sobe ate o topo
  state = Keyboard.moveCursor(state, 'up');
  state = Keyboard.moveCursor(state, 'up');
  state = Keyboard.moveCursor(state, 'up');
  state = Keyboard.moveCursor(state, 'up');
  assert.equal(state.row, 0);
});

test('moveCursor com esquerda na primeira coluna trava no zero', () => {
  const state = Keyboard.moveCursor(Keyboard.createState(''), 'left');
  assert.equal(state.col, 0);
});

test('getFocusedKey devolve a tecla focada e null fora do grid', () => {
  const state = Keyboard.createState('');
  assert.equal(Keyboard.getFocusedKey(state), 'q');
  const onControl = Keyboard.moveCursor(state, 'down');
  assert.equal(typeof Keyboard.getFocusedKey(onControl), 'string');
  assert.equal(Keyboard.getFocusedKey(null), null);
});

test('insertChar insere na posicao do cursor e avanca', () => {
  let state = Keyboard.createState('ac');
  state = Keyboard.moveTextCursor(state, -1); // cursor entre "a" e "c"
  state = Keyboard.insertChar(state, 'b');
  assert.equal(state.value, 'abc');
  assert.equal(state.cursor, 2);
});

test('backspace apaga o caractere anterior ao cursor', () => {
  let state = Keyboard.createState('abc');
  state = Keyboard.backspace(state);
  assert.equal(state.value, 'ab');
  assert.equal(state.cursor, 2);

  const empty = Keyboard.backspace(Keyboard.createState(''));
  assert.equal(empty.value, '');
  assert.equal(empty.cursor, 0);
});

test('clearText apaga tudo', () => {
  const state = Keyboard.clearText(Keyboard.createState('abc'));
  assert.equal(state.value, '');
  assert.equal(state.cursor, 0);
});

test('toggleMode alterna lower -> upper -> numbers -> symbols -> lower', () => {
  let state = Keyboard.createState('');
  state = Keyboard.toggleMode(state);
  assert.equal(state.mode, 'upper');
  state = Keyboard.toggleMode(state);
  assert.equal(state.mode, 'numbers');
  state = Keyboard.toggleMode(state);
  assert.equal(state.mode, 'symbols');
  state = Keyboard.toggleMode(state);
  assert.equal(state.mode, 'lower');
});

test('toggleMode mantem o cursor de texto e reinicia a coluna', () => {
  let state = Keyboard.createState('abc');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.toggleMode(state);
  assert.equal(state.value, 'abc');
  assert.equal(state.cursor, 3);
  assert.equal(state.col, 0);
});

test('moveTextCursor respeita os limites do texto', () => {
  let state = Keyboard.createState('ab');
  state = Keyboard.moveTextCursor(state, -10);
  assert.equal(state.cursor, 0);
  state = Keyboard.moveTextCursor(state, 10);
  assert.equal(state.cursor, 2);
});

test('getModeLabel devolve o rotulo do botao de modo', () => {
  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'lower')), 'abc');
  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'upper')), 'ABC');
  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'numbers')), '123');
  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'symbols')), '#+=');
  assert.equal(Keyboard.getModeLabel(null), 'abc');
});

test('advanceCursor anda para a proxima tecla e quebra a linha no fim da fileira', () => {
  let state = Keyboard.createState('');
  state = Keyboard.advanceCursor(state);
  assert.equal(state.col, 1);
  assert.equal(state.row, 0);

  // ultima coluna da 1a linha -> proxima linha, primeira coluna
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  assert.equal(state.col, 9);
  state = Keyboard.advanceCursor(state);
  assert.equal(state.row, 1);
  assert.equal(state.col, 0);
});

test('advanceCursor na ultima linha volta para o inicio do teclado', () => {
  let state = Keyboard.createState('');
  state = Keyboard.moveCursor(state, 'down');
  state = Keyboard.moveCursor(state, 'down');
  state = Keyboard.moveCursor(state, 'down');
  state = Keyboard.moveCursor(state, 'down');
  assert.equal(state.row, 4);
  // vai ate a ultima tecla da linha de controle (a de CLEAR)
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  state = Keyboard.moveCursor(state, 'right');
  assert.equal(state.col, 3);
  state = Keyboard.advanceCursor(state);
  assert.equal(state.row, 0);
  assert.equal(state.col, 0);
});
