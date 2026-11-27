package com.fakeskyline.html2apk.template;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
public class MainActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  web = new WebView(this);
  WebSettings s = web.getSettings();
  s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
  s.setAllowFileAccess(true); s.setAllowContentAccess(true);
  s.setMediaPlaybackRequiresUserGesture(false);
  web.setWebViewClient(new WebViewClient()); web.setWebChromeClient(new WebChromeClient());
  setContentView(web);
  if(state == null) web.loadUrl("file:///android_asset/www/index.html");
 }
 @Override public void onBackPressed() { if(web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
