/**
 * MK21 Web PWA — Módulo de Controle Parental (18+)
 * Alinhado às regras de detecção adulta do Android (M3UParser.isAdultContent) e Smart TV (mk21_adult_pin).
 * Compatível com navegador (window.MK21Parental) e Node.js (testes unitários).
 */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.MK21Parental = factory();
    }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    const STORAGE_PIN_KEY = 'mk21_adult_pin';
    const STORAGE_ENABLED_KEY = 'mk21_parental_enabled';
    const DEFAULT_PIN = '0000';

    const ADULT_KEYWORDS = [
        '18+',
        'ADULTO',
        'ADULT',
        'XXX',
        'SEXY',
        'PLAYBOY',
        'PENTHOUSE',
        'VENUS',
        'HOT ',
        'HUSTLER',
        'FORBIDDEN',
        'FORA DA LEI',
        'S0X',
        'PORN',
        'EROTIC',
        'PRIVE',
        'PRIVÊ',
        'SENSUAL'
    ];

    function normalizeText(value) {
        if (!value || typeof value !== 'string') return '';
        return value.toUpperCase();
    }

    function isAdultText(text) {
        const upper = normalizeText(text);
        if (!upper) return false;
        for (let i = 0; i < ADULT_KEYWORDS.length; i++) {
            if (upper.indexOf(ADULT_KEYWORDS[i]) !== -1) {
                return true;
            }
        }
        return false;
    }

    function isAdultCategory(category) {
        return isAdultText(category);
    }

    function isAdultItem(item) {
        if (!item || typeof item !== 'object') return false;
        if (item.isAdult === true) return true;
        return isAdultText(item.name || '') || isAdultText(item.category || '');
    }

    function isValidPin(pin) {
        return typeof pin === 'string' && /^\d{4,6}$/.test(pin.trim());
    }

    function createController(storage) {
        const store = storage || (typeof localStorage !== 'undefined' ? localStorage : null);
        let sessionUnlocked = false;

        function readStorage(key) {
            if (!store) return null;
            try {
                return store.getItem(key);
            } catch (_) {
                return null;
            }
        }

        function writeStorage(key, value) {
            if (!store) return;
            try {
                store.setItem(key, value);
            } catch (_) {
                // Ignora indisponibilidade de storage em modo privado restrito
            }
        }

        function isEnabled() {
            const raw = readStorage(STORAGE_ENABLED_KEY);
            if (raw === null || raw === undefined) return true; // Ativo por padrão como no Android/TV
            return raw !== 'false';
        }

        function setEnabled(enabled) {
            const flag = Boolean(enabled);
            writeStorage(STORAGE_ENABLED_KEY, flag ? 'true' : 'false');
            if (!flag) {
                sessionUnlocked = false;
            }
            return flag;
        }

        function getPin() {
            const stored = readStorage(STORAGE_PIN_KEY);
            return isValidPin(stored) ? stored.trim() : DEFAULT_PIN;
        }

        function setPin(newPin) {
            if (!isValidPin(newPin)) {
                return { ok: false, error: 'O PIN deve conter entre 4 e 6 dígitos numéricos.' };
            }
            writeStorage(STORAGE_PIN_KEY, newPin.trim());
            return { ok: true };
        }

        function isUnlocked() {
            return sessionUnlocked;
        }

        function lockSession() {
            sessionUnlocked = false;
        }

        function verifyPin(enteredPin) {
            const clean = typeof enteredPin === 'string' ? enteredPin.trim() : '';
            if (!clean) return false;
            if (clean === getPin()) {
                sessionUnlocked = true;
                return true;
            }
            return false;
        }

        function shouldBlockCategory(category) {
            if (!isEnabled() || sessionUnlocked) return false;
            return isAdultCategory(category);
        }

        function shouldBlockItem(item) {
            if (!isEnabled() || sessionUnlocked) return false;
            return isAdultItem(item);
        }

        return {
            STORAGE_PIN_KEY,
            STORAGE_ENABLED_KEY,
            DEFAULT_PIN,
            ADULT_KEYWORDS,
            isAdultCategory,
            isAdultItem,
            isValidPin,
            isEnabled,
            setEnabled,
            getPin,
            setPin,
            isUnlocked,
            lockSession,
            verifyPin,
            shouldBlockCategory,
            shouldBlockItem,
            createController
        };
    }

    return createController();
});
