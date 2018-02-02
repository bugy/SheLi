package net.buggy.shoplist.units;


import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.buggy.components.ViewUtils;
import net.buggy.shoplist.BuildConfig;
import net.buggy.shoplist.R;
import net.buggy.shoplist.ShopListActivity;
import net.buggy.shoplist.units.views.InflatingViewRenderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.os.Process.myPid;
import static net.buggy.shoplist.ShopListActivity.MAIN_VIEW_ID;
import static net.buggy.shoplist.ShopListActivity.TOOLBAR_VIEW_ID;

public class AboutAppUnit extends Unit<ShopListActivity> {

    @Override
    public void initialize() {
        addRenderer(MAIN_VIEW_ID, new MainViewRenderer());

        final InflatingViewRenderer<ShopListActivity, ViewGroup> toolbarRenderer =
                new InflatingViewRenderer<>(R.layout.unit_about_toolbar);
        addRenderer(TOOLBAR_VIEW_ID, toolbarRenderer);
    }

    private class MainViewRenderer extends net.buggy.shoplist.units.views.ViewRenderer<ShopListActivity, ViewGroup> {
        @Override
        public void renderTo(ViewGroup parentView, ShopListActivity activity) {
            final LayoutInflater inflater = LayoutInflater.from(parentView.getContext());
            inflater.inflate(R.layout.unit_about, parentView, true);

            final TextView versionView = parentView.findViewById(R.id.unit_about_version);

            String version = ViewUtils.getVersion(activity);

            final String versionString = activity.getString(R.string.version_pattern, version);
            versionView.setText(versionString);

            if (BuildConfig.DEBUG) {
                initDebugPanel(parentView, activity);
            }
        }

        private void initDebugPanel(ViewGroup parentView, final ShopListActivity activity) {
            final ViewGroup debugPanel = parentView.findViewById(
                    R.id.unit_about_debug_panel);
            final Button exceptionButton = parentView.findViewById(
                    R.id.unit_about_exception_button);
            final Button sendLogsButton = parentView.findViewById(
                    R.id.unit_about_send_logs_button);

            debugPanel.setVisibility(View.VISIBLE);

            exceptionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    throw new RuntimeException("This is debug exception. To test reporting");
                }
            });

            sendLogsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String log = getLog();

                    final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
                    emailIntent.setType("text/plain");
                    emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{ShopListActivity.DEVELOPER_EMAIL});
                    emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "SheLi logs");
                    emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, log);

                    emailIntent.setType("message/rfc822");

                    try {
                        activity.startActivity(Intent.createChooser(emailIntent,
                                "Send email using..."));
                    } catch (ActivityNotFoundException ex) {
                        ClipboardManager clipboard =
                                (ClipboardManager) activity.getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("logs", log);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(activity, "No email client, logs copied to clipboard",
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    public static String getLog() {
        final String processId = Integer.toString(myPid());

        final StringBuilder builder = new StringBuilder();

        try {
            String[] command = new String[]{"logcat", "-d", "-v", "threadtime"};

            Process process = Runtime.getRuntime().exec(command);

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(processId)) {
                    builder.append(line).append("\n");
                }
            }
        } catch (IOException ex) {
            Log.e("AboutAppUnit", "getLog: getting logs failed", ex);
            throw new RuntimeException(ex.getMessage(), ex);
        }

        return builder.toString();
    }
}
