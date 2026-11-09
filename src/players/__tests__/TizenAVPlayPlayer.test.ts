/**
 * @jest-environment jsdom
 */
import { TizenAVPlayPlayer } from '../players/TizenAVPlayPlayer';
import type { TizenAVPlay, TizenStreamInfo } from '../players/TizenAVPlayPlayer';
import type { ErrorPayload, TracksReadyPayload } from '../types/player';

function createMockAVPlay(): jest.Mocked<TizenAVPlay> {
  return {
    open: jest.fn(),
    close: jest.fn(),
    setDisplay: jest.fn(),
    setDisplayRect: jest.fn(),
    setListener: jest.fn(),
    prepareAsync: jest.fn<void, [() => void, (err: unknown) => void]>((ok) => ok()),
    play: jest.fn(),
    pause: jest.fn(),
    stop: jest.fn(),
    seekTo: jest.fn(),
    getCurrentTime: jest.fn(() => 1000),
    getDuration: jest.fn(() => 60000),
    getState: jest.fn(() => 'IDLE' as const),
    setStreamingProperty: jest.fn(),
    setBufferingParam: jest.fn(),
    setSelectTrack: jest.fn(),
    getCurrentStreamInfo: jest.fn(() => []),
    getTotalTrackInfo: jest.fn(() => []),
    setVolume: jest.fn(),
  };
}

describe('TizenAVPlayPlayer', () => {
  let avplay: jest.Mocked<TizenAVPlay>;
  let video: HTMLVideoElement;

  beforeEach(() => {
    avplay = createMockAVPlay();
    (window as { webapis?: unknown }).webapis = { avplay };
    video = document.createElement('video');
    document.body.appendChild(video);
  });

  afterEach(() => {
    document.body.innerHTML = '';
    delete (window as { webapis?: unknown }).webapis;
  });

  it('configura buffer padrão de 3000ms no play', async () => {
    const player = new TizenAVPlayPlayer(video);
    await player.play('https://cdn.example.com/live.m3u8');
    expect(avplay.open).toHaveBeenCalledWith('https://cdn.example.com/live.m3u8');
    expect(avplay.setStreamingProperty).toHaveBeenCalledWith('BUFFER_SIZE', '3000');
    // API oficial: buffer em segundos, no estado IDLE.
    expect(avplay.setBufferingParam).toHaveBeenCalledWith('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', 3);
    expect(avplay.play).toHaveBeenCalled();
  });

  it('aceita buffer customizado via options', async () => {
    const player = new TizenAVPlayPlayer(video);
    await player.play('https://cdn.example.com/live.m3u8', { bufferSizeMs: 8000 });
    expect(avplay.setStreamingProperty).toHaveBeenCalledWith('BUFFER_SIZE', '8000');
    expect(avplay.setBufferingParam).toHaveBeenCalledWith('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', 8);
  });

  it('propaga erro fatal via evento "error"', async () => {
    avplay.getState.mockReturnValue('PLAYING' as never);
    const player = new TizenAVPlayPlayer(video);
    const onError = jest.fn();
    player.on('error', onError);

    await player.play('https://cdn.example.com/live.m3u8');

    // Captura o listener registrado e simula erro do firmware
    const listener = avplay.setListener.mock.calls[0][0];
    listener.onerror('PLAYER_ERROR_CONNECTION_FAILED');

    expect(onError).toHaveBeenCalled();
    const payload = onError.mock.calls[0][0] as ErrorPayload;
    expect(payload.fatal).toBe(true);
    expect(payload.code).toBe('PLAYER_ERROR_CONNECTION_FAILED');
    expect(player.getState()).toBe('error');
  });

  it('emite buffering → buffered durante transição de estado', async () => {
    const player = new TizenAVPlayPlayer(video);
    const events: string[] = [];
    player.on('buffering', () => events.push('buffering'));
    player.on('buffered', () => events.push('buffered'));

    await player.play('https://cdn.example.com/live.m3u8');
    const listener = avplay.setListener.mock.calls[0][0];
    listener.onbufferingstart();
    listener.onbufferingcomplete();

    expect(events).toEqual(['buffering', 'buffered']);
  });

  it('clamp do seek respeita duração', async () => {
    avplay.getDuration.mockReturnValue(60000);
    const player = new TizenAVPlayPlayer(video);
    await player.play('https://cdn.example.com/live.m3u8');
    player.seek(999999);
    expect(avplay.seekTo).toHaveBeenCalledWith(60000);
    player.seek(-500);
    expect(avplay.seekTo).toHaveBeenCalledWith(0);
  });

  it('seleciona faixa de áudio pelo id', async () => {
    avplay.getCurrentStreamInfo.mockReturnValue([
      { type: 'AUDIO', info: { track_num: 0, track_lang: 'por' } },
      { type: 'AUDIO', info: { track_num: 1, track_lang: 'eng' } },
    ]);
    const player = new TizenAVPlayPlayer(video);
    await player.play('https://cdn.example.com/live.m3u8');
    player.setAudioTrack('audio-1');
    expect(avplay.setSelectTrack).toHaveBeenCalledWith('AUDIO', 1);
  });

  it('lê as faixas do formato real da API (extra_info é JSON string)', async () => {
    const infos: TizenStreamInfo[] = [
      { type: 'AUDIO', index: 1, extra_info: '{"track_num":"1","track_lang":"por","fourCC":"AACL"}' },
      { type: 'AUDIO', index: 2, extra_info: '{"track_num":"2","track_lang":"eng"}' },
      { type: 'VIDEO', index: 0, extra_info: '{"fourCC":"H264"}' },
    ];
    avplay.getTotalTrackInfo.mockReturnValue(infos);
    const player = new TizenAVPlayPlayer(video);
    const onTracks = jest.fn();
    player.on('tracksReady', onTracks);

    await player.play('https://cdn.example.com/live.m3u8');

    const payload = onTracks.mock.calls[onTracks.mock.calls.length - 1][0] as TracksReadyPayload;
    expect(payload.audioTracks).toHaveLength(2);
    expect(payload.audioTracks[0]).toMatchObject({ id: 'audio-1', index: 1, language: 'por' });
    expect(payload.audioTracks[1]).toMatchObject({ id: 'audio-2', index: 2, language: 'eng' });

    player.setAudioTrack('audio-2');
    expect(avplay.setSelectTrack).toHaveBeenCalledWith('AUDIO', 2);
  });

  it('falha de prepare fecha o AVPlay e permite um novo play (sem travar)', async () => {
    const player = new TizenAVPlayPlayer(video);
    const onError = jest.fn();
    player.on('error', onError);

    avplay.prepareAsync.mockImplementationOnce((_ok, err) => {
      err(new Error('prepare falhou'));
    });

    await expect(player.play('https://cdn.example.com/ruim.m3u8')).rejects.toThrow('prepare falhou');

    expect(onError).toHaveBeenCalledTimes(1);
    expect((onError.mock.calls[0][0] as ErrorPayload).fatal).toBe(true);
    // Sem o close, o próximo open() falharia com InvalidStateError para sempre.
    expect(avplay.close).toHaveBeenCalled();
    expect(player.getState()).toBe('error');

    await player.play('https://cdn.example.com/boa.m3u8');
    expect(avplay.open).toHaveBeenCalledTimes(2);
    expect(avplay.play).toHaveBeenCalledTimes(1);
    expect(player.getState()).toBe('playing');
  });

  it('destroy emite "destroyed" antes de limpar os listeners', () => {
    const player = new TizenAVPlayPlayer(video);
    const onDestroyed = jest.fn();
    player.on('destroyed', onDestroyed);
    player.destroy();
    expect(onDestroyed).toHaveBeenCalledTimes(1);
    expect(avplay.close).toHaveBeenCalled();
  });

  it('expõe estado e volume', () => {
    const player = new TizenAVPlayPlayer(video);
    expect(player.getState()).toBe('idle');
    player.setVolume(0.3);
    expect(avplay.setVolume).toHaveBeenCalledWith(0.3);
  });
});
