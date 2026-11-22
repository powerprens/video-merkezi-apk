package com.yusaa.videomerkezi;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.widget.Toast;

/**
 * PackageInstaller oturum sonucunu karsilar; kullanici onayindan
 * sonra sistem kurulumu tamamlar.
 */
public class InstallResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        int status = i != null ? i.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE) : PackageInstaller.STATUS_FAILURE;
        String msg = i != null ? i.getStringExtra(
                PackageInstaller.EXTRA_STATUS_MESSAGE) : null;

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            try {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) startActivity(confirm);
            } catch (Exception e) {
                Toast.makeText(this, "Kurulum onayi acilamadi",
                        Toast.LENGTH_LONG).show();
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(this, "Video Merkezi guncellendi",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Kurulum tamamlanamadi: " + msg,
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
