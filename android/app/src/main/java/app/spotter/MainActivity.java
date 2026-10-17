package app.spotter;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// アプリを開くと、アプリの中に入っている index.html を全画面で表示する。
// 記録はこの中（端末の中）に保存される。
public class MainActivity extends Activity {
  private WebView web;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true); // 記録の保存（localStorage）に必要
    s.setTextZoom(100);
    web.setWebViewClient(new WebViewClient());
    setContentView(web);
    if (savedInstanceState != null) web.restoreState(savedInstanceState);
    else web.loadUrl("file:///android_asset/index.html");
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
}
