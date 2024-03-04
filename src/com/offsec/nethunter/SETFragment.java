package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;


class SETFragment extends Fragment {
    private SharedPreferences sharedpreferences;
    private NhPaths nh;
    private Activity activity;
    private static final String ARG_SECTION_NUMBER = "section_number";

    public static SETFragment newInstance(int sectionNumber) {
        SETFragment fragment = new SETFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getContext();
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.set, container, false);
        SETFragment.TabsPagerAdapter tabsPagerAdapter = new TabsPagerAdapter(this);

        ViewPager2 mViewPager = rootView.findViewById(R.id.pagerBt);
        mViewPager.setAdapter(tabsPagerAdapter);
        mViewPager.setOffscreenPageLimit(3);
        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                activity.invalidateOptionsMenu();
            }
        });
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        setHasOptionsMenu(true);
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuinflater) {
        menuinflater.inflate(R.menu.bt, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.setup:
                RunSetup();
                return true;
            case R.id.update:
                RunUpdate();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void SetupDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.DialogStyleCompat);
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        builder.setTitle("Welcome to SET!");
        builder.setMessage("In order to make sure everything is working, an initial setup needs to be done.");
        builder.setPositiveButton("Check & Install", (dialog, which) -> {
            RunSetup();
            sharedpreferences.edit().putBoolean("set_setup_done", true).apply();
        });
        builder.show();

    }

    public void RunSetup() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;SET Setup\\007\" && clear;if [[ -d /root/setoolkit ]]; then echo 'SET is already installed'" +
                        ";else git clone https://github.com/yesimxev/social-engineer-toolkit /root/setoolkit && echo 'Successfully installed SET!';fi; echo 'Closing in 3secs..'; sleep 3 && exit ");
        sharedpreferences.edit().putBoolean("set_setup_done", true).apply();
    }

    public void RunUpdate() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;SET Update\\007\" && clear;if [[ -d /root/setoolkit ]]; then cd /root/setoolkit && git pull && echo 'Succesfully updated SET! Closing in 3secs..';else echo 'Please run SETUP first!';fi; sleep 3 && exit ");
        sharedpreferences.edit().putBoolean("set_setup_done", true).apply();
    }

    static class TabsPagerAdapter extends FragmentStateAdapter {
        public TabsPagerAdapter(@NonNull SETFragment fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return new MainFragment();
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        @NonNull
        public Fragment getItem() {
            return new MainFragment();
        }

        public int getCount() {
            return 1;
        }

        public CharSequence getPageTitle() {
            return "Email Template";
        }
    }

    public static class MainFragment extends SETFragment {
        private Context context;
        private NhPaths nh;
        final ShellExecuter exe = new ShellExecuter();
        private String selected_template;
        private String template_src;
        private String template_tempfile;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.set_main, container, false);
            SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
            EditText PhishName = rootView.findViewById(R.id.set_name);
            EditText PhishSubject = rootView.findViewById(R.id.set_subject);

            //First run
            Boolean setupdone = sharedpreferences.getBoolean("set_setup_done", false);
            if (!setupdone.equals(true))
                SetupDialog();

            //Templates spinner
            String[] templates = new String[]{"Messenger", "Facebook", "Twitter"};
            Spinner template_spinner = rootView.findViewById(R.id.set_template);
            template_spinner.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, templates));

            //Select Template
            WebView myBrowser = rootView.findViewById(R.id.mybrowser);
            template_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    selected_template = parentView.getItemAtPosition(pos).toString();
                    switch (selected_template) {
                        case "Messenger":
                            template_src = NhPaths.APP_SD_FILES_PATH + "/configs/set-messenger.html";
                            template_tempfile = "set-messenger.html";
                            PhishSubject.setText(PhishName.getText() + " sent you a message on Messenger.");
                            break;
                        case "Facebook":
                            template_src = NhPaths.APP_SD_FILES_PATH + "/configs/set-facebook.html";
                            template_tempfile = "set-facebook.html";
                            PhishSubject.setText(PhishName.getText() + " sent you a message on Facebook.");
                            break;
                        case "Twitter":
                            template_src = NhPaths.APP_SD_FILES_PATH + "/configs/set-twitter.html";
                            template_tempfile = "set-twitter.html";
                            PhishSubject.setText(PhishName.getText() + " sent you a Direct Message on Twitter!");
                            break;
                    }
                    myBrowser.clearCache(true);
                    myBrowser.loadUrl(template_src);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            Button ResetTemplate = rootView.findViewById(R.id.reset_template);
            Button SaveTemplate = rootView.findViewById(R.id.save_template);
            Button LaunchSET = rootView.findViewById(R.id.start_set);

            //Refresh Template
            Button RefreshPreview = rootView.findViewById(R.id.refreshPreview);
            RefreshPreview.setOnClickListener(v -> {
                refresh(rootView);
            });

            //Reset Template
            ResetTemplate.setOnClickListener(v -> {
                myBrowser.clearCache(true);
                myBrowser.loadUrl(template_src);
            });

            //Save Template
            SaveTemplate.setOnClickListener(v -> {
                refresh(rootView);
                final String template_path = "/sdcard/" + template_tempfile;
                String template_final = "/root/setoolkit/src/templates/" + selected_template + ".template";
                String phish_subject = PhishSubject.getText().toString();
                exe.RunAsChrootOutput("echo 'SUBJECT=\"" + phish_subject + "\"' > " + template_final + " && echo 'HTML=\"' >> " + template_final +
                        " && cat " + template_path + " >> " + template_final + " && echo '\\nEND\"' >> " + template_final);
                Toast.makeText(requireActivity().getApplicationContext(), "Successfully saved to SET templates folder", Toast.LENGTH_SHORT).show();
            });

            //Launch SET
            LaunchSET.setOnClickListener(v -> {
                run_cmd("echo -ne \"\\033]0;SET\\007\" && clear;cd /root/setoolkit && ./setoolkit");
            });

            return rootView;
        }

        private void refresh(View SETFragment) {
            WebView myBrowser = SETFragment.findViewById(R.id.mybrowser);
            final String template_path = NhPaths.SD_PATH + "/" + template_tempfile;

            //Setting fields
            EditText PhishLink = SETFragment.findViewById(R.id.set_link);
            EditText PhishName = SETFragment.findViewById(R.id.set_name);
            EditText PhishPic = SETFragment.findViewById(R.id.set_pic);
            EditText PhishSubject = SETFragment.findViewById(R.id.set_subject);

            exe.RunAsRoot(new String[]{"cp " + template_src + " " + NhPaths.SD_PATH});

            String phish_link = PhishLink.getText().toString();
            String phish_name = PhishName.getText().toString();
            String phish_pic = PhishPic.getText().toString();

            switch (selected_template) {
                case "Messenger":
                    PhishSubject.setText(PhishName.getText() + " sent you a message on Messenger.");
                    break;
                case "Facebook":
                    PhishSubject.setText(PhishName.getText() + " sent you a message on Facebook.");
                    break;
                case "Twitter":
                    PhishSubject.setText(PhishName.getText() + " sent you a Direct Message on Twitter!");
                    break;
            }

            if (!phish_link.isEmpty()) {
                if (phish_link.contains("&")) phish_link = exe.RunAsRootOutput("sed 's/\\&/\\\\\\&/g' <<< \"" + phish_link + "\"");
                phish_link = exe.RunAsRootOutput("sed 's|\\/|\\\\\\/|g' <<< \"" + phish_link + "\"");
                exe.RunAsRoot(new String[]{"sed -i 's/https\\:\\/\\/www.google.com/" + phish_link + "/g' " + template_path});
            }
            if (!phish_name.isEmpty()) exe.RunAsRoot(new String[]{"sed -i 's/E Corp/" + phish_name + "/g' " + template_path});
            if (!phish_pic.isEmpty()) {
                if (phish_pic.contains("&")) phish_pic = exe.RunAsRootOutput("sed 's/\\&/\\\\\\&/g' <<< \"" + phish_pic + "\"");
                exe.RunAsRoot(new String[]{"sed -i \"s|id=\\\"set\\\".*|id=\\\"set\\\" src=\\\"" + phish_pic + "\\\" width=\\\"72\\\">|\" " + template_path});
            }
            myBrowser.clearCache(true);
            myBrowser.loadUrl(template_path);
        }
    }

    ////
    // Bridge side functions
    ////

    public void run_cmd(String cmd) {
        Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
    }
}
