/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人工具类
 * 用于根据电话号码查询联系人姓名，并提供缓存功能
 */
public class Contact {
    // 缓存：电话号码 -> 联系人姓名
    private static HashMap<String, String> sContactCache;
    // 日志标签
    private static final String TAG = "Contact";

    /**
     * 查询联系人的SQL选择条件模板
     * 使用 PHONE_NUMBERS_EQUAL 函数进行电话号码匹配（处理格式差异）
     * 限定数据类型为电话号码
     * 通过子查询在 phone_lookup 表中查找匹配的 raw_contact_id
     * 模板中的 '+' 会被实际的 min_match 值替换
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名
     * @param context 上下文对象，用于访问ContentResolver
     * @param phoneNumber 要查询的电话号码
     * @return 联系人姓名，如果未找到则返回null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 懒加载：首次调用时初始化缓存
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 先查缓存，命中则直接返回
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 将电话号码转换为用于 caller ID 查询的最小匹配格式（如去掉国家代码、区号等）
        // 并替换SQL模板中的 '+' 占位符
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 查询通讯录数据库
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,                           // 查询数据表
                new String [] { Phone.DISPLAY_NAME },       // 只获取联系人姓名
                selection,                                  // 查询条件
                new String[] { phoneNumber },               // 查询参数（原始号码用于PHONE_NUMBERS_EQUAL）
                null);                                      // 不需要排序

        // 处理查询结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 获取第一行的联系人姓名
                String name = cursor.getString(0);
                // 存入缓存
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 异常处理：索引越界（理论上不会发生）
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 确保关闭游标，释放资源
                cursor.close();
            }
        } else {
            // 没有找到匹配的联系人
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
