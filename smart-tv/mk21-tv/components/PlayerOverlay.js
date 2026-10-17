// smart-tv/mk21-tv/components/PlayerOverlay.js - XSS-Safe Rendering
export function updatePlayerOSD(channel, epgData) {
  const titleEl = document.querySelector('.player-hud .channel-title');
  const programEl = document.querySelector('.player-hud .current-program');
  const nextProgramEl = document.querySelector('.player-hud .next-program');
  const logoEl = document.querySelector('.player-hud .channel-logo');

  if (titleEl) titleEl.textContent = channel.name || 'Canal Desconhecido';
  if (programEl && epgData?.current) programEl.textContent = `AGORA: ${epgData.current.title || 'Sem Informações'}`;
  if (nextProgramEl && epgData?.next) nextProgramEl.textContent = `A SEGUIR: ${epgData.next.title || 'Sem Informações'}`;

  if (logoEl && channel.logo) {
    const safeUrl = channel.logo.startsWith('http') ? channel.logo : '';
    logoEl.src = safeUrl;
    logoEl.style.display = safeUrl ? 'block' : 'none';
  }
}
