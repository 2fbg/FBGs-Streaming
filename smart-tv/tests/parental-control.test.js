const test = require('node:test');
const assert = require('node:assert/strict');
const parental = require('../../js/parental-control.js');

function createMemoryStorage() {
    const map = new Map();
    return {
        getItem(k) { return map.has(k) ? map.get(k) : null; },
        setItem(k, v) { map.set(k, String(v)); },
        removeItem(k) { map.delete(k); }
    };
}

test('detecta categorias e itens adultos com as mesmas regras do Android/TV', () => {
    assert.equal(parental.isAdultCategory('Canais | Adultos 18+'), true);
    assert.equal(parental.isAdultCategory('XXX Premium'), true);
    assert.equal(parental.isAdultCategory('Esportes HD'), false);

    assert.equal(parental.isAdultItem({ name: 'Playboy TV HD', category: 'Canais' }), true);
    assert.equal(parental.isAdultItem({ name: 'Canal Normal', category: 'Adulto' }), true);
    assert.equal(parental.isAdultItem({ name: 'Globo SP', category: 'TV Aberta' }), false);
    assert.equal(parental.isAdultItem({ name: 'Marcado', category: 'Geral', isAdult: true }), true);
});

test('bloqueia por padrao ate validar o PIN e rebloqueia ao encerrar sessao', () => {
    const ctrl = parental.createController(createMemoryStorage());
    const adultItem = { name: 'Venus HD', category: 'Adultos 18+' };
    const safeItem = { name: 'SporTV HD', category: 'Esportes' };

    assert.equal(ctrl.isEnabled(), true);
    assert.equal(ctrl.getPin(), '0000');
    assert.equal(ctrl.shouldBlockItem(adultItem), true);
    assert.equal(ctrl.shouldBlockCategory('Adultos 18+'), true);
    assert.equal(ctrl.shouldBlockItem(safeItem), false);

    assert.equal(ctrl.verifyPin('1234'), false);
    assert.equal(ctrl.isUnlocked(), false);

    assert.equal(ctrl.verifyPin('0000'), true);
    assert.equal(ctrl.isUnlocked(), true);
    assert.equal(ctrl.shouldBlockItem(adultItem), false);
    assert.equal(ctrl.shouldBlockCategory('Adultos 18+'), false);

    ctrl.lockSession();
    assert.equal(ctrl.isUnlocked(), false);
    assert.equal(ctrl.shouldBlockItem(adultItem), true);
});

test('valida formato do novo PIN (4 a 6 digitos) e respeita desativacao do controle', () => {
    const ctrl = parental.createController(createMemoryStorage());

    assert.equal(ctrl.setPin('12').ok, false);
    assert.equal(ctrl.setPin('abcd').ok, false);
    assert.equal(ctrl.setPin('1234567').ok, false);

    assert.equal(ctrl.setPin('2580').ok, true);
    assert.equal(ctrl.getPin(), '2580');
    assert.equal(ctrl.verifyPin('0000'), false);
    assert.equal(ctrl.verifyPin('2580'), true);

    ctrl.lockSession();
    ctrl.setEnabled(false);
    assert.equal(ctrl.isEnabled(), false);
    assert.equal(ctrl.shouldBlockCategory('XXX'), false);
    assert.equal(ctrl.shouldBlockItem({ name: 'Playboy', category: 'Adultos' }), false);
});
