package com.nimbusgida.bayiharitasi;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int RC_SIGN_IN = 3101;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleClient;
    private WebView webView;
    private ListenerRegistration registration;
    private boolean canEdit = false;
    private boolean pageReady = false;
    private String lastJson = "{\"provinces\":{},\"updatedAt\":null}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(getResources().getIdentifier("default_web_client_id", "string", getPackageName())))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) showLogin();
        else openApp(user);
    }

    private void showLogin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(245, 248, 252));

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(180, 180);
        logoParams.setMargins(0, 0, 0, 28);
        root.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("Nimbus Gıda Bayi Haritası");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(30, 71, 124));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(this);
        desc.setText("Bayi verilerini görüntülemek ve güncellemek için Google hesabınla giriş yap.");
        desc.setTextSize(14);
        desc.setTextColor(Color.rgb(71, 84, 103));
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, 16, 0, 28);
        root.addView(desc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button button = new Button(this);
        button.setText("Google ile giriş yap");
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setOnClickListener(v -> startActivityForResult(googleClient.getSignInIntent(), RC_SIGN_IN));
        root.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120));

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) return;

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential).addOnCompleteListener(this, authTask -> {
                if (authTask.isSuccessful()) openApp(auth.getCurrentUser());
                else toast("Firebase giriş başarısız: " + (authTask.getException() != null ? authTask.getException().getMessage() : ""));
            });
        } catch (Exception e) {
            toast("Google giriş başarısız: " + e.getMessage());
        }
    }

    private void openApp(FirebaseUser user) {
        String email = user != null && user.getEmail() != null ? user.getEmail() : "";
        canEdit = email.toLowerCase().endsWith("@nimbusfood.com.tr");

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                injectUserAndData();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/app.html");

        startFirestoreListener();
    }

    private void startFirestoreListener() {
        if (registration != null) registration.remove();
        registration = db.collection("companyData").document("bayiHaritasi")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        toast("Veri okunamadı: " + e.getMessage());
                        return;
                    }
                    if (snapshot != null && snapshot.exists() && snapshot.getData() != null) {
                        lastJson = new JSONObject(snapshot.getData()).toString();
                    } else {
                        lastJson = "{\"provinces\":{},\"updatedAt\":null}";
                    }
                    injectUserAndData();
                });
    }

    private void injectUserAndData() {
        if (!pageReady || webView == null) return;
        FirebaseUser user = auth.getCurrentUser();
        String email = user != null && user.getEmail() != null ? user.getEmail() : "";
        String safeEmail = JSONObject.quote(email);
        String safeJson = JSONObject.quote(lastJson);
        webView.post(() -> {
            webView.evaluateJavascript("nativeSetUser(" + safeEmail + "," + (canEdit ? "true" : "false") + ");", null);
            webView.evaluateJavascript("nativeSetData(" + safeJson + ");", null);
        });
    }

    private Map<String, Object> jsonToMap(JSONObject json) throws Exception {
        Map<String, Object> ret = new HashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof JSONObject) value = jsonToMap((JSONObject) value);
            else if (value instanceof JSONArray) value = jsonToList((JSONArray) value);
            else if (value == JSONObject.NULL) value = null;
            ret.put(key, value);
        }
        return ret;
    }

    private List<Object> jsonToList(JSONArray array) throws Exception {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONObject) value = jsonToMap((JSONObject) value);
            else if (value instanceof JSONArray) value = jsonToList((JSONArray) value);
            else if (value == JSONObject.NULL) value = null;
            list.add(value);
        }
        return list;
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    public class Bridge {
        @JavascriptInterface public void pageReady() {
            pageReady = true;
            injectUserAndData();
        }

        @JavascriptInterface public void saveData(String jsonText) {
            if (!canEdit) {
                toast("Yetkili değilsin. Sadece görüntüleyebilirsin.");
                return;
            }
            try {
                JSONObject obj = new JSONObject(jsonText);
                Map<String, Object> map = jsonToMap(obj);
                db.collection("companyData").document("bayiHaritasi")
                        .set(map, SetOptions.merge())
                        .addOnFailureListener(e -> toast("Kayıt başarısız: " + e.getMessage()));
            } catch (Exception e) {
                toast("Kayıt verisi işlenemedi: " + e.getMessage());
            }
        }

        @JavascriptInterface public void signOut() {
            auth.signOut();
            googleClient.signOut();
            runOnUiThread(() -> showLogin());
        }

        @JavascriptInterface public void toast(String msg) {
            MainActivity.this.toast(msg);
        }
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
