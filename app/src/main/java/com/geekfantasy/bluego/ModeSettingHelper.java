package com.geekfantasy.bluego;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModeSettingHelper {

    public static byte[] getPreferenceData(Map<String, ?> map, String prefix) {
        List<byte[]> dataList = new ArrayList<>();

        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 过滤不符合前缀要求的键值对
            if (!key.startsWith(prefix)) {
                continue;
            }

            // 处理键
            String subKey = key.substring(prefix.length());
            byte[] keyBytes = (subKey + ":").getBytes(StandardCharsets.UTF_8);

            // 处理值
            byte[] valueBytes;
            if (value instanceof Boolean) {
                int intValue = ((Boolean) value) ? 1 : 0;
                valueBytes = new byte[] {(byte) (intValue >> 8), (byte) intValue, ','};
            } else if (value instanceof String) {
                int intValue = Integer.parseUnsignedInt((String) value);
                valueBytes = new byte[] {(byte) (intValue >> 8), (byte) intValue, ','};
            } else {
                continue;
            }
            // 添加键值对到数据列表中
            dataList.add(concatenateByteArrays(keyBytes, valueBytes));
        }

        // 将数据列表中的所有字节数组连接成一个大的字节数组
        return concatenateByteArrays(dataList.toArray(new byte[0][]));
    }

    private static byte[] concatenateByteArrays(byte[]... byteArrays) {
        int totalLength = 0;
        for (byte[] byteArray : byteArrays) {
            totalLength += byteArray.length;
        }

        byte[] result = new byte[totalLength];
        int currentIndex = 0;
        for (byte[] byteArray : byteArrays) {
            System.arraycopy(byteArray, 0, result, currentIndex, byteArray.length);
            currentIndex += byteArray.length;
        }

        return result;
    }
}
