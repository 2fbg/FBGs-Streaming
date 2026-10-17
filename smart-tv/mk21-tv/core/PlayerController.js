// smart-tv/mk21-tv/core/PlayerController.js - Atomic Stream Management
class SafePlayerController {
  constructor() {
    this.video = null;
    this.hls = null;
    this.state = 'IDLE';
  }

  init(videoSelector) {
    this.video = document.querySelector(videoSelector);
  }

  async play(channel) {
    if (!this.video) return;
    this.state = 'BUFFERING';
    this.cleanupCurrentStream();

    if (window.Hls && window.Hls.isSupported() && channel.url.includes('.m3u8')) {
      this.hls = new window.Hls({
        lowLatencyMode: true,
        startFragPrefetch: true,
        backBufferLength: 10,
        maxBufferLength: 15,
        maxMaxBufferLength: 30,
        enableWorker: true
      });

      this.hls.attachMedia(this.video);
      this.hls.on(window.Hls.Events.MEDIA_ATTACHED, () => this.hls.loadSource(channel.url));
      this.hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
        this.video.play().catch(e => console.warn('Autoplay bloqueado:', e));
        this.state = 'PLAYING';
      });
      this.hls.on(window.Hls.Events.ERROR, (event, data) => this.handleHlsError(data));
    } else {
      this.video.src = channel.url;
      this.video.play().catch(e => console.warn('Erro de reprodução nativa:', e));
    }
  }

  cleanupCurrentStream() {
    if (this.hls) {
      this.hls.stopLoad();
      this.hls.detachMedia();
      this.hls.destroy();
      this.hls = null;
    }
    if (this.video) {
      this.video.pause();
      this.video.removeAttribute('src');
      this.video.load();
    }
  }

  handleHlsError(data) {
    if (!data.fatal) return;
    switch (data.type) {
      case window.Hls.ErrorTypes.NETWORK_ERROR:
        console.warn('[Player] Falha de rede. Tentando reconexão...');
        this.hls.startLoad();
        break;
      case window.Hls.ErrorTypes.MEDIA_ERROR:
        console.warn('[Player] Erro de decodificação. Recuperando...');
        this.hls.recoverMediaError();
        break;
      default:
        console.error('[Player] Erro fatal irreversível. Reiniciando player...');
        this.cleanupCurrentStream();
        this.state = 'ERROR';
        break;
    }
  }
}

window.SafePlayerController = SafePlayerController;
