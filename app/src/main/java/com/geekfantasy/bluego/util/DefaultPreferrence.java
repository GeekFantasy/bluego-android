package com.geekfantasy.bluego.util;

public class DefaultPreferrence {
    public class StringPreferrence {
        public String key;
        public String strValue;

        public StringPreferrence(String key, String strValue) {
            this.key = key;
            this.strValue = strValue;
        }
    }

    public class BoolPreferrence {
        public String key;
        public boolean boolValue;

        public BoolPreferrence(String key, boolean boolValue) {
            this.key = key;
            this.boolValue = boolValue;
        }
    }

    public  StringPreferrence[] strPreferrences = {
            new StringPreferrence("am_imu_gyro", "201"),
            new StringPreferrence("am_mfs_middle", "202"),
            new StringPreferrence("am_mfs_up", "204"),
            new StringPreferrence("am_mfs_down", "203"),
            new StringPreferrence("ges_ges_up", "101"),
            new StringPreferrence("ges_ges_down", "102"),
            new StringPreferrence("ges_ges_left", "103"),
            new StringPreferrence("ges_ges_right", "104"),
            new StringPreferrence("ges_ges_forward", "105"),
            new StringPreferrence("ges_ges_clk", "106"),
            new StringPreferrence("ges_ges_aclk", "107"),
            new StringPreferrence("mfs_mfs_up", "101"),
            new StringPreferrence("mfs_mfs_down", "102"),
            new StringPreferrence("mfs_mfs_left", "103"),
            new StringPreferrence("mfs_mfs_right", "104"),
            new StringPreferrence("mfs_mfs_middle", "105"),
            new StringPreferrence("mfs_ges_up", "101"),
            new StringPreferrence("mfs_ges_down", "102"),
            new StringPreferrence("mfs_ges_left", "103"),
            new StringPreferrence("mfs_ges_right", "104"),
            new StringPreferrence("mfs_ges_forward", "105"),
            new StringPreferrence("mfs_ges_clk", "106"),
            new StringPreferrence("mfs_ges_aclk", "107"),
    };
    public  BoolPreferrence[] boolPreferrences = {
            new BoolPreferrence("am_imu", true),
            new BoolPreferrence("am_mfs", true),
            new BoolPreferrence("am_ges", false),
            new BoolPreferrence("ges_imu", false),
            new BoolPreferrence("ges_mfs", false),
            new BoolPreferrence("ges_ges", true),
            new BoolPreferrence("mfs_imu", false),
            new BoolPreferrence("mfs_mfs", true),
            new BoolPreferrence("mfs_ges", false),
            new BoolPreferrence("cm1_mfs", false),
            new BoolPreferrence("cm2_mfs", false),
    } ;
}
