/*
 * 版权所有 (c) 2010-2011，小米开源社区 (www.micode.net)
 *
 * 根据 Apache 许可证 2.0 版（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言 governing 权限和限制。
 */

package net.micode.notes.data;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.data.Notes.TagsColumns;
import net.micode.notes.data.Notes.NoteTagsColumns;
/**
 * 笔记应用的内容提供器，负责对外共享笔记数据
 * 支持对 note 和 data 两张表的增删改查，以及搜索建议功能
 */
public class NotesProvider extends ContentProvider {
    private static final UriMatcher mMatcher; // URI 匹配器，用于根据 URI 判断操作类型

    private NotesDatabaseHelper mHelper; // 数据库帮助类实例

    private static final String TAG = "NotesProvider"; // 日志标签

    // URI 匹配码定义
    private static final int URI_NOTE            = 1; // 操作 note 表（多条记录）
    private static final int URI_NOTE_ITEM       = 2; // 操作 note 表中的某一条记录
    private static final int URI_DATA            = 3; // 操作 data 表（多条记录）
    private static final int URI_DATA_ITEM       = 4; // 操作 data 表中的某一条记录

    private static final int URI_SEARCH          = 5; // 搜索笔记
    private static final int URI_SEARCH_SUGGEST  = 6; // 搜索建议（供系统搜索框使用）
    private static final int URI_TAGS           = 7;  // 操作 tags 表（多条）
    private static final int URI_TAG_ITEM       = 8;  // 操作 tags 表中的某一条
    private static final int URI_NOTE_TAGS      = 9;  // 操作 note_tags 表
    // 静态初始化块，注册 URI 及其对应的匹配码
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, "tags", URI_TAGS);
        mMatcher.addURI(Notes.AUTHORITY, "tags/#", URI_TAG_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "note_tags", URI_NOTE_TAGS);
    }

    /**
     * x'0A' 在 sqlite 中代表换行符 '\n'。
     * 对于搜索结果中的标题和内容，我们会去除换行符和空白字符，以显示更多信息。
     * 该投影定义了搜索建议返回的列结构。
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
            + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
            + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
            + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
            + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    // 搜索笔记的 SQL 查询语句：基于 snippet 字段进行模糊匹配，排除垃圾箱中的笔记
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
            + " FROM " + TABLE.NOTE
            + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
            + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
            + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    /**
     * 当 ContentProvider 被创建时调用，初始化数据库帮助类
     * @return 始终返回 true，表示创建成功
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据，根据 URI 的不同类型，查询不同的表或执行搜索
     * @param uri 查询的 URI
     * @param projection 需要返回的列
     * @param selection 查询条件（WHERE 子句）
     * @param selectionArgs 查询条件的参数
     * @param sortOrder 排序方式
     * @return 查询结果的游标
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 手动拼接 SQL 用于调试
                String sql = "SELECT " + (projection != null ? TextUtils.join(", ", projection) : "*")
                        + " FROM " + TABLE.NOTE
                        + (selection != null ? " WHERE " + selection : "")
                        + (sortOrder != null ? " ORDER BY " + sortOrder : "");
                Log.d(TAG, "执行查询 SQL: " + sql);
                Log.d(TAG, "参数: " + java.util.Arrays.toString(selectionArgs));

                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_NOTE_ITEM:
                // 查询 note 表中的单条记录，URI 中带 ID
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                // 查询 data 表（多条记录）
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_DATA_ITEM:
                // 查询 data 表中的单条记录
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索功能：不允许指定 sortOrder、projection 等额外参数
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "此查询不允许指定 sortOrder、selection、selectionArgs 或 projection");
                }

                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 搜索建议 URI：从路径中提取搜索关键字
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 普通搜索 URI：从查询参数中提取 pattern
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 构造模糊查询字符串，执行原始 SQL 查询
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "发生异常: " + ex.toString());
                }
                break;
            case URI_TAGS:
                c = db.query(TABLE.TAGS, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_TAG_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.TAGS, projection, TagsColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_NOTE_TAGS:
                c = db.query(TABLE.NOTE_TAGS, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("未知 URI " + uri);
        }
        if (c != null) {
            // 设置游标的通知 URI，当数据变化时能通知到观察者
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据，支持插入 note 或 data 表
     * @param uri 插入的目标 URI
     * @param values 要插入的键值对
     * @return 新插入记录的 URI（包含自动生成的 ID）
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入 note 表
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                // 插入 data 表，需要检查是否包含 NOTE_ID 字段
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "数据格式错误，缺少 note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            case URI_TAGS:
                insertedId = db.insert(TABLE.TAGS, null, values);
                break;
            case URI_NOTE_TAGS:
                insertedId = db.insert(TABLE.NOTE_TAGS, null, values);
                break;
            default:
                throw new IllegalArgumentException("未知 URI " + uri);
        }
        // 如果成功插入 note，通知 note URI 发生变化
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // 如果成功插入 data，通知 data URI 发生变化
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据，支持删除 note 或 data 表中的记录
     * @param uri 要删除的数据的 URI
     * @param selection 删除条件
     * @param selectionArgs 删除条件的参数
     * @return 删除的记录数
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false; // 标记是否删除了 data 表中的数据
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除 note 表中的多条记录，且保证 ID > 0（系统文件夹不允许删除）
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                /**
                 * ID 小于等于 0 的是系统文件夹，不允许移入垃圾箱
                 */
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            case URI_TAGS:
                count = db.delete(TABLE.TAGS, selection, selectionArgs);
                break;
            case URI_TAG_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.TAGS,
                        TagsColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_NOTE_TAGS:
                count = db.delete(TABLE.NOTE_TAGS, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("未知 URI " + uri);
        }
        if (count > 0) {
            // 如果删除了 data 数据，需要通知 note URI 也发生变化（因为 note 的关联数据变了）
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);

        }
        return count;
    }

    /**
     * 更新数据，支持更新 note 或 data 表
     * @param uri 要更新的数据的 URI
     * @param values 要更新的键值对
     * @param selection 更新条件
     * @param selectionArgs 更新条件的参数
     * @return 更新的记录数
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false; // 标记是否更新了 data 表
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新 note 表多条记录前，先增加笔记版本号（用于同步等场景）
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            case URI_TAGS:
                count = db.update(TABLE.TAGS, values, selection, selectionArgs);
                break;
            case URI_TAG_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.TAGS, values, TagsColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("未知 URI " + uri);
        }

        if (count > 0) {
            // 如果更新了 data 表，需要通知 note URI 发生变化
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 辅助方法：将传入的 selection 条件包装成 " AND (selection)" 的形式
     * @param selection 原始条件
     * @return 包装后的条件字符串，如果 selection 为空则返回空字符串
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 增加笔记的版本号，用于同步或冲突检测
     * @param id 笔记的 ID，若 >0 则只更新该笔记；若 = -1 则更新符合 selection 的所有笔记
     * @param selection 额外的 WHERE 条件
     * @param selectionArgs WHERE 条件的参数
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 将 selectionArgs 中的参数依次替换 ? 占位符（简单替换，注意 SQL 注入风险，但此处 selection 由内部调用保证安全）
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * 返回指定 URI 的 MIME 类型
     * 本实现未使用，直接返回 null
     */
    @Override
    public String getType(Uri uri) {
        // TODO: 根据需要实现
        return null;
    }
}