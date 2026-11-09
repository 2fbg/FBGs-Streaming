/**
 * Entrada do app da TV (Fase 2).
 *
 * Ordem de execução no bundle: o corpo de `player-service.js` roda primeiro e
 * publica `window.FBG2PlayerService`; em seguida o app legado inicializa e
 * registra seus hooks de UI; por último este arquivo agenda o refresh do flag
 * remoto. Nada aqui depende de build em runtime — a OTA continua baixando só
 * `app.js` e `styles.css`.
 */
import { playerService } from './player-service.js';
import './legacy/app-legacy.js';

if (typeof window !== 'undefined') {
  window.FBG2PlayerService = playerService;

  // Flag remoto (`playerEngine`) — atualiza em background, sem bloquear o boot.
  setTimeout(() => {
    try {
      playerService.hydrateRemote();
    } catch (err) {
      console.warn('[PlayerService] hydrateRemote falhou:', err);
    }
  }, 1500);
}
