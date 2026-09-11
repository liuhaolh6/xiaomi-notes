# 小米便签 — 开源项目维护与标签功能扩展

基于小米开源便签（`net.micode.notes`）的**代码阅读、注释、维护与功能扩展**项目。

> 个人独立负责 ｜ 核心工作：从 0 到 1 实现 **标签（Tag）系统**
> 技术栈：Java + Android ContentProvider + SQLite + Cursor

---

## 一、项目概览

| 项目 | 说明 |
|---|---|
| 类型 | 开源项目维护与功能扩展（个人独立完成） |
| 基础代码 | 小米便签 `net.micode.notes`（Apache 2.0） |
| 规模 | 43 个 Java 文件 |
| 本人新增 | `TagManager.java`（约 230 行）+ 2 张数据表 + 相关 UI 集成 |
| 编译配置 | `compileSdk 36` / `minSdk 24` / `targetSdk 36` / JDK 11 |
| 主要工作 | 标签系统设计、多对多建模、Cursor 管理、构建问题修复 |

---

## 二、本人贡献（可验证）

### 提交记录

```
提交者：LH-liu-hao <3605637021@qq.com>
提交数：29 次
```

### 核心工作

| 类别 | 内容 |
|---|---|
| **数据库扩展** | 新增 `tags`、`note_tags` 两张表，定义 `TagsColumns` / `NoteTagsColumns` 列常量 |
| **ContentProvider 扩展** | 新增 `CONTENT_TAGS_URI`、`CONTENT_NOTE_TAGS_URI` 两个 URI 及 MIME 类型 |
| **业务类** | 独立编写 `TagManager.java`（约 230 行），封装标签全部读写逻辑 |
| **UI 集成** | `NotesListActivity` 集成标签筛选栏（`mTagFilterLayout` / `mSelectedTagId`） |
| **构建修复** | 解决 Apache HTTP 库移除导致的编译失败、打包 META-INF 冲突 |

---

## 三、标签系统设计

### 1. 数据模型 — 多对多关系建模

一条笔记可以有多个标签，一个标签可以属于多条笔记，因此采用**多对多**建模，
引入中间表 `note_tags`：

```
┌──────────────┐         ┌───────────────────┐         ┌──────────────┐
│    notes     │         │    note_tags      │         │     tags     │
├──────────────┤         ├───────────────────┤         ├──────────────┤
│ _id      PK  │◀───────│ note_id    FK     │         │ _id      PK  │
│ ...          │  1    N │ tag_id     FK     │ N    1  │ name UNIQUE  │
└──────────────┘         │ PK(note_id,tag_id)│────────▶└──────────────┘
                         └───────────────────┘
```

**建表 SQL**（`NotesDatabaseHelper.java`）：

```sql
CREATE TABLE tags (
    _id  INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL
);

CREATE TABLE note_tags (
    note_id INTEGER NOT NULL,
    tag_id  INTEGER NOT NULL,
    PRIMARY KEY (note_id, tag_id),
    FOREIGN KEY (note_id) REFERENCES note(_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id)  REFERENCES tags(_id) ON DELETE CASCADE
);
```

**设计要点**：
1. `name TEXT UNIQUE` — 标签名唯一约束，从**数据库层面**防止重复标签，
   即使应用层校验遗漏也不会产生脏数据
2. **复合主键 `(note_id, tag_id)`** — 天然防止同一条笔记重复打同一标签
3. **外键 + `ON DELETE CASCADE`** — 删除笔记时自动清理关联记录，
   避免产生孤儿数据，比在应用层手工维护更可靠

### 2. ContentProvider 扩展

沿用小米便签原有的 ContentProvider 架构（而非绕过它直接操作数据库），
新增两个 URI：

```java
// data/Notes.java
public static final Uri CONTENT_TAGS_URI =
        Uri.parse("content://" + AUTHORITY + "/tags");
public static final Uri CONTENT_NOTE_TAGS_URI =
        Uri.parse("content://" + AUTHORITY + "/note_tags");

public static final String CONTENT_TYPE_TAGS =
        "vnd.android.cursor.dir/vnd.micode.tags";
public static final String CONTENT_ITEM_TYPE_TAGS =
        "vnd.android.cursor.item/vnd.micode.tag";

public static final class TagsColumns {
    public static final String ID = "_id";
    public static final String NAME = "name";
}

public static final class NoteTagsColumns {
    public static final String NOTE_ID = "note_id";
    public static final String TAG_ID = "tag_id";
}
```

> **为什么坚持走 ContentProvider**：这是 Android 官方推荐的跨进程数据共享方式，
> 保持一致可以让标签数据同样被 Widget、同步服务等组件访问，
> 也保持了与原有代码风格的一致。

### 3. TagManager — 业务逻辑封装

`tool/TagManager.java`（约 230 行）封装全部标签读写，主要方法：

| 方法 | 职责 | 关键点 |
|---|---|---|
| `getOrCreateTagId(String)` | 查询标签，不存在则创建 | 返回 -1 表示失败 |
| `addTagToNote(long, String)` | 为笔记打标签 | **先查重再插入** |
| `getTagsForNote(long)` | 查询笔记的所有标签名 | 二次查询组装 |
| `getAllTags()` | 获取所有**被使用过**的标签 | 子查询过滤孤立标签 |
| `getNotesByTag(long)` | 按标签筛选笔记 | 查中间表返回 note_id |
| `updateTagsForNote(long, List)` | 更新笔记标签集合 | **全删再插** |
| `deleteTag(long)` | 删除标签 | **先删关联再删主体** |
| `cleanupOrphanedTags()` | 清理孤立标签 | 子查询 + 遍历删除 |

---

## 四、关键技术点

### 1. Cursor 泄漏防护

Android 的 `Cursor` 持有底层数据库游标资源，不关闭会造成内存泄漏与
`Too many open files` 异常。本项目所有 Cursor 都使用 `try-finally` 保证关闭：

```java
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
        cursor.close();          // 无论是否抛异常都关闭
    }
}
```

### 2. 防重复打标签

`addTagToNote` 在插入前先查询关联是否已存在，
配合数据库层的复合主键形成**双重防护**：

```java
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
if (exists) return;              // 已存在则直接返回
```

### 3. 子查询过滤孤立标签

只展示"至少被一条笔记使用过"的标签，避免下拉列表里出现无意义的空标签：

```java
public Cursor getAllTags() {
    return mResolver.query(Notes.CONTENT_TAGS_URI,
            new String[]{TagsColumns.ID, TagsColumns.NAME},
            TagsColumns.ID + " IN (SELECT DISTINCT " + NoteTagsColumns.TAG_ID
                    + " FROM " + TABLE.NOTE_TAGS + ")",   // 子查询
            null,
            TagsColumns.NAME + " ASC");                    // 按名称排序
}
```

对应地，`cleanupOrphanedTags()` 用 `NOT IN` 找出无关联标签并清理：

```java
public void cleanupOrphanedTags() {
    Cursor cursor = mResolver.query(Notes.CONTENT_TAGS_URI,
            new String[]{TagsColumns.ID},
            TagsColumns.ID + " NOT IN (SELECT DISTINCT " + NoteTagsColumns.TAG_ID
                    + " FROM " + TABLE.NOTE_TAGS + ")",
            null, null);
    if (cursor != null) {
        while (cursor.moveToNext()) {
            deleteTag(cursor.getLong(0));
        }
        cursor.close();
    }
}
```

### 4. 更新策略：全删再插

`updateTagsForNote` 采用"先删光旧关联，再批量插入新关联"的策略：

```java
public void updateTagsForNote(long noteId, List<String> newTags) {
    mResolver.delete(Notes.CONTENT_NOTE_TAGS_URI,
            NoteTagsColumns.NOTE_ID + "=?",
            new String[]{String.valueOf(noteId)});
    for (String tag : newTags) {
        addTagToNote(noteId, tag);
    }
}
```

**取舍说明**：相比逐条 diff（计算出新增/删除差集再做增量更新），
全删再插的写法更简单、不易出错，且单条笔记的标签数量很少（通常 < 10），
性能差异可忽略。**这是"可读性优先于微优化"的权衡**。

### 5. 删除时的引用清理

删除标签时必须先删关联记录，否则会留下指向不存在标签的脏数据：

```java
public void deleteTag(long tagId) {
    mResolver.delete(Notes.CONTENT_NOTE_TAGS_URI,
            NoteTagsColumns.TAG_ID + "=?",
            new String[]{String.valueOf(tagId)});          // 1. 先删关联
    mResolver.delete(ContentUris.withAppendedId(Notes.CONTENT_TAGS_URI, tagId),
            null, null);                                    // 2. 再删标签
}
```

---

## 五、构建问题修复

将 2011 年的旧版小米便签移植到现代 Android 构建环境时遇到两类问题：

### 1. Apache HTTP 库被移除

Android 6.0（API 23）起系统移除了 Apache HttpClient，而小米便签早期代码依赖它。

```gradle
android {
    // 通过 useLibrary 声明可继续使用系统内置的兼容库
    useLibrary 'org.apache.http.legacy'
}
```

### 2. 打包时 META-INF 文件冲突

第三方库中包含重复的元数据文件，导致打包 APK 时报错。

```gradle
packagingOptions {
    exclude 'META-INF/DEPENDENCIES'
    exclude 'META-INF/LICENSE'
    exclude 'META-INF/NOTICE'
}
```

> 此外还补充了 `minSdk` / `compileSdk` / `JavaVersion.VERSION_11` 等现代化配置，
> 使这份旧代码能在当前 AGP 上构建通过。

---

## 六、目录结构（关键文件）

```
xiaomi-notes/
└── app/src/main/java/net/micode/notes/
    ├── data/
    │   ├── Notes.java                    ★ 新增 TagsColumns / NoteTagsColumns / URI
    │   ├── NotesDatabaseHelper.java      ★ 新增 tags / note_tags 建表语句
    │   └── NotesProvider.java            ★ ContentProvider 支持新 URI
    ├── tool/
    │   └── TagManager.java               ★ 本人独立编写（约 230 行）
    ├── ui/
    │   ├── NotesListActivity.java        ★ 集成标签筛选栏
    │   └── NotesListAdapter.java
    ├── model/
    │   ├── Note.java
    │   └── WorkingNote.java
    └── ...
```

★ 标记为本人新增或修改的文件

---

## 七、项目价值

这个项目的意义不在于"从零做一个 App"，而在于：

1. **阅读并理解了约 43 个文件的陌生代码库**，梳理出数据层（ContentProvider + SQLite）
   与 UI 层的交互方式
2. **在既有架构上做增量扩展**，而不是另起炉灶——这是真实工作中最常见的场景
3. **涉及多对多建模、外键约束、游标资源管理等数据库核心知识**
4. **修复了跨版本构建兼容问题**，理解了 Android 平台演进带来的破坏性变更

---

## 八、后续可改进方向

- [ ] 标签支持颜色标记（`tags` 表加 `color` 字段）
- [ ] 支持标签重命名，并处理重名冲突
- [ ] 用 `LoaderManager` / `LiveData` 替代手工 Cursor 管理
- [ ] 补单元测试（Robolectric 测试 ContentProvider）
- [ ] `updateTagsForNote` 改为批量操作 + 事务，减少 I/O 次数
- [ ] 迁移到 Room，减少手写 SQL

---

## License

基础代码来自小米开源社区（MiCode），遵循 Apache License 2.0。
本仓库的扩展部分同样以 Apache 2.0 发布。

