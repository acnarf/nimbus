package com.nimbusgida.bayiharitasi;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://nimbus-bayi-haritasi.firebaseapp.com/?app=android_browser";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openInSecureBrowser();
    }

    private void openInSecureBrowser() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(APP_URL));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Tarayıcı açılamadı. Chrome yüklü olduğundan emin olun.", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
