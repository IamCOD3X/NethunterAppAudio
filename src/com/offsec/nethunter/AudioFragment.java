package com.offsec.nethunter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import java.util.Arrays;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;


public class AudioFragment extends AppCompatActivity {

    public static final int DEFAULT_INDEX_BUFFER_HEADROOM = 4;
    public static final int DEFAULT_INDEX_TARGET_LATENCY = 6;

    private static final List<Long> VALUES_BUFFER_HEADROOM =
            Arrays.asList(0L, 15625L, 31250L, 62500L, 125000L, 250000L, 500000L, 1000000L, 2000000L);
    private static final List<Long> VALUES_TARGET_LATENCY =
            Arrays.asList(0L, 15625L, 31250L, 62500L, 125000L, 250000L, 500000L, 1000000L, 2000000L, 5000000L, 10000000L, -1L);

    private Button playButton = null;
    private Spinner bufferHeadroomSpinner;
    private Spinner targetLatencySpinner;
    private AudioBufferSizeAdapter bufferHeadroomAdapter;
    private AudioBufferSizeAdapter targetLatencyAdapter;
    private EditText serverInput;
    private EditText portInput;
    private CheckBox autoStartCheckBox = null;
    private TextView errorText;

    private AudioPlaybackService boundService;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            boundService = ((AudioPlaybackService.LocalBinder) service).getService();

            boundService.playState().observe(AudioFragment.this,
                    playState -> updatePlayState(playState));

            boundService.showNotification();

            updatePrefs(boundService);

            if (boundService.getAutostartPref() && boundService.isStartable()) {
                play();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            boundService.playState().removeObservers(AudioFragment.this);

            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            boundService = null;
        }
    };

    // Method to get the day with suffix (st, nd, rd, th)
    private String getDayWithSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return day + "th";
        }
        switch (day % 10) {
            case 1: return day + "st";
            case 2: return day + "nd";
            case 3: return day + "rd";
            default: return day + "th";
        }
    }

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.audio, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio);

        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String dayWithSuffix = getDayWithSuffix(day);

        TextView appInfoLabel = findViewById(R.id.appInfoLabel);
        TextView builderinfoLabel = findViewById(R.id.builderinfoLabel);
        TextView buildTimeLabel = findViewById(R.id.buildTimeLabel);
        serverInput = findViewById(R.id.EditTextServer);
        portInput = findViewById(R.id.EditTextPort);
        autoStartCheckBox = findViewById(R.id.auto_start);
        playButton = findViewById(R.id.ButtonPlay);
        errorText = findViewById(R.id.errorText);
        bufferHeadroomSpinner = findViewById(R.id.bufferHeadroomSpinner);
        targetLatencySpinner = findViewById(R.id.targetLatencySpinner);

        bufferHeadroomAdapter = new AudioBufferSizeAdapter(this, VALUES_BUFFER_HEADROOM);
        targetLatencyAdapter = new AudioBufferSizeAdapter(this, VALUES_TARGET_LATENCY);

        // Set the current date and time in the buildTimeLabel TextView
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy | HH:mm:ss", Locale.getDefault());
        String dateAndTime = dayWithSuffix + " " + sdf.format(new Date());
        buildTimeLabel.setText("Build Time : "+ dateAndTime);

        // Set Maintainer Info
        String builderinfo = getString(R.string.builderinfo);
        builderinfoLabel.setText("Maintainer : "+ builderinfo);

        // Set App Info
        String appInfo = getString(R.string.appInfo);
        appInfoLabel.setText("Info : "+ appInfo);

        playButton.setOnClickListener(v -> {
            if (boundService.getPlayState().isActive()) {
                stop();
            } else {
                play();
            }
        });

        bindService(new Intent(this, AudioPlaybackService.class),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        super.onDestroy();
    }

    private void updatePrefs(AudioPlaybackService service) {
        serverInput.setText(service.getServerPref());
        int port = service.getPortPref();
        portInput.setText(port > 0 ? Integer.toString(port) : "");
        autoStartCheckBox.setChecked(service.getAutostartPref());
        setUpSpinner(bufferHeadroomSpinner, bufferHeadroomAdapter, service.getBufferHeadroom(), DEFAULT_INDEX_BUFFER_HEADROOM);
        setUpSpinner(targetLatencySpinner, targetLatencyAdapter, service.getTargetLatency(), DEFAULT_INDEX_TARGET_LATENCY);
    }

    private void setUpSpinner(Spinner spinner, AudioBufferSizeAdapter adapter, long value, int defaultIndex) {
        spinner.setOnItemSelectedListener(null);

        spinner.setAdapter(adapter);

        int pos = adapter.getItemPosition(value);
        spinner.setSelection(pos >= 0 ? pos : defaultIndex);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (boundService != null) {
                    long headroomUsec = bufferHeadroomAdapter.getItem(bufferHeadroomSpinner.getSelectedItemPosition());
                    long latencyUsec = targetLatencyAdapter.getItem(targetLatencySpinner.getSelectedItemPosition());
                    boundService.setBufferUsec(headroomUsec, latencyUsec);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updatePlayState(@Nullable AudioPlayState playState) {
        if (playState == null) {
            playButton.setText(R.string.btn_waiting);
            playButton.setEnabled(false);
            errorText.setText(""); // Clear any existing error messages
            return;
        }

        switch (playState) {
            case STOPPED:
                playButton.setText(R.string.btn_play);
                playButton.setEnabled(true);
                appendErrorText("Disconnected State", getResources().getColor(android.R.color.holo_orange_light));
                appendDashes();
                break;
            case STARTING:
                playButton.setText(R.string.btn_starting);
                playButton.setEnabled(true);
                appendErrorText("Connection Started", getResources().getColor(android.R.color.holo_green_dark));
                break;
            case BUFFERING:
                playButton.setText(R.string.btn_buffering);
                playButton.setEnabled(true);
                appendErrorText("Establishing Connection", getResources().getColor(android.R.color.holo_orange_light));
                break;
            case STARTED:
                playButton.setText(R.string.btn_stop);
                playButton.setEnabled(true);
                appendErrorText("Everything is working fine! Enjoy!", getResources().getColor(android.R.color.holo_green_dark));
                appendDashes();
                break;
            case STOPPING:
                playButton.setText(R.string.btn_stopping);
                playButton.setEnabled(false);
                appendErrorText("Connection Disconnecting", getResources().getColor(android.R.color.holo_red_light));
                break;
        }

        // Handle any errors
        Throwable error = boundService == null ? null : boundService.getError();
        if (error != null) {
            String text = formatMessage(error);
            appendErrorText("An error occurred: " + boundService.getError().getMessage(), getResources().getColor(android.R.color.holo_red_dark));
            appendDashes();
        }
    }

    private void appendDashes() {
        String dashes = "------------------\n";
        SpannableString spannable = new SpannableString(dashes);
        int purpleColor = getResources().getColor(android.R.color.holo_purple);
        spannable.setSpan(new ForegroundColorSpan(purpleColor), 0, spannable.length(), 0); // Set color
        errorText.append(spannable);
    }

    private void appendErrorText(String newErrorMessage, int color) {
        if (!newErrorMessage.isEmpty()) {
            // Create a SpannableString to apply color
            SpannableString spannable = new SpannableString(newErrorMessage + "\n");
            spannable.setSpan(new ForegroundColorSpan(color), 0, spannable.length(), 0);
            errorText.append(spannable);
        }
    }

    public void play() {
        String server = serverInput.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString());
            if (port < 1 || port > 65535) { // Port range check
                portInput.setError("Port must be between 1 and 65535");
                return;
            }
        } catch (NumberFormatException e) {
            portInput.setError("Invalid port number");
            return;
        }
        portInput.setError(null);

        if (boundService != null) {
            boundService.setPrefs(server, port, autoStartCheckBox.isChecked());
            boundService.play(server, port);
        }
    }

    public void stop() {
        if (boundService != null) {
            boundService.stop();
        }
    }

    @NonNull
    private String formatMessage(Throwable error) {
        String msg = error.getLocalizedMessage();
        return error.getClass().getName()
                + (msg == null ? "" : ": " + msg);
    }

}
