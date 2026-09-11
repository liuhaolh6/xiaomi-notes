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

package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 数据库 data 表的操作封装类，用于在同步过程中处理笔记的附件数据（如文本内容、图片等）。
 * 支持从 JSON 构建、提交到数据库、以及从数据库游标加载。
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    private static final int INVALID_ID = -99999;   // 无效的数据 ID 标识

    // 查询 data 表时使用的投影列（只选择需要的列，提高效率）
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // 投影中各列的索引位置
    public static final int DATA_ID_COLUMN = 0;             // 数据 ID
    public static final int DATA_MIME_TYPE_COLUMN = 1;      // MIME 类型
    public static final int DATA_CONTENT_COLUMN = 2;        // 内容（如文本）
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3; // 扩展字段1（通常用于存储数字，如日期）
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4; // 扩展字段3（通常用于存储字符串）

    private ContentResolver mContentResolver;  // 内容解析器，用于访问 ContentProvider

    private boolean mIsCreate;      // 是否为新建记录（true表示尚未插入数据库）
    private long mDataId;           // 数据记录 ID（对应 data 表的 _id）
    private String mDataMimeType;   // MIME 类型，例如 Notes.DataConstants.NOTE 表示文本笔记
    private String mDataContent;    // 实际内容，如笔记的文本
    private long mDataContentData1; // 辅助数据1，通常用于存储数字（例如提醒时间）
    private String mDataContentData3; // 辅助数据3，通常用于存储额外字符串

    private ContentValues mDiffDataValues; // 记录待提交的字段变更（增量更新）

    /**
     * 构造函数：用于创建一条新的空白数据记录
     * @param context 上下文，用于获取 ContentResolver
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;           // 标记为新建状态
        mDataId = INVALID_ID;       // 初始 ID 无效
        mDataMimeType = DataConstants.NOTE;   // 默认 MIME 类型为文本笔记
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues(); // 初始化变更集合
    }

    /**
     * 构造函数：从数据库游标加载已有数据记录
     * @param context 上下文
     * @param c       指向 data 表某行的游标（已移动到有效位置）
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;          // 标记为已存在记录
        loadFromCursor(c);          // 从游标加载字段值
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从游标中加载数据到成员变量
     * @param c 游标，已指向有效行
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 通过 JSON 对象设置数据内容。
     * 会与当前值比较，如有变化则记录到 mDiffDataValues 中，用于后续提交。
     * @param js 包含数据字段的 JSON 对象
     * @throws JSONException JSON 解析异常
     */
    public void setContent(JSONObject js) throws JSONException {
        // 处理 ID 字段
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 处理 MIME 类型字段
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 处理内容字段
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 处理 DATA1 字段（长整型）
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 处理 DATA3 字段（字符串）
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将当前数据对象转换为 JSON 对象（用于同步上传）
     * @return JSON 对象，如果当前是未创建的记录则返回 null
     * @throws JSONException JSON 构建异常
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "看起来这条数据尚未在数据库中创建");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 将待提交的变更（mDiffDataValues）保存到数据库。
     * @param noteId         所属笔记的 ID（对应 note 表）
     * @param validateVersion 是否校验版本号（用于同步冲突检测）
     * @param version        如果 validateVersion 为 true，则要求 note 的版本号与此参数一致才更新
     * @throws ActionFailureException 当插入或更新失败时抛出
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            // 新建记录：如果 ID 为无效值且变更集中包含了 ID 字段，则移除 ID（让数据库自动生成）
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            // 关联笔记 ID
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            // 执行插入操作
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                // 从返回的 URI 中解析出自增的 ID
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "获取笔记 ID 出错：" + e.toString());
                throw new ActionFailureException("创建笔记失败");
            }
        } else {
            // 更新已有记录：仅当有实际变更时才执行
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 不校验版本：直接更新
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 校验版本：使用 SQL 子查询确保所属 note 的版本号匹配
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "没有更新任何行，可能用户在同步期间修改了笔记");
                }
            }
        }

        // 清空变更集，并将状态改为“已创建”
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取数据记录的 ID
     * @return 数据 ID，若尚未创建则为 INVALID_ID
     */
    public long getId() {
        return mDataId;
    }
}