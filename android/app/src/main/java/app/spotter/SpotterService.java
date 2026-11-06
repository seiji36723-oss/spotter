package app.spotter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Iterator;

// 画面を閉じていても、通知（ロック画面・通知センター）にいまの状態を出し、ボタン（完了・飛ばす・一時停止）で操作できるようにする。
// 段階の時刻はアプリの画面（index.html）から受け取る。プランクの 3・2・1 と、時間になった時の音・振動はここで鳴らす
// （画面を閉じるとページの中の時計は止まることがあるため）。
public class SpotterService extends Service {
  static final String CH = "spotter_now";
  static final int ID = 1;
  static final String ACT = "app.spotter.ACT";
  static SpotterService inst;
  static String pendingJson; // サービスが動き出す前に届いた状態

  private final Handler h = new Handler(Looper.getMainLooper());
  private JSONArray segs = new JSONArray();
  private boolean paused;
  private long pausedAt;
  private final HashSet<Long> fired = new HashSet<Long>(); // もう鳴らした時刻（同じ音を2回鳴らさない）
  private String shownKey = "";
  private boolean fg;
  private boolean dead; // 止めた（このあと届いた状態は、新しいサービスに渡す）
  private long lockUntil;
  private PowerManager.WakeLock wl;
  private ToneGenerator tone;

  private final Runnable tick = new Runnable() {
    public void run() { onTick(); }
  };

  // アプリの画面から：状態が変わった（segs が空なら通知を消す）
  static void update(Context c, final String json) {
    final SpotterService s = inst;
    if (s != null) {
      s.h.post(new Runnable() { public void run() { s.apply(json); } });
      return;
    }
    if (count(json) == 0) { pendingJson = null; return; }
    pendingJson = json;
    Intent i = new Intent(c, SpotterService.class);
    try {
      if (Build.VERSION.SDK_INT >= 26) Context.class.getMethod("startForegroundService", Intent.class).invoke(c, i);
      else c.startService(i);
    } catch (Exception e) { /* 始められなくても、アプリの画面はそのまま動く */ }
  }

  static int count(String json) {
    try { return new JSONObject(json).getJSONArray("segs").length(); } catch (Exception e) { return 0; }
  }

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public void onCreate() {
    super.onCreate();
    inst = this;
    dead = false;
    makeChannel();
    try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 90); } catch (Exception e) { tone = null; }
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (pendingJson != null) { String j = pendingJson; pendingJson = null; apply(j); }
    if (!fg) { // startForegroundService のあとは、すぐに通知を出す決まり
      startForeground(ID, build());
      fg = true;
    }
    if (intent != null && ACT.equals(intent.getAction())) onAction(intent.getStringExtra("a"));
    if (segs.length() == 0) stopNow();
    return START_NOT_STICKY;
  }

  @Override public void onDestroy() {
    h.removeCallbacks(tick);
    lock(0);
    if (tone != null) tone.release();
    if (inst == this) inst = null;
    super.onDestroy();
  }

  void apply(String json) {
    if (dead) { update(getApplicationContext(), json); return; }
    try {
      JSONObject o = new JSONObject(json);
      segs = o.getJSONArray("segs");
      boolean p = o.optBoolean("paused");
      if (p && !paused) pausedAt = System.currentTimeMillis();
      paused = p;
    } catch (Exception e) { segs = new JSONArray(); }
    if (segs.length() == 0) { stopNow(); return; }
    skipPast(System.currentTimeMillis(), 900);
    shownKey = "";
    onTick();
  }

  // 新しい段階を受け取った：もう過ぎた音は鳴らさない（飛ばした・打ち切ったときに鳴らないように）。
  // ただし ▶ を押した直後の最初の「3」は鳴らす（サービスが動き出すまでの遅れを見て 0.9秒まで）
  void skipPast(long now, long grace) {
    for (int q = 0; q < segs.length(); q++) {
      JSONObject g = segs.optJSONObject(q);
      if (g == null) continue;
      long s = g.optLong("s"), e = g.optLong("e");
      if (g.optBoolean("beep")) for (int k = 0; k < 3; k++) { long t = s + k * 1000L; if (t <= now - grace) fired.add(t); }
      if ((g.optBoolean("alarm") || g.optBoolean("restEnd")) && e > 0 && e <= now) fired.add(-e);
    }
  }

  void stopNow() {
    dead = true;
    if (inst == this) inst = null;
    h.removeCallbacks(tick);
    lock(0);
    if (fg) { stopForeground(true); fg = false; }
    stopSelf();
  }

  // 通知のボタン：画面（ページ）に伝える。飛ばす・一時停止は、ページの返事を待たずに通知も先に進める
  void onAction(String a) {
    if (a == null) return;
    long now = System.currentTimeMillis();
    try {
      // 古い通知のボタンなど、いまの段階にないボタンは、ページに伝えるだけ（通知は先に動かさない）
      JSONObject cg = segs.length() > 0 ? segs.getJSONObject(current(now)) : null;
      JSONArray ca = cg != null ? cg.optJSONArray("acts") : null;
      boolean here = ca != null && ca.toString().contains("\"" + a + "\"");
      if (here && "skip".equals(a) && !paused) {
        int i = current(now);
        JSONObject g = segs.getJSONObject(i);
        long e = g.optLong("e");
        if (e > now) {
          long d = e - now;
          g.put("e", now);
          fired.add(-now); // 飛ばした休憩の終わりの音は鳴らさない
          for (int q = i + 1; q < segs.length(); q++) shift(segs.getJSONObject(q), -d);
        }
      } else if (here && "pause".equals(a)) {
        if (paused) {
          long d = now - pausedAt;
          for (int q = 0; q < segs.length(); q++) shift(segs.getJSONObject(q), d);
          paused = false;
          skipPast(now, 0); // 止める前に鳴らした音は、ずらしても鳴らし直さない
        } else { paused = true; pausedAt = now; }
      }
    } catch (Exception e) { /* 形が違うときは、ページの返事にまかせる */ }
    shownKey = "";
    MainActivity.sendAction(a);
    onTick();
  }

  static void shift(JSONObject g, long d) throws Exception {
    if (g.optLong("s") > 0) g.put("s", g.optLong("s") + d);
    if (g.optLong("e") > 0) g.put("e", g.optLong("e") + d);
  }

  // いまの段階：始まっていて、まだ終わっていないもの（なければ最初か最後）
  int current(long now) {
    int k = 0;
    for (int q = 0; q < segs.length(); q++) {
      JSONObject g = segs.optJSONObject(q);
      if (g == null) continue;
      long s = g.optLong("s"), e = g.optLong("e");
      if (s <= now) k = q;
      if (s <= now && (e == 0 || now < e)) return q;
    }
    return k;
  }

  void onTick() {
    h.removeCallbacks(tick);
    if (segs.length() == 0) return;
    long now = System.currentTimeMillis();
    long next = 0; // この先に鳴らす時刻のうち、いちばん遅いもの
    if (!paused) {
      for (int q = 0; q < segs.length(); q++) {
        JSONObject g = segs.optJSONObject(q);
        if (g == null) continue;
        long s = g.optLong("s"), e = g.optLong("e");
        if (g.optBoolean("beep")) {
          for (int k = 0; k < 3; k++) {
            long t = s + k * 1000L;
            if (e > 0 && t >= e) break;
            if (t <= now && now - t < 1500 && fired.add(t)) beep(1);
            if (t > now) next = Math.max(next, t);
          }
        }
        if (g.optBoolean("alarm") && e > 0) {
          if (e <= now && now - e < 20000 && fired.add(-e)) { loud(new int[] { 3000 }); vibrate(new long[] { 0, 400, 150, 400, 150, 400, 150, 400 }); }
          if (e > now) next = Math.max(next, e);
        }
        if (g.optBoolean("restEnd") && e > 0) { // 休憩の目安になった
          if (e <= now && now - e < 20000 && fired.add(-e)) { loud(new int[] { 150, 120, 150 }); vibrate(new long[] { 0, 150, 100, 150 }); }
          if (e > now) next = Math.max(next, e);
        }
      }
    }
    for (Iterator<Long> it = fired.iterator(); it.hasNext();) if (now - Math.abs(it.next()) > 60000) it.remove();
    lock(next > now ? next - now + 30000 : 0); // 鳴らす予定がある間だけ、画面を閉じても止まらないようにする
    int i = current(now);
    String key = (paused ? "p" : "") + i + ":" + segs.optJSONObject(i);
    if (!key.equals(shownKey)) {
      shownKey = key;
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      try { nm.notify(ID, build()); } catch (Exception e) { /* 通知が許可されていない */ }
    }
    h.postDelayed(tick, next > now ? 200 : 1000);
  }

  void lock(long ms) {
    try {
      if (ms > 0) {
        if (wl == null) {
          wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spotter:timer");
          wl.setReferenceCounted(false); // 取り直すと、時間が後ろにのびる
        }
        long until = System.currentTimeMillis() + ms;
        if (!wl.isHeld() || until > lockUntil + 1000) { wl.acquire(ms); lockUntil = until; }
      } else if (wl != null && wl.isHeld()) wl.release();
    } catch (Exception e) { /* 使えなくても続ける */ }
  }

  void beep(int n) {
    if (tone == null) return;
    for (int k = 0; k < n; k++) {
      h.postDelayed(new Runnable() {
        public void run() { try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180); } catch (Exception e) { /* 鳴らせなくても続ける */ } }
      }, k * 250L);
    }
  }

  // 大きめの音（アラームの音量。どれも同じ大きさで、長さで聞き分ける）：ms = { 鳴らす, 休む, 鳴らす, … } のミリ秒
  // プランクの時間：{ 3000 }（ピーーー）、休憩の終わり：{ 150, 120, 150 }（ピッピッ）
  void loud(int[] ms) {
    final int rate = 44100, period = rate / 1050;
    int total = 0;
    for (int m : ms) total += m;
    final short[] buf = new short[rate * total / 1000];
    int pos = 0;
    for (int q = 0; q < ms.length; q++) {
      int n = rate * ms[q] / 1000, fade = rate / 100;
      for (int k = 0; k < n && pos < buf.length; k++, pos++) {
        if (q % 2 == 1) continue; // 休む
        float env = Math.min(1f, Math.min(k, n - k) / (float) fade);
        buf[pos] = (short) (((pos % period) < period / 2 ? 16000 : -16000) * env);
      }
    }
    try {
      final AudioTrack t = new AudioTrack(AudioManager.STREAM_ALARM, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, buf.length * 2, AudioTrack.MODE_STATIC);
      t.write(buf, 0, buf.length);
      t.play();
      h.postDelayed(new Runnable() { public void run() { try { t.release(); } catch (Exception e) { /* 終わっている */ } } }, total + 500L);
    } catch (Exception e) { /* 鳴らせなくても続ける */ }
  }

  void vibrate(long[] pattern) {
    try {
      Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      if (v != null) v.vibrate(pattern, -1);
    } catch (Exception e) { /* 対応していない端末 */ }
  }

  // 通知の置き場所（Android 8 以降）。音は鳴らさず（音はこのサービスが鳴らす）、ロック画面にも出す
  void makeChannel() {
    if (Build.VERSION.SDK_INT < 26) return;
    try {
      Class<?> cc = Class.forName("android.app.NotificationChannel");
      Object ch = cc.getConstructor(String.class, CharSequence.class, int.class).newInstance(CH, "トレーニング中の状態", 3);
      cc.getMethod("setSound", android.net.Uri.class, android.media.AudioAttributes.class).invoke(ch, null, null);
      cc.getMethod("enableVibration", boolean.class).invoke(ch, false);
      cc.getMethod("setLockscreenVisibility", int.class).invoke(ch, Notification.VISIBILITY_PUBLIC);
      cc.getMethod("setShowBadge", boolean.class).invoke(ch, false);
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      NotificationManager.class.getMethod("createNotificationChannel", cc).invoke(nm, ch);
    } catch (Exception e) { /* 作れなくても続ける */ }
  }

  Notification build() {
    Notification.Builder b;
    try {
      b = Build.VERSION.SDK_INT >= 26
        ? Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(this, CH)
        : new Notification.Builder(this);
    } catch (Exception e) { b = new Notification.Builder(this); }
    long now = System.currentTimeMillis();
    JSONObject g = segs.length() > 0 ? segs.optJSONObject(current(now)) : null;
    if (g == null) g = new JSONObject();
    long s = g.optLong("s"), e = g.optLong("e");
    Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    b.setSmallIcon(R.drawable.ic_stat)
      .setContentTitle(g.optString("t", "SPOTTER"))
      .setContentText(g.optString("n", ""))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setCategory(Notification.CATEGORY_PROGRESS)
      .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    // 時間：残りを数える段階は終わる時刻まで、ほかは始まった時刻から数える（通知の時計は端末が動かす）
    if (!paused && (g.optBoolean("down") ? e > now : s > 0)) {
      b.setShowWhen(true).setUsesChronometer(true).setWhen(g.optBoolean("down") ? e : s);
      if (g.optBoolean("down")) {
        try { Notification.Builder.class.getMethod("setChronometerCountDown", boolean.class).invoke(b, true); } catch (Exception x) { /* Android 7 より前 */ }
      }
    } else b.setShowWhen(false);
    JSONArray acts = g.optJSONArray("acts");
    for (int k = 0; acts != null && k < acts.length() && k < 3; k++) {
      String a = acts.optString(k);
      String label = "done".equals(a) ? "完了" : "skip".equals(a) ? "飛ばす" : "start".equals(a) ? "スタート" : "pause".equals(a) ? (paused ? "再開" : "一時停止") : null;
      if (label == null) continue;
      int icon = "pause".equals(a) ? (paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
        : "skip".equals(a) ? android.R.drawable.ic_media_next : android.R.drawable.ic_media_play;
      Intent i = new Intent(this, SpotterService.class).setAction(ACT).putExtra("a", a);
      int code = "start".equals(a) ? 11 : "done".equals(a) ? 12 : "skip".equals(a) ? 13 : 14; // ボタンごとに別の番号（同じだと中身が上書きされる）
      b.addAction(icon, label, PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
    return b.build();
  }
}
