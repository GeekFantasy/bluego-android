package com.geekfantasy.bluego;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

public class ModeSettingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_setting);

        TabLayout tabLayout = findViewById(R.id.tl_modes);
        ViewPager viewPager = findViewById(R.id.vp_fragment_container);
        SectionPagerAdapter pagerAdapter = new SectionPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));
    }

    public static class DummyFragment extends Fragment{
        private static final String ARG_SECTION_NUMBER = "section_number";

        public static DummyFragment newInstance(int sectionNumber){
            DummyFragment fragment = new DummyFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.checked_item, container, false);
            TextView textView = rootView.findViewById(R.id.editText);
            textView.setText("Fragment page " + getArguments().getInt(ARG_SECTION_NUMBER, 0));
            return rootView;
        }
    }

    public class SectionPagerAdapter extends FragmentPagerAdapter{

        public SectionPagerAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            int preference_xml;

            switch (position){
                case 0:
                    preference_xml = R.xml.air_mouse_settings;
                    break;
                case 1:
                    preference_xml = R.xml.gestures_settings;
                    break;
                case 2:
                    preference_xml = R.xml.buttons_settings;
                    break;
                case 3:
                    preference_xml = R.xml.custom_mode_1_settings;
                    break;
                default:
                    preference_xml = R.xml.custom_mode_2_settings;
                    break;
            }

            return new ModeSettingsFragment(preference_xml);
            //return DummyFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            return 5;
        }
    }
}