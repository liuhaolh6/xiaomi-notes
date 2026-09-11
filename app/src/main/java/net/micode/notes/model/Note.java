/*
 * 版权所有 (c) 2010-2011，MiCode 开源社区 (www.micode.net)
 *
 * 根据 Apache 许可证 2.0 版（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获得许可证副本：
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 不附带任何明示或暗示的担保或条件。
 * 请参阅许可证了解具体语言管理权限和限制。
 */

package net.micode.notes.model;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 笔记模型类，封装了一条笔记的完整数据（包括笔记本身及其关联的数据条目，如文本、通话记录等）。
 * 支持本地修改标记和同步到 ContentProvider 的功能。
 */
public class Note {
    // 笔记自身字段的修改值集合（对应 NoteColumns）
    private ContentValues mNoteDiffValues;
    // 笔记关联的数据（如文本、通话记录）的封装
    private NoteData mNoteData;
    private static final String TAG = "Note";

    /**
     * 在数据库中创建一条新笔记，返回新生成的笔记 ID。
     * @param context  上下文
     * @param folderId 所属文件夹 ID
     * @return 新笔记的 ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 在数据库中创建一条新笔记
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);      // 标记为本地已修改
        values.put(NoteColumns.PARENT_ID, folderId);
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "获取笔记 ID 错误：" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("错误的笔记 ID：" + noteId);
        }
        return noteId;
    }

    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置笔记自身的字段值（例如标题、内容摘要等）
     * @param key   字段名
     * @param value 字段值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断笔记是否有本地未同步的修改（包括笔记自身字段和关联数据字段）
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将笔记的修改同步到 ContentProvider（数据库）
     * @param context 上下文
     * @param noteId  要同步的笔记 ID
     * @return 是否同步成功
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("错误的笔记 ID：" + noteId);
        }

        if (!isLocalModified()) {
            return true;   // 无修改，直接成功
        }

        /**
         * 理论上，一旦数据变化，笔记的 LOCAL_MODIFIED 和 MODIFIED_DATE 应该被更新。
         * 为了数据安全，即使更新笔记自身失败，我们也会继续尝试更新笔记数据。
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "更新笔记错误，这不应该发生");
            // 不返回，继续向下执行
        }
        mNoteDiffValues.clear();

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部类，封装与笔记关联的数据条目（例如文本数据、通话记录数据）。
     * 每个数据条目对应 ContentProvider 中的一条 DATA 记录。
     */
    private class NoteData {
        private long mTextDataId;          // 文本数据的 ID
        private ContentValues mTextDataValues;   // 待更新的文本数据字段
        private long mCallDataId;           // 通话记录数据的 ID
        private ContentValues mCallDataValues;   // 待更新的通话记录数据字段
        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        void setTextDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("文本数据 ID 应大于 0");
            }
            mTextDataId = id;
        }

        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("通话记录数据 ID 应大于 0");
            }
            mCallDataId = id;
        }

        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将当前 NoteData 中的修改推送到 ContentProvider。
         * @param context 上下文
         * @param noteId  所属笔记的 ID
         * @return 成功则返回该笔记的 URI，失败返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            if (noteId <= 0) {
                throw new IllegalArgumentException("错误的笔记 ID：" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            ContentProviderOperation.Builder builder;

            // 处理文本数据
            if (mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 新增文本数据
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "插入新文本数据失败，noteId=" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新已有的文本数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // 处理通话记录数据
            if (mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 新增通话记录数据
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "插入新通话记录数据失败，noteId=" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新已有的通话记录数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 执行批量操作
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}