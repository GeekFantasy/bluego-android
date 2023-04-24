package com.geekfantasy.bluego;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

public class ModeSettingsFragment extends PreferenceFragmentCompat {
    private  int preference_xml;

    public ModeSettingsFragment(int prefer_xml)
    {
        this.preference_xml =  prefer_xml;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(preference_xml, rootKey);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

//        SwitchPreference sp = findPreference("am_multi_fun_switch");
//
//        sp.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//            @Override
//            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
//                if(preference instanceof SwitchPreference){
//                    if(newValue instanceof Boolean ){
//                        PreferenceCategory pc = findPreference("am_pc_multi_fun_switch");
//                        Boolean visible = (Boolean) newValue;
//                        pc.setVisible(visible);
//                    }
//                }
//                return true;
//            }
//        });

    }
}