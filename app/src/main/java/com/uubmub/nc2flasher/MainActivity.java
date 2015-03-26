package com.uubmub.nc2flasher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
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

        flash.setEnabled(false);

        flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flash.setEnabled(false);
                progress.setVisibility(View.VISIBLE);
                new BinaryFlasher().execute();
            }
        });

        new Checks().execute();
    }

    class BinaryFlasher extends AsyncTask<Void, Void, Void> {
        void setError(final String action, Throwable t) {
            if (t == null)
                setText(action + ". Nothing was flashed.");
            else
                setText(action + ". Nothing was flashed.\n\"" + t.getMessage() + "\"");
        }

        void setText(final String text) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) findViewById(R.id.status_text)).setText(text);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            try (InputStream kernel = getAssets().open("nc2_kernel_boot.img")) {
                byte bs[] = new byte[8];
                kernel.read(bs);

                if (!Arrays.equals(bs, new byte[]{'A', 'N', 'D', 'R', 'O', 'I', 'D', '!'})) {
                    setError("Signature check failed", null);
                    return null;
                }
            } catch (IOException e) {
                setError("Error reading NC2 kernel", e);
                e.printStackTrace();
                return null;
            }

            setText("Copying kernel");
            File bootimg;
            try {
                InputStream src = getAssets().open("nc2_kernel_boot.img");
                bootimg = File.createTempFile("nc2_kernel_boot", ".img");
                copyFile(src, bootimg);
                src.close();
            } catch (IOException e) {
                setError("Error copying nc2_kernel_boot.img to a temp file", e);
                e.printStackTrace();
                return null;
            }

            setText("Flashing...");
            List<String> ddout = Shell.SU.run("busybox dd if=\"" + bootimg.getAbsolutePath() + "\" of=/dev/block/mmcblk0p14");
            setText("dd output: \n" + ddout.toString());

            bootimg.delete();

            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress.setVisibility(View.GONE);
                }
            });

            Looper.prepare();
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Reboot")
                    .setMessage("Your phone will reboot now.")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Shell.SU.run("reboot");
                            finish();
                            System.exit(0);
                        }
                    })
                    .show();
            Looper.loop();

            return null;
        }
    }

    class Checks extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);

            boolean haveSS = new File("/etc/safestrap").exists();
            boolean haveBBX = new File("/system/xbin/busybox").exists();

            if (!haveSS || !haveBBX) {
                Looper.prepare();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Safestrap/Busybox")
                        .setMessage("Either safestrap or busybox could not be found on this device. Both are needed to flash NC2.")
                        .setIcon(android.R.drawable.ic_delete)
                        .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
                Looper.loop();
            } else {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        flash.setEnabled(true);
                    }
                });
            }

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
