/**
 * MK21 Play — Teclado virtual (on-screen keyboard) da Smart TV.
 *
 * Portão da funcionalidade do Saimo TV (LG webOS): na TV não existe teclado
 * físico, e o teclado do sistema (OSK) em webOS/Tizen é lento e ruim para
 * busca. Este módulo implementa um teclado QWERTY navegável 100% pelo D-pad.
 *
 * Módulo PURO: guarda só o estado (texto + cursor + modo + posição no grid).
 * A camada de DOM fica no app legado (`src/legacy/app-legacy.js`).
 */

/** Modos de teclado disponíveis, na ordem de alternância do botão "⇧". */
const KEYBOARD_MODES = ['lower', 'upper', 'numbers', 'symbols'];

/** Rótulo do botão que alterna o modo. */
const MODE_LABELS = {
  lower: 'abc',
  upper: 'ABC',
  numbers: '123',
  symbols: '#+='
};

const LETTER_ROWS = [
  ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
  ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ç'],
  ['z', 'x', 'c', 'v', 'b', 'n', 'm', ',', '.', '?'],
  ['á', 'é', 'í', 'ó', 'ú', 'â', 'ê', 'ô', 'à', 'õ']
];

const NUMBER_ROWS = [
  ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
  ['-', '.', ',', '?', '!', "'", '@', '#', '/', ':'],
  ['(', ')', '+', '=', '%', '*', '$', '&', '_', '"']
];

/** Linha de controle fixa no rodapé do teclado. */
const CONTROL_ROW = [
  { action: 'MODE', label: '⇧' },
  { action: 'SPACE', label: '␣' },
  { action: 'BACKSPACE', label: '⌫' },
  { action: 'CLEAR', label: '⟲' }
];

/**
 * Monta o grid completo de teclas do modo pedido.
 * @returns {Array<Array<string|{action:string,label:string}>>}
 */
function getRows(mode) {
  const normalized = KEYBOARD_MODES.indexOf(mode) >= 0 ? mode : 'lower';

  let base;
  if (normalized === 'numbers' || normalized === 'symbols') {
    base = NUMBER_ROWS.map((row) => row.slice());
  } else {
    base = LETTER_ROWS.map((row) => row.slice());
    if (normalized === 'upper') {
      base = base.map((row) => row.map((ch) => ch.toUpperCase()));
    }
  }

  // No modo "símbolos" as letras continuam minúsculas — os símbolos ficam
  // na segunda e terceira linhas, junto com os números.
  return base.concat([CONTROL_ROW.slice()]);
}

/** Cria o estado inicial do teclado. */
function createState(value, mode) {
  const text = String(value === null || value === undefined ? '' : value);
  return {
    value: text,
    cursor: text.length,
    mode: KEYBOARD_MODES.indexOf(mode) >= 0 ? mode : 'lower',
    row: 0,
    col: 0
  };
}

/** Número de linhas do grid do modo atual. */
function getRowCount(state) {
  return getRows(state && state.mode).length;
}

/** Número de colunas da linha (as linhas de controle são menores). */
function getColCount(state, row) {
  const rows = getRows(state && state.mode);
  const idx = typeof row === 'number' ? row : (state ? state.row : 0);
  const line = rows[idx] || rows[0] || [];
  return line.length;
}

/**
 * Move a seleção do grid no D-pad.
 *
 * Ao contrário de um grid de rolagem, o teclado virtual **prende** a seleção
 * nas bordas: no D-pad da TV é muito mais previsível do que um salto da última
 * letra para a primeira.
 *
 * @param {object} state
 * @param {'up'|'down'|'left'|'right'} direction
 */
function moveCursor(state, direction) {
  if (!state) return state;
  const rows = getRows(state.mode);
  const maxRow = rows.length - 1;

  let row = state.row;
  let col = state.col;

  if (direction === 'up') {
    row = row > 0 ? row - 1 : 0;
  } else if (direction === 'down') {
    row = row < maxRow ? row + 1 : maxRow;
  } else if (direction === 'left') {
    col = col > 0 ? col - 1 : 0;
  } else if (direction === 'right') {
    const width = getColCount(state, row);
    col = col < width - 1 ? col + 1 : width - 1;
  }

  // Ao trocar de linha, garante que a coluna exista naquela linha.
  const width = getColCount(state, row);
  if (col >= width) col = width - 1;
  if (col < 0) col = 0;

  return Object.assign({}, state, { row, col });
}

/** Devolve a tecla focada no grid (ou null). */
function getFocusedKey(state) {
  if (!state) return null;
  const rows = getRows(state.mode);
  const line = rows[state.row];
  if (!line) return null;
  return line[state.col] !== undefined ? line[state.col] : null;
}

/** Insere um caractere na posição do cursor e avança o cursor. */
function insertChar(state, char) {
  if (!state || !char) return state;
  const text = String(state.value || '');
  const cursor = Math.max(0, Math.min(text.length, state.cursor));
  const next = text.slice(0, cursor) + char + text.slice(cursor);
  return Object.assign({}, state, { value: next, cursor: cursor + char.length });
}

/** Apaga o caractere anterior ao cursor (backspace). */
function backspace(state) {
  if (!state) return state;
  const text = String(state.value || '');
  const cursor = Math.max(0, Math.min(text.length, state.cursor));
  if (cursor === 0) return state;
  const next = text.slice(0, cursor - 1) + text.slice(cursor);
  return Object.assign({}, state, { value: next, cursor: cursor - 1 });
}

/** Limpa todo o texto. */
function clearText(state) {
  if (!state) return state;
  return Object.assign({}, state, { value: '', cursor: 0 });
}

/** Alterna o modo do teclado (lower -> upper -> numbers -> symbols -> lower). */
function toggleMode(state) {
  if (!state) return state;
  const idx = KEYBOARD_MODES.indexOf(state.mode);
  const nextMode = KEYBOARD_MODES[(idx + 1) % KEYBOARD_MODES.length];
  const rows = getRows(nextMode);
  return Object.assign({}, state, {
    mode: nextMode,
    row: Math.min(state.row, rows.length - 1),
    col: 0
  });
}

/**
 * Avança a seleção para a próxima tecla, como um teclado de celular.
 * Usado depois de digitar: sem isso, o OK seguinte repetiria a mesma letra.
 */
function advanceCursor(state) {
  if (!state) return state;
  const rows = getRows(state.mode);
  let row = state.row;
  let col = state.col;

  const width = getColCount(state, row);
  if (col < width - 1) {
    col++;
  } else if (row < rows.length - 1) {
    row++;
    col = 0;
  } else {
    row = 0;
    col = 0;
  }

  return Object.assign({}, state, { row, col });
}

/** Move o cursor de texto (usado pelas setas esquerda/direita quando em modo texto). */
function moveTextCursor(state, delta) {
  if (!state) return state;
  const text = String(state.value || '');
  const cursor = Math.max(0, Math.min(text.length, (state.cursor || 0) + delta));
  return Object.assign({}, state, { cursor });
}

/** Rótulo do botão de modo, para desenhar na tela. */
function getModeLabel(state) {
  return MODE_LABELS[(state && state.mode) || 'lower'] || 'abc';
}

module.exports = {
  KEYBOARD_MODES,
  MODE_LABELS,
  LETTER_ROWS,
  NUMBER_ROWS,
  CONTROL_ROW,
  getRows,
  createState,
  getRowCount,
  getColCount,
  moveCursor,
  advanceCursor,
  getFocusedKey,
  insertChar,
  backspace,
  clearText,
  toggleMode,
  moveTextCursor,
  getModeLabel
};
