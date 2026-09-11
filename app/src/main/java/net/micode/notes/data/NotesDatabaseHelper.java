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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;


/**
 * 便签数据库帮助类
 * 负责创建、升级和管理便签应用的SQLite数据库
 * 采用单例模式确保全局只有一个数据库连接实例
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库文件名
    private static final String DB_NAME = "note.db";

    // 数据库版本号
    private static final int DB_VERSION = 4;

    /**
     * 数据表名称定义接口
     */
// NotesDatabaseHelper.java
    public interface TABLE {
        public static final String NOTE = "note";
        public static final String DATA = "data";
        public static final String TAGS = "tags"; // 新增
        public static final String NOTE_TAGS = "note_tags"; // 新增
    }

    private static final String TAG = "NotesDatabaseHelper";

    // 单例实例
    private static NotesDatabaseHelper mInstance;

    /**
     * 创建便签表(note)的SQL语句
     * 字段说明：
     * - ID: 主键，唯一标识
     * - PARENT_ID: 父文件夹ID，0表示根目录
     * - ALERTED_DATE: 提醒时间
     * - BG_COLOR_ID: 背景颜色ID
     * - CREATED_DATE: 创建时间（毫秒时间戳）
     * - HAS_ATTACHMENT: 是否有附件
     * - MODIFIED_DATE: 修改时间
     * - NOTES_COUNT: 文件夹内便签数量（仅文件夹使用）
     * - SNIPPET: 便签摘要/预览内容
     * - TYPE: 类型（普通便签/文件夹/系统文件夹）
     * - WIDGET_ID: 桌面小部件ID
     * - WIDGET_TYPE: 桌面小部件类型
     * - SYNC_ID: 同步ID，用于数据同步
     * - LOCAL_MODIFIED: 本地是否已修改（未同步）
     * - ORIGIN_PARENT_ID: 原始父文件夹ID（用于同步冲突解决）
     * - GTASK_ID: Google Tasks 任务ID
     * - VERSION: 版本号（用于冲突检测）
     */
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +
        ")";

    /**
     * 创建数据表(data)的SQL语句
     * 用于存储便签的具体内容，支持多种MIME类型
     * 字段说明：
     * - ID: 主键
     * - MIME_TYPE: 数据类型（文本/图片/录音等）
     * - NOTE_ID: 所属便签ID
     * - CREATED_DATE: 创建时间
     * - MODIFIED_DATE: 修改时间
     * - CONTENT: 主要内容
     * - DATA1~DATA5: 扩展数据字段（存储附件路径、额外信息等）
     */
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA1 + " INTEGER," +
            DataColumns.DATA2 + " INTEGER," +
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
        ")";

    /**
     * 为data表的note_id字段创建索引，提高查询性能
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ======================== 触发器（Triggers）定义 ========================
    // 触发器用于自动维护数据一致性，避免在应用层编写重复代码

    /**
     * 移动便签时，增加新文件夹的便签数量
     * 触发时机：更新note表的PARENT_ID字段后
     */
    /**
     * Increase folder's note count when move note to the folder
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 移动便签时，减少原文件夹的便签数量
     * 触发时机：更新note表的PARENT_ID字段后
     */
    /**
     * Decrease folder's note count when move note from folder
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";

    /**
     * 插入新便签时，增加父文件夹的便签数量
     * 触发时机：插入新记录到note表后
     */
    /**
     * Increase folder's note count when insert new note to the folder
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 删除便签时，减少父文件夹的便签数量
     * 触发时机：从note表删除记录后
     */
    /**
     * Decrease folder's note count when delete note from the folder
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";

    /**
     * 插入便签内容时，自动更新note表的摘要字段(SNIPPET)
     * 触发时机：向data表插入类型为NOTE的数据后
     */
    /**
     * Update note's content when insert data with type {@link DataConstants#NOTE}
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 更新便签内容时，同步更新note表的摘要字段
     * 触发时机：更新data表中类型为NOTE的记录后
     */
    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has changed
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 删除便签内容时，清空note表的摘要字段
     * 触发时机：从data表删除类型为NOTE的记录后
     */
    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has deleted
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 删除便签时，同时删除该便签下的所有数据内容
     * 触发时机：从note表删除记录后
     */
    /**
     * Delete datas belong to note which has been deleted
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 删除文件夹时，同时删除文件夹内的所有便签（级联删除）
     * 触发时机：从note表删除记录后
     */
    /**
     * Delete notes belong to folder which has been deleted
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 移动文件夹到回收站时，将文件夹内的所有便签也移动到回收站
     * 触发时机：更新note表的PARENT_ID字段为回收站ID后
     */
    /**
     * Move notes belong to folder which has been moved to trash folder
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 构造函数
     * @param context 上下文
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建note表
     * @param db 数据库实例
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    /**
     * 重建note表相关的触发器
     * 先删除已存在的触发器，再重新创建
     * @param db 数据库实例
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * 创建系统文件夹（通话记录文件夹、根文件夹、临时文件夹、回收站）
     * @param db 数据库实例
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        // 通话记录文件夹
        /**
         * call record foler for call notes
         */
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 根文件夹（默认文件夹）
        /**
         * root folder which is default folder
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 临时文件夹（用于移动便签时的临时存放）
        /**
         * temporary folder which is used for moving note
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 回收站文件夹
        /**
         * create trash folder
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建data表
     * @param db 数据库实例
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);            // 创建表
        reCreateDataTableTriggers(db);                // 创建触发器
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);    // 创建索引
        Log.d(TAG, "data table has been created");
    }

    /**
     * 重建data表相关的触发器
     * @param db 数据库实例
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        // 删除旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        // 创建新触发器
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 获取单例实例（线程安全）
     * @param context 上下文
     * @return NotesDatabaseHelper实例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }
    /**新增功能——标签分类**/
    private void createTagsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE.TAGS + " (" +
                Notes.TagsColumns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                Notes.TagsColumns.NAME + " TEXT UNIQUE NOT NULL)");
    }

    private void createNoteTagsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE.NOTE_TAGS + " (" +
                Notes.NoteTagsColumns.NOTE_ID + " INTEGER NOT NULL, " +
                Notes.NoteTagsColumns.TAG_ID + " INTEGER NOT NULL, " +
                "PRIMARY KEY(" + Notes.NoteTagsColumns.NOTE_ID + ", " + Notes.NoteTagsColumns.TAG_ID + "), " +
                "FOREIGN KEY(" + Notes.NoteTagsColumns.NOTE_ID + ") REFERENCES " + TABLE.NOTE + "(" + Notes.NoteColumns.ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY(" + Notes.NoteTagsColumns.TAG_ID + ") REFERENCES " + TABLE.TAGS + "(" + Notes.TagsColumns.ID + ") ON DELETE CASCADE)");
    }
    /**
     * 首次创建数据库时调用
     * @param db 数据库实例
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
        createTagsTable(db);
        createNoteTagsTable(db);
    }

    /**
     * 数据库升级时调用
     * 处理从旧版本到新版本的迁移逻辑
     * @param db 数据库实例
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        // 从版本1升级到版本2
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true; // this upgrade including the upgrade from v2 to v3  // 标记已包含v2->v3的升级
            oldVersion++;
        }

        // 从版本2升级到版本3
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        // 从版本3升级到版本4
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 如果需要，重建触发器
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 升级完成后检查版本号
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * 升级到版本2
     * 完全重建数据库（简单粗暴的升级方式）
     * @param db 数据库实例
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 升级到版本3
     * - 删除未使用的触发器
     * - 添加gtask_id字段（用于Google Tasks同步）
     * - 添加回收站文件夹
     * @param db 数据库实例
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除未使用的触发器
        // drop unused triggers
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // 添加gtask_id列
        // add a column for gtask id
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // 添加回收站系统文件夹
        // add a trash system folder
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级到版本4
     * 添加version字段，用于同步时的冲突检测
     * @param db 数据库实例
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}
