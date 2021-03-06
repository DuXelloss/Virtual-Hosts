/*
**Copyright (C) 2017  xfalcon
**
**This program is free software: you can redistribute it and/or modify
**it under the terms of the GNU General Public License as published by
**the Free Software Foundation, either version 3 of the License, or
**(at your option) any later version.
**
**This program is distributed in the hope that it will be useful,
**but WITHOUT ANY WARRANTY; without even the implied warranty of
**MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**GNU General Public License for more details.
**
**You should have received a copy of the GNU General Public License
**along with this program.  If not, see <http://www.gnu.org/licenses/>.
**
*/

package com.github.xfalcon.vhosts;
import android.support.v7.app.AlertDialog;
import com.baidu.mobstat.StatService;
import android.content.*;
import android.net.Uri;
import android.net.VpnService;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import com.github.xfalcon.vhosts.vservice.VhostsService;
import com.suke.widget.SwitchButton;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int VPN_REQUEST_CODE = 0x0F;
    private static final int SELECT_FILE_CODE = 0x05;
    public static final String PREFS_NAME = MainActivity.class.getName();
    public static final String HOSTS_URI = "HOST_URI";
    private Uri uri = null;

    private boolean waitingForVPNStart;

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (VhostsService.BROADCAST_VPN_STATE.equals(intent.getAction())) {
                if (intent.getBooleanExtra("running", false))
                    waitingForVPNStart = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatService.autoTrace(this, true,false);
        setContentView(R.layout.activity_main);
        final SwitchButton vpnButton = (SwitchButton) findViewById(R.id.button_start_vpn);
        final Button selcetHosts = (Button) findViewById(R.id.button_select_hosts);
        uri = getUriByPREFS();
        if (uri == null) {
            selcetHosts.setText(getString(R.string.select_hosts));
        }
        vpnButton.setOnCheckedChangeListener(new SwitchButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(SwitchButton view, boolean isChecked) {
                Log.d(TAG, vpnButton.isChecked() + "");
                if (isChecked) {
                    if (uri == null) {
                        showDialog();
                    } else {
                        startVPN();
                    }
                } else {
                    shutdownVPN();
                }
            }
        });
        selcetHosts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectFile();
            }
        });
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(VhostsService.BROADCAST_VPN_STATE));
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(intent, SELECT_FILE_CODE);
    }

    private void startVPN() {
        waitingForVPNStart = false;
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
    }

    private Uri getUriByPREFS() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String uri_path = settings.getString(HOSTS_URI, null);
        if (uri_path != null) {
            uri = Uri.parse(uri_path);
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                try {
                    inputStream.close();
                } catch (IOException e) {

                }
            } catch (FileNotFoundException e) {
                Log.d(TAG, "HOSTS FILE NOT FOUND");
                return null;
            }
            return uri;
        }
        return null;
    }

    private void setUriByPREFS(Intent intent) {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        uri = intent.getData();
        final int takeFlags = intent.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getContentResolver().takePersistableUriPermission(uri, takeFlags);
        editor.putString(HOSTS_URI, uri.toString());
        editor.commit();
        setButton(false);
    }

    private void shutdownVPN() {
        if (VhostsService.isRunning())
            startService(new Intent(this, VhostsService.class).setAction(VhostsService.ACTION_DISCONNECT));
        setButton(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            waitingForVPNStart = true;
            startService(new Intent(this, VhostsService.class).setAction(VhostsService.ACTION_CONNECT).setData(uri));
            setButton(false);
        } else if (requestCode == SELECT_FILE_CODE && resultCode == RESULT_OK) {
            setUriByPREFS(data);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setButton(!waitingForVPNStart && !VhostsService.isRunning());
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void setButton(boolean enable) {
        final SwitchButton vpnButton = (SwitchButton) findViewById(R.id.button_start_vpn);
        final Button selectHosts = (Button) findViewById(R.id.button_select_hosts);
        if (enable) {
            vpnButton.setChecked(false);
            selectHosts.setAlpha(1.0f);
            selectHosts.setClickable(true);
        } else {
            vpnButton.setChecked(true);
            selectHosts.setAlpha(.5f);
            selectHosts.setClickable(false);
        }
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_message);
        builder.setPositiveButton(R.string.dialog_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                selectFile();
            }
        });
        builder.show();
    }

}
