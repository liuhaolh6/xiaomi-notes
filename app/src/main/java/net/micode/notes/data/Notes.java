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

import android.net.Uri;
public class Notes {
    public static final String AUTHORITY = "micode_notes";
    public static final String TAG = "Notes";/** 日志标签 */
    public static final int TYPE_NOTE     = 0;/** 笔记类型：普通笔记 */
    public static final int TYPE_FOLDER   = 1;/** 笔记类型：文件夹 */
    public static final int TYPE_SYSTEM   = 2;/** 笔记类型：系统条目（如通话记录） */
    public static final Uri CONTENT_TAGS_URI = Uri.parse("content://" + AUTHORITY + "/tags");
    public static final Uri CONTENT_NOTE_TAGS_URI = Uri.parse("content://" + AUTHORITY + "/note_tags");
    public static final String CONTENT_TYPE_TAGS = "vnd.android.cursor.dir/vnd.micode.tags";
    public static final String CONTENT_ITEM_TYPE_TAGS = "vnd.android.cursor.item/vnd.micode.tag";
    public static final String CONTENT_TYPE_NOTE_TAGS = "vnd.android.cursor.dir/vnd.micode.note_tags";
    /**
     * Following IDs are system folders' identifiers
     * {@link Notes#ID_ROOT_FOLDER } is default folder
     * {@link Notes#ID_TEMPARAY_FOLDER } is for notes belonging no folder
     * {@link Notes#ID_CALL_RECORD_FOLDER} is to store call records
     */
    public static final int ID_ROOT_FOLDER = 0;// 根文件夹ID
    public static final int ID_TEMPARAY_FOLDER = -1;// 临时文件夹ID（无归属笔记
    public static final int ID_CALL_RECORD_FOLDER = -2;// 通话记录文件夹ID
    public static final int ID_TRASH_FOLER = -3;// 回收站文件夹ID

    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    /** Intent extra：提醒日期 */
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    /** Intent extra：背景颜色ID */
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    /** Intent extra：小部件ID */
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    /** Intent extra：小部件类型 */
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    /** Intent extra：文件夹ID */
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";
    /** Intent extra：通话日期 */

    public static final int TYPE_WIDGET_INVALIDE      = -1;
    /** 无效的小部件类型 */
    public static final int TYPE_WIDGET_2X            = 0;
    /** 2x2 小部件 */
    public static final int TYPE_WIDGET_4X            = 1;
    /** 4x4 小部件 */

    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        /** 普通笔记的MIME类型 */
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }
    /** 通话记录笔记的MIME类型 */

    /**
     * Uri to query all notes and folders用于查询所有笔记和文件夹的URI
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * Uri to query data用于查询数据（笔记内容）的URI
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    public interface NoteColumns {
        /**
         * The unique ID for a row 行的唯一ID
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String ID = "_id";

        /**
         * The parent's id for note or folder 笔记或文件夹的父ID
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * Created data for note or folder 创建日期
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date 最后修改日期
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String MODIFIED_DATE = "modified_date";


        /**
         * Alert date 提醒日期
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * Folder's name or text content of note 文件夹名称或笔记的文本片段（摘要）
         * <P> Type: TEXT </P> 类型：TEXT
         */
        public static final String SNIPPET = "snippet";

        /**
         * Note's widget id 笔记小部件的ID
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * Note's widget type 笔记小部件的类型
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * Note's background color's id 笔记背景颜色的ID
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * For text note, it doesn't has attachment, for multi-media
         * note, it has at least one attachment
         * <P> Type: INTEGER </P>
         * 是否包含附件（文本笔记无附件，多媒体笔记至少有一个附件），类型：INTEGER
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * Folder's count of notes 文件夹内的笔记数量
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * The file type: folder or note 文件类型：文件夹或笔记
         * <P> Type: INTEGER </P> 类型：INTEGER
         */
        public static final String TYPE = "type";

        /**
         * The last sync id 最后同步ID
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * Sign to indicate local modified or not 标识本地是否已修改
         * <P> Type: INTEGER </P> 类型：INTEGER
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * Original parent id before moving into temporary folder 移入临时文件夹前的原始父ID
         * <P> Type : INTEGER </P> 类型：INTEGER
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * The gtask id （Google Tasks ID）
         * <P> Type : TEXT </P> 类型：TEXT
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * The version code 版本号
         * <P> Type : INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String VERSION = "version";
        public static final String NOTE_ID = "note_id";
        public static final String TAG_ID = "tag_id";
    }

    public interface DataColumns {
        /**
         * The unique ID for a row 行的唯一ID
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String ID = "_id";

        /**
         * The MIME type of the item represented by this row. 该行数据项的MIME类型
         * <P> Type: Text </P> 类型：TEXT
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * The reference id to note that this data belongs to 所属笔记的引用ID
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String NOTE_ID = "note_id";

        /**
         * Created data for note or folder 创建日期
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date 最后修改日期
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * Data's content 数据内容
         * <P> Type: TEXT </P> 类型：TEXT
         */
        public static final String CONTENT = "content";


        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         * 通用数据列，含义由 {@link #MIME_TYPE} 决定，用于整数类型数据，类型：INTEGER
         */
        public static final String DATA1 = "data1";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         * 通用数据列，含义由 {@link #MIME_TYPE} 决定，用于整数类型数据，类型：INTEGER
         */
        public static final String DATA2 = "data2";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         * 通用数据列，含义由 {@link #MIME_TYPE} 决定，用于文本类型数据，类型：TEXT
         */
        public static final String DATA3 = "data3";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         * 通用数据列，含义由 {@link #MIME_TYPE} 决定，用于文本类型数据，类型：TEXT
         */
        public static final String DATA4 = "data4";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         * 通用数据列，含义由 {@link #MIME_TYPE} 决定，用于文本类型数据，类型：TEXT
         */
        public static final String DATA5 = "data5";
    }

    public static final class TextNote implements DataColumns {
        /**
         * Mode to indicate the text in check list mode or not 模式：指示文本是否处于清单列表模式
         * <P> Type: Integer 1:check list mode 0: normal mode </P> 类型：Integer，1表示清单模式，0表示普通模式
         */
        public static final String MODE = DATA1;

        public static final int MODE_CHECK_LIST = 1;
        /** 清单模式常量值 */

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        /** 文本笔记的目录MIME类型 */

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";
        /** 文本笔记的单条目MIME类型 */

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }/** 文本笔记的专用URI */

    public static final class CallNote implements DataColumns {
        /**
         * Call date for this record 通话记录的日期
         * <P> Type: INTEGER (long) </P> 类型：INTEGER (long)
         */
        public static final String CALL_DATE = DATA1;

        /**
         * Phone number for this record 通话记录的电话号码
         * <P> Type: TEXT </P> 类型：TEXT
         */
        public static final String PHONE_NUMBER = DATA3;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        /** 通话记录笔记的目录MIME类型 */

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        /** 通话记录笔记的单条目MIME类型 */

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
        /** 通话记录笔记的专用URI */
    }
    /**新增功能——便签分类**/
    public static final class TagsColumns {
        public static final String ID = "_id";
        public static final String NAME = "name";
    }

    public static final class NoteTagsColumns {
        public static final String NOTE_ID = "note_id";
        public static final String TAG_ID = "tag_id";
    }

    public static final String TABLE_TAGS = "tags";
    public static final String TABLE_NOTE_TAGS = "note_tags";


}
