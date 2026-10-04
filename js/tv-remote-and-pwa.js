// MK21 Web - Smart TV D-Pad Remote Navigation, PWA Installer & Persistent VOD Resume Engine

(function () {
    'use strict';

    // Auto-redirect to HTTPS on live domain (mandatory for PWA, Service Worker & Web Crypto on LG TV)
    if (location.protocol === 'http:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
        location.replace(location.href.replace('http:', 'https:'));
    }

    // ==========================================
    // 1. PWA SERVICE WORKER & UNIVERSAL INSTALL
    // ==========================================
    let deferredPrompt = null;

    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('sw.js').then((reg) => {
                console.log('[PWA] Service Worker registrado com sucesso:', reg.scope);
            }).catch((err) => {
                console.warn('[PWA] Falha ao registrar Service Worker:', err);
            });
        });
    }

    window.openSmartTvInstallModal = function() {
        const modal = document.getElementById('smart-tv-install-modal');
        if (modal) {
            modal.classList.remove('hidden');
            modal.classList.add('flex');
        }
    };

    window.closeSmartTvInstallModal = function() {
        const modal = document.getElementById('smart-tv-install-modal');
        if (modal) {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        }
    };

    window.triggerPwaInstall = function() {
        if (deferredPrompt) {
            deferredPrompt.prompt();
            deferredPrompt.userChoice.then(({ outcome }) => {
                console.log(`[PWA] Escolha do usuário: ${outcome}`);
                deferredPrompt = null;
            });
        } else {
            // Em Smart TVs (LG webOS, Samsung Tizen) ou navegadores sem prompt nativo:
            window.openSmartTvInstallModal();
        }
    };

    function setupInstallButtons() {
        const installBtns = document.querySelectorAll('.pwa-install-btn, #pwa-install-btn, #pwa-install-btn-login, #pwa-install-btn-header');
        installBtns.forEach(btn => {
            btn.onclick = (e) => {
                e.preventDefault();
                window.triggerPwaInstall();
            };
        });
    }

    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        deferredPrompt = e;
        console.log('[PWA] beforeinstallprompt capturado. Botão de instalação nativo ativado.');
        setupInstallButtons();
    });

    window.addEventListener('appinstalled', () => {
        console.log('[PWA] Aplicativo instalado com sucesso!');
        deferredPrompt = null;
    });

    // ==========================================
    // 2. RESUME VOD (CONTINUAR ASSISTINDO COM SEGUNDOS)
    // ==========================================
    const VOD_RESUME_STORAGE_KEY = 'mk21_vod_resume_progress';

    function getVodResumeData() {
        try {
            return JSON.parse(localStorage.getItem(VOD_RESUME_STORAGE_KEY)) || {};
        } catch (e) {
            return {};
        }
    }

    function saveVodResumeData(url, currentTime, duration, name) {
        if (!url || !duration || duration <= 0) return;
        const data = getVodResumeData();
        
        // Se assistiu mais de 95% do filme, considera concluído e limpa
        if (currentTime / duration > 0.95) {
            delete data[url];
        } else if (currentTime > 15) {
            data[url] = {
                currentTime: Math.floor(currentTime),
                duration: Math.floor(duration),
                name: name || 'Filme',
                updatedAt: Date.now()
            };
        }
        
        try {
            localStorage.setItem(VOD_RESUME_STORAGE_KEY, JSON.stringify(data));
        } catch (e) {}
    }

    function getSavedProgress(url) {
        if (!url) return null;
        const data = getVodResumeData();
        return data[url] || null;
    }

    function showResumePrompt(savedSeconds, duration) {
        const promptEl = document.getElementById('resume-vod-prompt');
        const timeDisplay = document.getElementById('resume-vod-time');
        const resumeBtn = document.getElementById('resume-vod-btn');
        const restartBtn = document.getElementById('restart-vod-btn');
        const video = document.getElementById('iptv-video');

        if (!promptEl || !video) return;

        const formatMinSec = (sec) => {
            const m = Math.floor(sec / 60);
            const s = Math.floor(sec % 60);
            return `${m}:${s < 10 ? '0' : ''}${s}`;
        };

        if (timeDisplay) {
            timeDisplay.textContent = formatMinSec(savedSeconds);
        }

        promptEl.classList.remove('hidden');
        promptEl.classList.add('flex');

        let timeout = setTimeout(() => {
            promptEl.classList.add('hidden');
            promptEl.classList.remove('flex');
        }, 8000);

        if (resumeBtn) {
            resumeBtn.onclick = () => {
                clearTimeout(timeout);
                video.currentTime = savedSeconds;
                promptEl.classList.add('hidden');
                promptEl.classList.remove('flex');
                if (typeof showNotification === 'function') {
                    showNotification(`Retomado em ${formatMinSec(savedSeconds)}`, 'info');
                }
            };
        }

        if (restartBtn) {
            restartBtn.onclick = () => {
                clearTimeout(timeout);
                video.currentTime = 0;
                promptEl.classList.add('hidden');
                promptEl.classList.remove('flex');
            };
        }
    }

    // Monitor video playback time to save progress
    function setupVideoResumeWatcher() {
        const video = document.getElementById('iptv-video');
        if (!video) return;

        let lastSavedTime = 0;

        video.addEventListener('timeupdate', () => {
            if (video.duration && isFinite(video.duration) && video.duration > 60) {
                const nowSec = Math.floor(video.currentTime);
                // Salva a cada 4 segundos para eficiência
                if (Math.abs(nowSec - lastSavedTime) >= 4) {
                    lastSavedTime = nowSec;
                    const activeUrl = window.currentStreamUrl || video.src;
                    const activeName = (window.currentActiveItem && window.currentActiveItem.name) || '';
                    saveVodResumeData(activeUrl, nowSec, video.duration, activeName);
                }
            }
        });

        video.addEventListener('loadedmetadata', () => {
            if (video.duration && isFinite(video.duration) && video.duration > 60) {
                const activeUrl = window.currentStreamUrl || video.src;
                const saved = getSavedProgress(activeUrl);
                if (saved && saved.currentTime > 15 && saved.currentTime < (video.duration - 30)) {
                    showResumePrompt(saved.currentTime, video.duration);
                }
            }
        });
    }

    // ==========================================
    // 3. SMART TV D-PAD & KEYBOARD REMOTE CONTROL
    // ==========================================
    let osdHideTimeout = null;

    function showRemoteOsdBanner(channelNumber, title, category) {
        let banner = document.getElementById('remote-osd-banner');
        const numEl = document.getElementById('remote-osd-number');
        const titleEl = document.getElementById('remote-osd-title');
        const catEl = document.getElementById('remote-osd-category');

        if (!banner) return;

        if (numEl) numEl.textContent = channelNumber ? `CH ${channelNumber}` : 'CANAL';
        if (titleEl) titleEl.textContent = title || 'Transmissão Ao Vivo';
        if (catEl) catEl.textContent = category || 'MK21 IPTV';

        clearTimeout(osdHideTimeout);
        banner.classList.remove('opacity-0', '-translate-y-4');
        banner.classList.add('opacity-100', 'translate-y-0');

        osdHideTimeout = setTimeout(() => {
            banner.classList.remove('opacity-100', 'translate-y-0');
            banner.classList.add('opacity-0', '-translate-y-4');
        }, 3200);
    }

    function switchChannelByOffset(offset) {
        // Obter itens da lista ativa atual
        const items = window.activeCategoryItems || [];
        if (!items || items.length === 0) return;

        let currentIndex = -1;
        if (window.currentActiveItem) {
            currentIndex = items.findIndex(i => i.url === window.currentActiveItem.url);
        }

        let newIndex = 0;
        if (currentIndex !== -1) {
            newIndex = currentIndex + offset;
            if (newIndex < 0) newIndex = items.length - 1;
            if (newIndex >= items.length) newIndex = 0;
        }

        const nextChannel = items[newIndex];
        if (nextChannel && typeof window.playStream === 'function') {
            window.playStream(nextChannel);
            showRemoteOsdBanner(newIndex + 1, nextChannel.name, nextChannel.category);
            
            // Scroll channel item into view if in list
            const channelCards = document.querySelectorAll('[data-stream-url]');
            channelCards.forEach(card => {
                if (card.getAttribute('data-stream-url') === nextChannel.url) {
                    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    card.classList.add('ring-2', 'ring-[#E50914]');
                    setTimeout(() => card.classList.remove('ring-2', 'ring-[#E50914]'), 1500);
                }
            });
        }
    }

    function handleRemoteKeyNavigation(e) {
        // Se o foco estiver em um campo de texto, não intercepta
        const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
        if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') {
            return;
        }

        const video = document.getElementById('iptv-video');
        const playerContainer = document.getElementById('video-player-container');
        const key = e.key;
        const code = e.keyCode || e.which;

        // Samsung Tizen (10009) & LG webOS (461) Return / Back Button
        if (code === 10009 || code === 461 || key === 'Escape' || key === 'Backspace' || key === 'GoBack') {
            e.preventDefault();

            // 0. Fechar modal de instalação na TV se aberto
            const installModal = document.getElementById('smart-tv-install-modal');
            if (installModal && !installModal.classList.contains('hidden')) {
                if (typeof window.closeSmartTvInstallModal === 'function') window.closeSmartTvInstallModal();
                return;
            }

            // 1. Fechar modal de espelhamento se aberto
            const castModal = document.getElementById('web-cast-modal');
            if (castModal && !castModal.classList.contains('hidden')) {
                if (typeof window.closeWebCastModal === 'function') window.closeWebCastModal();
                return;
            }

            // 2. Fechar modal de categorias no mobile se aberto
            const catModal = document.getElementById('mobile-category-modal');
            if (catModal && !catModal.classList.contains('hidden')) {
                catModal.classList.add('hidden');
                catModal.classList.remove('flex');
                return;
            }

            // 3. Sair de Tela Cheia
            if (document.fullscreenElement) {
                document.exitFullscreen().catch(() => {});
                return;
            }

            // 4. Se estiver assistindo canal no layout mobile, fechar reprodutor
            const playerCol = document.getElementById('player-column');
            if (playerCol && !playerCol.classList.contains('hidden') && window.innerWidth < 1024) {
                if (typeof window.closeMobilePlayer === 'function') {
                    window.closeMobilePlayer();
                    return;
                }
            }

            // 5. Se estiver em Tizen e na raiz, pode minimizar ou pedir confirmação
            if (window.tizen && window.tizen.application) {
                try {
                    window.tizen.application.getCurrentApplication().exit();
                } catch (err) {}
            }
            return;
        }

        // Color Keys on Smart TV Remotes:
        // Red: 403, Green: 404, Yellow: 405, Blue: 406
        if (code === 403 || key === 'ColorF0Red') {
            // Tecla Vermelha: Alterna entre Ao Vivo / Filmes / Séries
            e.preventDefault();
            const types = ['LIVE', 'MOVIE', 'SERIES'];
            let current = window.activeType || 'LIVE';
            let next = types[(types.indexOf(current) + 1) % types.length];
            if (typeof window.switchActiveType === 'function') {
                window.switchActiveType(next);
            }
            return;
        } else if (code === 404 || key === 'ColorF1Green') {
            // Tecla Verde: Atualizar Lista
            e.preventDefault();
            if (typeof window.forceRefreshPlaylist === 'function') {
                window.forceRefreshPlaylist();
            }
            return;
        } else if (code === 405 || key === 'ColorF2Yellow') {
            // Tecla Amarela: Espelhar na TV
            e.preventDefault();
            if (typeof window.openWebCastModal === 'function') {
                window.openWebCastModal();
            }
            return;
        } else if (code === 406 || key === 'ColorF3Blue') {
            // Tecla Azul: Alternar Tela Cheia
            e.preventDefault();
            if (playerContainer) {
                if (!document.fullscreenElement) {
                    if (playerContainer.requestFullscreen) playerContainer.requestFullscreen();
                    else if (playerContainer.webkitRequestFullscreen) playerContainer.webkitRequestFullscreen();
                } else {
                    if (document.exitFullscreen) document.exitFullscreen();
                }
            }
            return;
        }

        // Media Controls (Samsung Tizen, LG webOS, Android TV, Universal Remotes)
        // Play/Pause, MediaPlay, MediaPause, MediaPlayPause - Toggle Playback
        const isPlayPauseAction = 
            key === 'Play/Pause' || 
            key === 'MediaPlay' || 
            key === 'MediaPause' || 
            key === 'MediaPlayPause' || 
            key === 'Play' || 
            key === 'Pause' || 
            key === 'PlaySpeed' ||
            e.code === 'MediaPlayPause' ||
            e.code === 'MediaPlay' ||
            e.code === 'MediaPause' ||
            code === 10252 || 
            code === 179 || 
            code === 415 || 
            code === 250 || 
            code === 19 || 
            code === 413 ||
            (code === 32 && activeTag !== 'input' && activeTag !== 'textarea');

        if (isPlayPauseAction) {
            e.preventDefault();
            if (video) {
                if (video.paused) {
                    video.play().catch(() => {});
                    if (typeof showNotification === 'function') showNotification('▶ Reproduzindo', 'info');
                } else {
                    video.pause();
                    if (typeof showNotification === 'function') showNotification('⏸ Pausado', 'info');
                }
            }
            return;
        }
        if (code === 414 || code === 413 || key === 'MediaStop' || e.code === 'MediaStop') {
            e.preventDefault();
            if (typeof window.stopStream === 'function') window.stopStream();
            return;
        }
        if (code === 417 || key === 'MediaFastForward') {
            e.preventDefault();
            if (video && video.duration && isFinite(video.duration)) {
                video.currentTime = Math.min(video.duration, video.currentTime + 15);
            }
            return;
        }
        if (code === 412 || key === 'MediaRewind') {
            e.preventDefault();
            if (video && video.duration && isFinite(video.duration)) {
                video.currentTime = Math.max(0, video.currentTime - 15);
            }
            return;
        }

        switch (key) {
            case 'ArrowUp':
            case 'ChannelUp':
            case 'PageUp':
                e.preventDefault();
                // Troca para canal anterior
                switchChannelByOffset(-1);
                break;

            case 'ArrowDown':
            case 'ChannelDown':
            case 'PageDown':
                e.preventDefault();
                // Troca para próximo canal
                switchChannelByOffset(1);
                break;

            case 'ArrowLeft':
                if (video) {
                    e.preventDefault();
                    if (video.duration && isFinite(video.duration) && video.duration > 0) {
                        // VOD: retrocede 10s
                        video.currentTime = Math.max(0, video.currentTime - 10);
                        const rewindIndicator = document.getElementById('yt-rewind-indicator');
                        if (rewindIndicator) {
                            rewindIndicator.classList.remove('opacity-0');
                            setTimeout(() => rewindIndicator.classList.add('opacity-0'), 400);
                        }
                    } else {
                        // Ao Vivo: diminui volume
                        video.volume = Math.max(0, video.volume - 0.1);
                        if (typeof showNotification === 'function') {
                            showNotification(`Volume: ${Math.round(video.volume * 100)}%`, 'info');
                        }
                    }
                }
                break;

            case 'ArrowRight':
                if (video) {
                    e.preventDefault();
                    if (video.duration && isFinite(video.duration) && video.duration > 0) {
                        // VOD: avança 10s
                        video.currentTime = Math.min(video.duration, video.currentTime + 10);
                        const forwardIndicator = document.getElementById('yt-forward-indicator');
                        if (forwardIndicator) {
                            forwardIndicator.classList.remove('opacity-0');
                            setTimeout(() => forwardIndicator.classList.add('opacity-0'), 400);
                        }
                    } else {
                        // Ao Vivo: aumenta volume
                        video.volume = Math.min(1, video.volume + 0.1);
                        if (typeof showNotification === 'function') {
                            showNotification(`Volume: ${Math.round(video.volume * 100)}%`, 'info');
                        }
                    }
                }
                break;

            case ' ':
            case 'k':
            case 'K':
                if (video) {
                    e.preventDefault();
                    if (video.paused) {
                        video.play().catch(() => {});
                    } else {
                        video.pause();
                    }
                }
                break;

            case 'f':
            case 'F':
                if (playerContainer) {
                    e.preventDefault();
                    if (!document.fullscreenElement) {
                        if (playerContainer.requestFullscreen) {
                            playerContainer.requestFullscreen();
                        } else if (playerContainer.webkitRequestFullscreen) {
                            playerContainer.webkitRequestFullscreen();
                        }
                    } else {
                        if (document.exitFullscreen) {
                            document.exitFullscreen();
                        }
                    }
                }
                break;

            case 'm':
            case 'M':
                if (video) {
                    e.preventDefault();
                    video.muted = !video.muted;
                    if (typeof showNotification === 'function') {
                        showNotification(video.muted ? 'Áudio Mutado' : 'Áudio Ativado', 'info');
                    }
                }
                break;
        }
    }

    // ==========================================
    // 4. SLEEP TIMER (MODO SONECA)
    // ==========================================
    let sleepTimerSecondsRemaining = 0;
    let sleepTimerInterval = null;

    function setSleepTimer(minutes) {
        clearInterval(sleepTimerInterval);
        const badge = document.getElementById('yt-sleep-badge');
        const menu = document.getElementById('yt-sleep-menu');
        if (menu) menu.classList.add('hidden');

        if (minutes <= 0) {
            sleepTimerSecondsRemaining = 0;
            if (badge) badge.classList.add('hidden');
            if (typeof showNotification === 'function') {
                showNotification('Modo Soneca Desativado', 'info');
            }
            return;
        }

        sleepTimerSecondsRemaining = minutes * 60;
        if (badge) {
            badge.textContent = `${minutes}m`;
            badge.classList.remove('hidden');
        }

        if (typeof showNotification === 'function') {
            showNotification(`Modo Soneca ativado para ${minutes} minutos. 🌙`, 'success');
        }

        sleepTimerInterval = setInterval(() => {
            sleepTimerSecondsRemaining--;
            if (badge) {
                const m = Math.ceil(sleepTimerSecondsRemaining / 60);
                badge.textContent = `${m}m`;
            }
            if (sleepTimerSecondsRemaining <= 0) {
                clearInterval(sleepTimerInterval);
                if (badge) badge.classList.add('hidden');
                const video = document.getElementById('iptv-video');
                if (video) video.pause();
                if (typeof showNotification === 'function') {
                    showNotification('Modo Soneca ativado. Reprodução pausada automaticamente! 🌙', 'info');
                }
            }
        }, 1000);
    }

    // ==========================================
    // 5. PLAYBACK SPEED (VELOCIDADE ATÉ 4X)
    // ==========================================
    function setPlaybackSpeed(speed) {
        const video = document.getElementById('iptv-video');
        if (!video) return;
        video.playbackRate = speed;
        video.preservesPitch = true;
        if ('webkitPreservesPitch' in video) video.webkitPreservesPitch = true;
        if ('mozPreservesPitch' in video) video.mozPreservesPitch = true;
        window.currentPlaybackSpeed = speed;
        const label = document.getElementById('yt-speed-label');
        if (label) label.textContent = `${speed}x`;
        const menu = document.getElementById('yt-speed-menu');
        if (menu) menu.classList.add('hidden');
        if (typeof showNotification === 'function') {
            showNotification(`Velocidade de reprodução: ${speed}x ⚡`, 'info');
        }
    }

    // ==========================================
    // 6. AUDIO BOOSTER (+200% GANHO)
    // ==========================================
    let audioCtx = null;
    let gainNode = null;
    let audioSource = null;

    function setAudioBoost(factor) {
        const video = document.getElementById('iptv-video');
        if (!video) return;

        try {
            if (!audioCtx) {
                const AudioContext = window.AudioContext || window.webkitAudioContext;
                audioCtx = new AudioContext();
                gainNode = audioCtx.createGain();
                audioSource = audioCtx.createMediaElementSource(video);
                audioSource.connect(gainNode);
                gainNode.connect(audioCtx.destination);
            }
            if (audioCtx.state === 'suspended') {
                audioCtx.resume();
            }
            gainNode.gain.value = factor;
            const label = document.getElementById('yt-boost-label');
            if (label) label.textContent = `${Math.round(factor * 100)}%`;
            const menu = document.getElementById('yt-boost-menu');
            if (menu) menu.classList.add('hidden');
            if (typeof showNotification === 'function') {
                showNotification(`Ganho de Áudio: ${Math.round(factor * 100)}% ${factor > 1 ? '⚡ (Boost)' : ''}`, 'info');
            }
        } catch (err) {
            console.warn('Web Audio Booster:', err);
        }
    }

    // ==========================================
    // 7. ATUALIZAÇÃO FORÇADA DE LISTA (WEB)
    // ==========================================
    window.forceRefreshPlaylist = async function() {
        const refreshIcon = document.getElementById('refresh-playlist-icon');
        if (refreshIcon) refreshIcon.classList.add('fa-spin');

        if (typeof showNotification === 'function') {
            showNotification('Sincronizando canais, filmes e séries do servidor...', 'info');
        }

        try {
            if (typeof performLogin === 'function') {
                await performLogin(true);
                if (typeof showNotification === 'function') {
                    showNotification('Lista atualizada com sucesso!', 'success');
                }
            }
        } catch (e) {
            if (typeof showNotification === 'function') {
                showNotification('Erro ao sincronizar com o servidor: ' + e.message, 'error');
            }
        } finally {
            if (refreshIcon) {
                setTimeout(() => refreshIcon.classList.remove('fa-spin'), 1000);
            }
        }
    };

    // ==========================================
    // 8. WEB CAST & SMART TV MIRRORING ENGINE
    // ==========================================
    function initGoogleCast() {
        if (!document.getElementById('google-cast-sdk')) {
            const script = document.createElement('script');
            script.id = 'google-cast-sdk';
            script.src = 'https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1';
            document.head.appendChild(script);
        }

        window['__onGCastApiAvailable'] = function(isAvailable) {
            if (isAvailable && window.cast && window.cast.framework) {
                try {
                    window.cast.framework.CastContext.getInstance().setOptions({
                        receiverApplicationId: chrome.cast.media.DEFAULT_MEDIA_RECEIVER_APP_ID,
                        autoJoinPolicy: chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED
                    });
                } catch (e) {
                    console.log('[Cast] init:', e);
                }
            }
        };
    }

    window.triggerCastToTv = async function() {
        const video = document.getElementById('iptv-video');
        if (!video) return;

        // 1. Tentar AirPlay no Safari / Apple devices
        if (window.WebKitPlaybackTargetAvailabilityEvent || typeof video.webkitShowPlaybackTargetPicker === 'function') {
            try {
                video.webkitShowPlaybackTargetPicker();
                return;
            } catch (e) {
                console.log('AirPlay target picker:', e);
            }
        }

        // 2. Tentar Remote Playback API nativa (Chrome / Edge / Android)
        if (video.remote && typeof video.remote.prompt === 'function') {
            try {
                await video.remote.prompt();
                if (typeof showNotification === 'function') {
                    showNotification('Conectando à Smart TV...', 'info');
                }
                return;
            } catch (e) {
                console.log('Remote playback prompt cancelled or fallback needed:', e);
            }
        }

        // 3. Tentar Google Cast SDK se disponível
        if (window.cast && window.cast.framework) {
            try {
                const context = window.cast.framework.CastContext.getInstance();
                await context.requestSession();
                const session = context.getCurrentSession();
                if (session) {
                    const currentSrc = video.currentSrc || video.src;
                    const mediaInfo = new chrome.cast.media.MediaInfo(currentSrc, 'application/x-mpegURL');
                    mediaInfo.metadata = new chrome.cast.media.GenericMediaMetadata();
                    const titleElem = document.getElementById('player-title');
                    mediaInfo.metadata.title = titleElem ? titleElem.textContent : 'MK21 Streaming';
                    const request = new chrome.cast.media.LoadRequest(mediaInfo);
                    await session.loadMedia(request);
                    if (typeof showNotification === 'function') {
                        showNotification('Transmitindo para a Smart TV!', 'success');
                    }
                    return;
                }
            } catch (e) {
                console.log('Google Cast fallback:', e);
            }
        }

        // 4. Abrir modal interativo de espelhamento
        window.openWebCastModal();
    };

    window.openWebCastModal = function() {
        const modal = document.getElementById('web-cast-modal');
        if (!modal) return;
        const video = document.getElementById('iptv-video');
        const streamUrlInput = document.getElementById('cast-stream-url');
        if (streamUrlInput && video) {
            streamUrlInput.value = video.currentSrc || video.src || window.location.href;
        }
        modal.classList.remove('hidden');
        modal.classList.add('flex');
    };

    window.closeWebCastModal = function() {
        const modal = document.getElementById('web-cast-modal');
        if (modal) {
            modal.classList.add('hidden');
            modal.classList.remove('flex');
        }
    };

    window.copyCastUrl = function() {
        const input = document.getElementById('cast-stream-url');
        if (input) {
            input.select();
            navigator.clipboard.writeText(input.value).then(() => {
                if (typeof showNotification === 'function') {
                    showNotification('Link da transmissão copiado com sucesso!', 'success');
                }
            });
        }
    };

    function registerSmartTvEnvironment() {
        // 1. Samsung Tizen Key Registration
        if (window.tizen && window.tizen.tvinputdevice) {
            const tizenKeys = [
                'MediaPlay', 'MediaPause', 'MediaStop', 'MediaFastForward', 'MediaRewind',
                'MediaPlayPause', 'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue',
                'ChannelUp', 'ChannelDown', 'VolumeUp', 'VolumeDown', 'VolumeMute'
            ];
            tizenKeys.forEach(k => {
                try {
                    window.tizen.tvinputdevice.registerKey(k);
                } catch (e) {}
            });
        }

        // 2. Detect Smart TV User-Agent (Tizen, webOS, Android TV, SmartTV, AppleTV)
        const ua = navigator.userAgent.toLowerCase();
        const isTv = ua.includes('tizen') || ua.includes('webos') || ua.includes('smart-tv') || ua.includes('smarttv') || ua.includes('googletv') || ua.includes('appletv') || ua.includes('crkey');
        if (isTv) {
            document.body.classList.add('smart-tv-device');
            console.log('[MK21] Dispositivo Smart TV detectado.');
        }
    }

    // Expose helpers globally
    window.mk21Remote = {
        showOsd: showRemoteOsdBanner,
        switchChannel: switchChannelByOffset,
        getSavedProgress: getSavedProgress,
        setSleepTimer: setSleepTimer,
        setPlaybackSpeed: setPlaybackSpeed,
        setAudioBoost: setAudioBoost,
        triggerCastToTv: window.triggerCastToTv,
        openWebCastModal: window.openWebCastModal,
        closeWebCastModal: window.closeWebCastModal
    };

    // Initialize listeners when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            registerSmartTvEnvironment();
            setupInstallButtons();
            initGoogleCast();
            setupVideoResumeWatcher();
            window.addEventListener('keydown', handleRemoteKeyNavigation);
        });
    } else {
        registerSmartTvEnvironment();
        setupInstallButtons();
        initGoogleCast();
        setupVideoResumeWatcher();
        window.addEventListener('keydown', handleRemoteKeyNavigation);
    }

})();
