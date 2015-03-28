package com.benwaffle.nc2flasher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.*;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.util.*;

import eu.chainfire.libsuperuser.Shell;

public class MainActivity extends ActionBarActivity {
    Button flash;
    ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        flash = (Button) findViewById(R.id.button);
        progress = (ProgressBar) findViewById(R.id.progressBar);

        ((ListView) findViewById(R.id.listView)).setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {{
            add("For SM-N900A only!");
            add("I am not responsible for anything that may happen to your device.");
            add("Make sure you can flash NL1 or OC1 back.");
        }});

        flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flash.setEnabled(false);
                progress.setVisibility(View.VISIBLE);
                new BinaryFlasher().execute();
            }
        });

        if (!new File("/etc/safestrap").exists() || !new File("/system/xbin/busybox").exists()) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage("Either safestrap or busybox could not be found on this device. Both are needed to flash NC2.")
                    .setIcon(android.R.drawable.ic_delete)
                    .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        } else {
            flash.setEnabled(true);
        }
    }

    void update(final String msg) {
        runOnUiThread(new Runnable() { public void run() {
            ((TextView) findViewById(R.id.status_text)).setText(msg);
        } });
    }

    Void err(final String msg, final Throwable t) {
        runOnUiThread(new Runnable() { public void run() {
            flash.setEnabled(false);
            progress.setVisibility(View.GONE);
            if (t == null) ((TextView) findViewById(R.id.status_text)).setText(msg);
            else {
                ((TextView) findViewById(R.id.status_text)).setText(msg + "\n" + t.getMessage());
                Log.e("NC2 Flasher", msg, t);
                t.printStackTrace();
            }
        } });
        return null;
    }

    class BinaryFlasher extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            try (InputStream kernel = getAssets().open("nc2_kernel_boot.img")) {
                byte bs[] = new byte[8];
                kernel.read(bs);
                if (!Arrays.equals(bs, new byte[]{'A', 'N', 'D', 'R', 'O', 'I', 'D', '!'}))
                    return err("Signature check failed", null);
            } catch (IOException e) {
                return err("Error reading NC2 kernel", e);
            }

            update("Copying kernel");
            File bootimg;
            try {
                InputStream src = getAssets().open("nc2_kernel_boot.img");
                bootimg = File.createTempFile("nc2_kernel_boot", ".img");
                copyFile(src, bootimg);
                src.close();
            } catch (IOException e) {
                return err("Error copying nc2_kernel_boot.img to a temp file", e);
            }

            update("Flashing...");
            List<String> ddout = Shell.SU.run("busybox dd if=\"" + bootimg.getAbsolutePath() + "\" of=/dev/block/mmcblk0p14");
            update("dd output: \n" + ddout.toString());
            Shell.SU.run("echo 1 > /data/.recovery_mode");
            bootimg.delete();

            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    progress.setVisibility(View.GONE);
                    new AlertDialog.Builder(MainActivity.this)
                            .setCancelable(false)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setNeutralButton("tap to reboot", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Shell.SU.run("reboot");
                                }
                            })
                            .show();
                }
            });

            return null;
        }
    }

    public static void copyFile(InputStream in, File dest) throws IOException {
        FileOutputStream out = new FileOutputStream(dest);

        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1)
            out.write(buffer, 0, read);

        out.close();
    }
}
