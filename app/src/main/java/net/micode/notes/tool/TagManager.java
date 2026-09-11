package net.micode.notes.tool;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TagsColumns;
import net.micode.notes.data.Notes.NoteTagsColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

import java.util.ArrayList;
import java.util.List;

public class TagManager {
    private ContentResolver mResolver;
    private static final String TAG = "TagManager";

    public TagManager(ContentResolver resolver) {
        mResolver = resolver;
    }

    /**
     * 获取或创建标签 ID（如果标签不存在则插入）
     */
    private long getOrCreateTagId(String tagName) {
        if (tagName == null || tagName.trim().isEmpty()) {
            Log.e(TAG, "getOrCreateTagId: tagName为空");
            return -1;
        }
        tagName = tagName.trim();

        // 1. 查询标签是否已存在
        Cursor cursor = mResolver.query(Notes.CONTENT_TAGS_URI,
                new String[]{TagsColumns.ID},
                TagsColumns.NAME + "=?",
                new String[]{tagName},
                null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    Log.d(TAG, "标签已存在: " + tagName + " -> id=" + id);
                    return cursor.getLong(0);
                }
            } finally {
                cursor.close();
            }
        }

        // 2. 不存在则插入新标签
        ContentValues values = new ContentValues();
        values.put(TagsColumns.NAME, tagName);
        Uri uri = mResolver.insert(Notes.CONTENT_TAGS_URI, values);
        if (uri != null) {
            long id = ContentUris.parseId(uri);
            Log.d(TAG, "新建标签: " + tagName + " -> id=" + id + ", uri=" + uri);
            return ContentUris.parseId(uri);
        } else {
            Log.e(TAG, "插入标签失败: " + tagName);
        }
        return -1;
    }

    /**
     * 为笔记添加一个标签
     */
    public void addTagToNote(long noteId, String tagName) {
        long tagId = getOrCreateTagId(tagName);
        if (tagId == -1 || noteId <= 0) {
            Log.e(TAG, "addTagToNote失败: tagId=" + tagId + ", noteId=" + noteId);
            return;
        }

        // 检查是否已经存在关联，避免重复添加
        Cursor cursor = mResolver.query(Notes.CONTENT_NOTE_TAGS_URI,
                null,
                NoteTagsColumns.NOTE_ID + "=? AND " + NoteTagsColumns.TAG_ID + "=?",
                new String[]{String.valueOf(noteId), String.valueOf(tagId)},
                null);
        boolean exists = false;
        if (cursor != null) {
            exists = cursor.getCount() > 0;
            cursor.close();
        }
        if (exists) {
            Log.d(TAG, "关联已存在: noteId=" + noteId + ", tagId=" + tagId);
            return;
        }

        ContentValues values = new ContentValues();
        values.put(NoteTagsColumns.NOTE_ID, noteId);
        values.put(NoteTagsColumns.TAG_ID, tagId);
        Uri uri = mResolver.insert(Notes.CONTENT_NOTE_TAGS_URI, values);
        if (uri != null) {
            Log.d(TAG, "插入note_tags成功: noteId=" + noteId + ", tagId=" + tagId + ", uri=" + uri);
        } else {
            Log.e(TAG, "插入note_tags失败: noteId=" + noteId + ", tagId=" + tagId);
        }
    }

    /**
     * 移除笔记的某个标签
     */
    public void removeTagFromNote(long noteId, String tagName) {
        long tagId = getTagId(tagName);
        if (tagId == -1) return;

        mResolver.delete(Notes.CONTENT_NOTE_TAGS_URI,
                NoteTagsColumns.NOTE_ID + "=? AND " + NoteTagsColumns.TAG_ID + "=?",
                new String[]{String.valueOf(noteId), String.valueOf(tagId)});
    }

    /**
     * 获取标签名对应的 ID（不创建）
     */
    private long getTagId(String tagName) {
        Cursor cursor = mResolver.query(Notes.CONTENT_TAGS_URI,
                new String[]{TagsColumns.ID},
                TagsColumns.NAME + "=?",
                new String[]{tagName},
                null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            } finally {
                cursor.close();
            }
        }
        return -1;
    }

    /**
     * 获取笔记关联的所有标签名
     */
    public List<String> getTagsForNote(long noteId) {
        List<String> tags = new ArrayList<>();
        // 连表查询：note_tags JOIN tags
        String sql = "SELECT " + TABLE.TAGS + "." + TagsColumns.NAME +
                " FROM " + TABLE.NOTE_TAGS +
                " JOIN " + TABLE.TAGS +
                " ON " + TABLE.NOTE_TAGS + "." + NoteTagsColumns.TAG_ID + " = " + TABLE.TAGS + "." + TagsColumns.ID +
                " WHERE " + TABLE.NOTE_TAGS + "." + NoteTagsColumns.NOTE_ID + "=?";
        // 由于 ContentProvider 不支持直接执行原始 SQL，需要将查询包装成 URI 查询
        // 这里采用简化方式：先查 note_tags 得到 tag_id 列表，再逐个查 tags
        Cursor cursor = mResolver.query(Notes.CONTENT_NOTE_TAGS_URI,
                new String[]{NoteTagsColumns.TAG_ID},
                NoteTagsColumns.NOTE_ID + "=?",
                new String[]{String.valueOf(noteId)},
                null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long tagId = cursor.getLong(0);
                String name = getTagNameById(tagId);
                if (name != null) {
                    tags.add(name);
                }
            }
            cursor.close();
        }
        return tags;
    }

    private String getTagNameById(long tagId) {
        Cursor cursor = mResolver.query(ContentUris.withAppendedId(Notes.CONTENT_TAGS_URI, tagId),
                new String[]{TagsColumns.NAME},
                null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * 获取所有标签名（用于筛选列表）
     */
    public Cursor getAllTags() {
        return mResolver.query(Notes.CONTENT_TAGS_URI,
                new String[]{TagsColumns.ID, TagsColumns.NAME},
                TagsColumns.ID + " IN (SELECT DISTINCT " + NoteTagsColumns.TAG_ID + " FROM " + TABLE.NOTE_TAGS + ")",
                null,
                TagsColumns.NAME + " ASC");
    }

    /**
     * 根据标签 ID 查询笔记列表
     */
    public Cursor getNotesByTag(long tagId) {
        // 联表查询：notes JOIN note_tags WHERE tag_id = ?
        // 这里为了简单，直接返回 note_id 列表，上层再自行查询笔记详情
        // 实际使用时可在 NotesListActivity 中通过修改 selection 实现
        return mResolver.query(Notes.CONTENT_NOTE_TAGS_URI,
                new String[]{NoteTagsColumns.NOTE_ID},
                NoteTagsColumns.TAG_ID + "=?",
                new String[]{String.valueOf(tagId)},
                null);
    }

    /**
     * 更新笔记的标签集合（先删除旧标签，再添加新标签）
     */
    public void updateTagsForNote(long noteId, List<String> newTags) {
        // 删除该笔记所有标签关联
        mResolver.delete(Notes.CONTENT_NOTE_TAGS_URI,
                NoteTagsColumns.NOTE_ID + "=?",
                new String[]{String.valueOf(noteId)});
        // 添加新标签
        for (String tag : newTags) {
            addTagToNote(noteId, tag);
        }
    }
    public void deleteTag(long tagId) {
        // 先删除所有关联记录
        mResolver.delete(Notes.CONTENT_NOTE_TAGS_URI,
                NoteTagsColumns.TAG_ID + "=?",
                new String[]{String.valueOf(tagId)});
        // 再删除标签本身
        mResolver.delete(ContentUris.withAppendedId(Notes.CONTENT_TAGS_URI, tagId),
                null, null);
    }

    /**
     * 清理所有未被任何笔记使用的标签（孤立标签）
     */
    public void cleanupOrphanedTags() {
        // 查询所有没有任何关联的标签 ID
        Cursor cursor = mResolver.query(Notes.CONTENT_TAGS_URI,
                new String[]{TagsColumns.ID},
                TagsColumns.ID + " NOT IN (SELECT DISTINCT " + NoteTagsColumns.TAG_ID + " FROM " + TABLE.NOTE_TAGS + ")",
                null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long tagId = cursor.getLong(0);
                deleteTag(tagId);
            }
            cursor.close();
        }
    }
}