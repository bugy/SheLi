package net.buggy.shoplist.crashes;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import net.buggy.components.ViewUtils;
import net.buggy.shoplist.R;

import org.acra.dialog.BaseCrashReportDialog;

public class ShopListCrashDialog extends BaseCrashReportDialog {

    @Override
    protected void init(@Nullable Bundle savedInstanceState) {
        super.init(savedInstanceState);

        setContentView(R.layout.crash_dialog);

        final Button reportButton = findViewById(
                R.id.crash_dialog_report_button);
        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCrash(null, null);

                finish();
            }
        });

        final Button closeButton = findViewById(
                R.id.crash_dialog_close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelReports();

                finish();
            }
        });


        final Button restartButton = findViewById(
                R.id.crash_dialog_restart_button);
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelReports();

                Intent i = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(getBaseContext().getPackageName());
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                finish();

                startActivity(i);
            }
        });

        final ImageView image = findViewById(
                R.id.crash_dialog_image);
        ViewUtils.setTint(image, R.color.color_primary_icons);
    }
}
