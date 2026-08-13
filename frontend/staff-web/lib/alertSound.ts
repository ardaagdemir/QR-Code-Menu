/**
 * Section 6: "Mümkünse sesli/görsel uyarı verilsin" - kasa ekranında bir sipariş
 * kritik/gecikmiş hale geldiğinde çalınan kısa uyarı sesi. Harici ses dosyasına bağımlı
 * olmamak için Web Audio API ile anlık üretiliyor; bazı tarayıcılar kullanıcı
 * etkileşimi olmadan sesi engelleyebileceğinden hata sessizce yutulur.
 */
export function playCriticalOrderAlert(): void {
  try {
    const AudioContextClass =
      window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) {
      return;
    }
    const context = new AudioContextClass();
    const now = context.currentTime;

    [0, 0.22].forEach((offset) => {
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(880, now + offset);
      gain.gain.setValueAtTime(0, now + offset);
      gain.gain.linearRampToValueAtTime(0.2, now + offset + 0.02);
      gain.gain.linearRampToValueAtTime(0, now + offset + 0.18);
      oscillator.connect(gain);
      gain.connect(context.destination);
      oscillator.start(now + offset);
      oscillator.stop(now + offset + 0.2);
    });

    setTimeout(() => context.close().catch(() => undefined), 600);
  } catch {
    // Ses çalınamazsa görsel uyarı (kritik rozet/kart rengi) zaten yeterli sinyali verir.
  }
}
