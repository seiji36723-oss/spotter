package app.spotter;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

// アプリを開くと、アプリの中に入っている index.html を全画面で表示する。
// 記録はこの中（端末の中）に保存される。
// トレーニング中は、いまの状態を通知（SpotterService）に渡し、通知のボタンで押した操作をページに伝える。
public class MainActivity extends Activity {
  static MainActivity inst;
  static final ArrayList<String> pending = new ArrayList<String>(); // 画面がない間に通知で押した操作（開いた時にページが受け取る）
  private WebView web;
  private boolean asked;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    inst = this;
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true); // 記録の保存（localStorage）に必要
    s.setTextZoom(100);
    s.setMediaPlaybackRequiresUserGesture(false);
    web.setWebViewClient(new WebViewClient());
    web.addJavascriptInterface(new Bridge(), "SpotterNative");
    setContentView(web);
    if (savedInstanceState != null) web.restoreState(savedInstanceState);
    else web.loadUrl("file:///android_asset/index.html");
  }

  @Override
  protected void onDestroy() {
    if (inst == this) inst = null;
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    web.saveState(out);
  }

  @Override
  public void onBackPressed() {
    if (web.canGoBack()) web.goBack();
    else super.onBackPressed();
  }

  // 通知のボタンを押した：ページに伝える（画面がなければ、開いた時に渡す）
  static void sendAction(final String a) {
    final MainActivity m = inst;
    if (m == null) {
      synchronized (pending) { pending.add(a); }
      return;
    }
    m.runOnUiThread(new Runnable() {
      public void run() { m.web.evaluateJavascript("window.spotterAction&&window.spotterAction(" + JSONObject.quote(a) + ")", null); }
    });
  }

  // 通知を出してよいか、トレーニングを始めた時に一度だけ聞く（Android 13 以降）
  void askNotify() {
    if (asked || Build.VERSION.SDK_INT < 33) return;
    asked = true;
    final String perm = "android.permission.POST_NOTIFICATIONS";
    if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return;
    runOnUiThread(new Runnable() {
      public void run() { requestPermissions(new String[] { perm }, 1); }
    });
  }

  // ページから呼ぶ（window.SpotterNative）
  class Bridge {
    @JavascriptInterface
    public void update(String json) {
      if (SpotterService.count(json) > 0) askNotify();
      SpotterService.update(getApplicationContext(), json);
    }

    @JavascriptInterface
    public String takeActions() {
      JSONArray a = new JSONArray();
      synchronized (pending) {
        for (String x : pending) a.put(x);
        pending.clear();
      }
      return a.toString();
    }
  }
}
