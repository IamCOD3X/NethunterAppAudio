package com.offsec.nethunter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.gps.KaliGPSUpdates;
import com.offsec.nethunter.gps.LocationUpdateService;
import com.offsec.nethunter.utils.NhPaths;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class KaliGpsServiceFragment extends Fragment implements KaliGPSUpdates.Receiver {
    private static final String TAG = "KaliGpsServiceFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private KaliGPSUpdates.Provider gpsProvider = null;
    private TextView gpsTextView;
    private Context context;
    private boolean wantKismet = false;
    private boolean wantHelpView = true;
    private boolean reattachedToRunningService = false;
    private SwitchCompat switch_gps_provider = null;
    private SwitchCompat switch_gpsd = null;
    private String rtlsdr = "";
    private String rtlamr = "";
    private String rtladsb = "";
    private String mousejack = "";

    public KaliGpsServiceFragment() {
    }

    public static KaliGpsServiceFragment newInstance(int sectionNumber) {
        KaliGpsServiceFragment fragment = new KaliGpsServiceFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.gps, container, false);
    }

    private void setCheckedQuietly(CompoundButton button, boolean state) {
        button.setTag("quiet");
        button.setChecked(state);
        button.setTag(null);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gpsTextView = view.findViewById(R.id.gps_textview);
        TextView gpsHelpView = view.findViewById(R.id.gps_help);
        switch_gps_provider = view.findViewById(R.id.switch_gps_provider);
        switch_gpsd = view.findViewById(R.id.switch_gpsd);
        Button button_launch_app = view.findViewById(R.id.gps_button_launch_app);
        ShellExecuter exe = new ShellExecuter();
        Spinner wlanSpinner = (Spinner) view.findViewById(R.id.wlan_interface);

        // Get the list of available WiFi adapters
        List<String> wlanAdapters = getAvailableWifiAdapters();

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_item, wlanAdapters);

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        wlanSpinner.setAdapter(adapter);
        EditText bt_interface = view.findViewById(R.id.bt_interface);
        CheckBox sdrcheckbox = view.findViewById(R.id.rtlsdr);
        CheckBox sdramrcheckbox = view.findViewById(R.id.rtlamr);
        CheckBox sdradsbcheckbox = view.findViewById(R.id.rtladsb);
        CheckBox mousejackcheckbox = view.findViewById(R.id.mousejack);

        // TODO: make this text dynamic so we can launch other apps besides Kismet
        button_launch_app.setText("Launch Kismet in NH Terminal");
        if (!wantHelpView)
            gpsHelpView.setVisibility(View.GONE);
        Log.d(TAG, "reattachedToRunningService: " + reattachedToRunningService);
        if (reattachedToRunningService) {
            // gpsTextView.append("Service already running\n");
            setCheckedQuietly(switch_gps_provider, true);
        }

        // check if gpsd is already running
        check_gpsd();

        switch_gps_provider.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (switch_gps_provider.getTag() != null)
                return;
            Log.d(TAG, "switch_gps_provider clicked: " + isChecked);
            if (isChecked) {
                startGpsProvider();
            } else {
                stopGpsProvider();
            }
        });

        switch_gpsd.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (switch_gpsd.getTag() != null)
                return;
            Log.d(TAG, "switch_gpsd clicked: " + isChecked);
            if (isChecked) {
                startChrootGpsd();
            } else {
                stopChrootGpsd();
            }
        });

        button_launch_app.setOnClickListener(view1 -> {
            if (!switch_gps_provider.isChecked()) {
                gpsTextView.append("Android GPS Provider not running!\n");
                switch_gps_provider.setChecked(true);
                startGpsProvider();
            }
            if (!switch_gpsd.isChecked()) {
                gpsTextView.append("chroot gpsd not running!\n");
                switch_gpsd.setChecked(true);
                startChrootGpsd();
            }
            //WLAN interface
            String wlaniface = wlanSpinner.getSelectedItem().toString();
            if (!wlaniface.isEmpty()) wlaniface = "source=" + wlaniface + "\n";

            //BT interface
            String btiface = bt_interface.getText().toString();
            if (!btiface.isEmpty()) btiface = "source=" + btiface + "\n";

            //SDR sensors interface
            if (sdrcheckbox.isChecked()) rtlsdr = "source=rtl433-0\n";
            else rtlsdr = "";

            //SDR AMR interface
            if (sdramrcheckbox.isChecked()) rtlamr = "source=rtlamr-0\n";
            else rtlamr = "";

            //SDR ADSB interface
            if (sdradsbcheckbox.isChecked()) rtladsb = "source=rtladsb-0\n";
            else rtladsb = "";

            //Mousejack interface
            if (mousejackcheckbox.isChecked())
                mousejack = "source=mousejack:name=nRF,channel_hoprate=100/sec\n";
            else mousejack = "";

            String conf = "log_template=%p/%n\nlog_prefix=/captures/kismet/\ngps=gpsd:host=localhost,port=2947\n" + wlaniface + btiface + rtlsdr + rtlamr + rtladsb + mousejack;
            try {
                exe.RunAsRoot();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                exe.RunAsRoot();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Toast.makeText(requireActivity().getApplicationContext(), "Starting Kismet.. Web UI will be available at localhost:2501\"", Toast.LENGTH_LONG).show();
            wantKismet = true;
            gpsTextView.append("Kismet will launch after next position received.  Waiting...\n");
        });
    }

    private List<String> getAvailableWifiAdapters() {
        List<String> wlanAdapters = new ArrayList<>();
        ShellExecuter exe = new ShellExecuter();
        String command = "ls /sys/class/net/";
        String response = exe.RunAsRootOutput(command);
        String[] interfaces = response.split("\n");
        for (String iface : interfaces) {
            if (iface.startsWith("wlan")) {
                wlanAdapters.add(iface);
            }
        }
        return wlanAdapters;
    }

    public static class ShellExecuter {
        public String RunAsRootOutput(String cmd) {
            StringBuilder result = new StringBuilder();
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                InputStream is = p.getInputStream();
                os.writeBytes(cmd + "\n");
                os.writeBytes("exit\n");
                os.flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result.toString();
        }


        public void RunAsRoot() throws IOException {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("exit\n");
                os.flush();
            } catch (IOException ignored) {
                // do nothing
            }
        }
    }

    private void startGpsProvider() {
        if (gpsProvider != null) {
            gpsTextView.append("Starting Android GPS Publisher\n");
            gpsTextView.append("GPS NMEA messages will be sent to udp://127.0.0.1:" + NhPaths.GPS_PORT + "\n");
            gpsProvider.onLocationUpdatesRequested(KaliGpsServiceFragment.this);
        }
    }

    private void stopGpsProvider() {
        if (gpsProvider != null) {
            gpsTextView.append("Stopping Android GPS Publisher\n");
            gpsProvider.onStopRequested();
        }
    }

    private void startChrootGpsd() {
        gpsTextView.append("Starting gpsd in Kali chroot\n");
        // do this in a thread because it takes a second or two and lags the UI
        new Thread(() -> {
            ShellExecuter exe = new ShellExecuter();
            String command = "su -c '" + NhPaths.APP_SCRIPTS_PATH + File.separator + "bootkali start_gpsd " + NhPaths.GPS_PORT + "'";
            Log.d(TAG, command);
            String response = exe.RunAsRootOutput(command);
            Log.d(TAG, "Response = " + response);
        }).start();
    }

    private void stopChrootGpsd() {
        gpsTextView.append("Stopping gpsd in Kali chroot\n");
        // do this in a thread because it takes a second or two and lags the UI
        new Thread(() -> {
            ShellExecuter exe = new ShellExecuter();
            String command = "su -c '" + NhPaths.APP_SCRIPTS_PATH + File.separator + "stop-gpsd'";
            Log.d(TAG, command);
            exe.RunAsRootOutput(command);
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if(LocationUpdateService.isInstanceCreated()) {
            // a LocationUpdateService is already running
            setCheckedQuietly(switch_gps_provider, true);
            // make sure it has a handle to this fragment so it can display updates
            reattachedToRunningService = this.gpsProvider.onReceiverReattach(this);
        } else
        {
            setCheckedQuietly(switch_gps_provider, false);
        }

        // check if gpsd is already running
        check_gpsd();
    }

    private void check_gpsd() {
        ShellExecuter exe = new ShellExecuter();
        String command = "pgrep gpsd";
        Log.d(TAG, "command = " + command);
        String response = exe.RunAsRootOutput(command);
        Log.d(TAG, "response = '" + response + "'");
        setCheckedQuietly(switch_gpsd, !response.isEmpty());
    }

    @Override
    public void onAttach(@NonNull Context context) {
        if (context instanceof KaliGPSUpdates.Provider) {
            this.gpsProvider = (KaliGPSUpdates.Provider) context;
            reattachedToRunningService = this.gpsProvider.onReceiverReattach(this);
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // we've already granted permissions, make the nag message go away
            wantHelpView = false;
        }

        super.onAttach(context);
    }

    @Override
    public void onPositionUpdate(String nmeaSentences) {
        CharSequence charSequence = gpsTextView.getText();
        int lineCnt = 0;
        int i;
        for(i=charSequence.length()-1; i >= 0; i--) {
            if (charSequence.charAt(i) == '\n')
                lineCnt++;
            if (lineCnt >= 20)
                break;
        }
        // delete anything more than X lines previous so this doesn't get huge
        if(i > 0) {
            gpsTextView.getEditableText().delete(0, i);
        }

        gpsTextView.append(nmeaSentences + "\n");
        if(wantKismet) {
            wantKismet = false;
            gpsTextView.append("Launching kismet in NetHunter Terminal\n");
            startKismet();
        }
    }

    @Override
    public void onFirstPositionUpdate() {
    }

    private void startKismet() {
        try {
            run_cmd("/usr/bin/start-kismet");
        } catch (Exception e) {
            NhPaths.showMessage(context, getString(R.string.toast_install_terminal));
        }
    }

    public void run_cmd(String cmd) {
        Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        context.startActivity(intent);
    }
}
