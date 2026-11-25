package com.family.medbox.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.util.DateUtils

/**
 * 端侧家庭药箱数据库。所有数据仅保存在本机，不上传任何服务器。
 */
class MediBoxDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_MEMBER (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "relation TEXT NOT NULL DEFAULT ''," +
                "birthday TEXT," +
                "color_index INTEGER NOT NULL DEFAULT 0," +
                "is_self INTEGER NOT NULL DEFAULT 0," +
                "needs_care INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE $TABLE_MEDICINE (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "ingredient TEXT NOT NULL DEFAULT ''," +
                "category TEXT NOT NULL DEFAULT ''," +
                "spec TEXT NOT NULL DEFAULT ''," +
                "form TEXT NOT NULL DEFAULT ''," +
                "quantity INTEGER NOT NULL DEFAULT 0," +
                "unit TEXT NOT NULL DEFAULT '份'," +
                "expiry_date TEXT," +
                "member_id INTEGER," +
                "location TEXT NOT NULL DEFAULT ''," +
                "note TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL DEFAULT 0)"
        )
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 只做增量迁移，避免升级时把用户已有的药品和成员清空。
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE_MEMBER ADD COLUMN needs_care INTEGER NOT NULL DEFAULT 0"
            )
        }
        if (oldVersion < 3) {
            // 老版本沿用「宝贝」，新版本改为「爷爷」：原地改名保留成员 id，
            // 同时把孩子专用的药品归到「全家」，避免挂在祖父名下。
            val babyId = db.rawQuery(
                "SELECT id FROM $TABLE_MEMBER WHERE name = '宝贝' LIMIT 1", null
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            val hasGrandpa = db.rawQuery(
                "SELECT 1 FROM $TABLE_MEMBER WHERE name = '爷爷' LIMIT 1", null
            ).use { it.moveToFirst() }
            if (babyId != null && !hasGrandpa) {
                db.execSQL(
                    "UPDATE $TABLE_MEMBER SET name = '爷爷', relation = '祖父', " +
                        "color_index = 4, needs_care = 1 WHERE id = $babyId"
                )
                db.execSQL("UPDATE $TABLE_MEDICINE SET member_id = NULL WHERE member_id = $babyId")
            }
        }
    }

    // ---------------- 成员 ----------------

    fun loadMembers(): List<Member> {
        val result = mutableListOf<Member>()
        readableDatabase.query(TABLE_MEMBER, null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) {
                result += Member(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    relation = c.getString(c.getColumnIndexOrThrow("relation")),
                    birthday = c.getStringOrNull("birthday"),
                    colorIndex = c.getInt(c.getColumnIndexOrThrow("color_index")),
                    isSelf = c.getInt(c.getColumnIndexOrThrow("is_self")) == 1,
                    needsCare = c.getInt(c.getColumnIndexOrThrow("needs_care")) == 1
                )
            }
        }
        return result.sortedWith(compareBy({ memberRank(it) }, { it.id }))
    }

    /** 家庭页固定顺序：我 → 爸爸 → 妈妈 → 爷爷 → 奶奶 → 其他成员。 */
    private fun memberRank(member: Member): Int {
        if (member.isSelf) return 0
        val label = member.relation + member.name
        return when {
            // 先判祖辈：「祖父」含「父」、「祖母」含「母」，避免被下一档误匹配
            label.contains("爷") || label.contains("外公") || label.contains("祖父") -> 3
            label.contains("奶") || label.contains("外婆") || label.contains("祖母") -> 4
            label.contains("爸") || label.contains("父") -> 1
            label.contains("妈") || label.contains("母") -> 2
            else -> 5
        }
    }

    /** 从备份恢复：清空后按原 id 写回，保证计划里的成员引用不错位。 */
    fun replaceAll(medicines: List<Medicine>, members: List<Member>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_MEDICINE, null, null)
            db.delete(TABLE_MEMBER, null, null)
            members.forEach { db.insert(TABLE_MEMBER, null, it.toValuesWithId()) }
            medicines.forEach { db.insert(TABLE_MEDICINE, null, it.toValuesWithId()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertMember(member: Member): Long =
        writableDatabase.insert(TABLE_MEMBER, null, member.toValues())

    fun updateMember(member: Member) {
        writableDatabase.update(
            TABLE_MEMBER, member.toValues(), "id = ?", arrayOf(member.id.toString())
        )
    }

    fun deleteMember(id: Long) {
        writableDatabase.beginTransaction()
        try {
            val clear = ContentValues().apply { putNull("member_id") }
            writableDatabase.update(TABLE_MEDICINE, clear, "member_id = ?", arrayOf(id.toString()))
            writableDatabase.delete(TABLE_MEMBER, "id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    // ---------------- 药品 ----------------

    fun loadMedicines(): List<Medicine> {
        val result = mutableListOf<Medicine>()
        readableDatabase.query(TABLE_MEDICINE, null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                result += Medicine(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    ingredient = c.getString(c.getColumnIndexOrThrow("ingredient")),
                    category = c.getString(c.getColumnIndexOrThrow("category")),
                    spec = c.getString(c.getColumnIndexOrThrow("spec")),
                    form = c.getString(c.getColumnIndexOrThrow("form")),
                    quantity = c.getInt(c.getColumnIndexOrThrow("quantity")),
                    unit = c.getString(c.getColumnIndexOrThrow("unit")),
                    expiryDate = c.getStringOrNull("expiry_date"),
                    memberId = c.getLongOrNull("member_id"),
                    location = c.getString(c.getColumnIndexOrThrow("location")),
                    note = c.getString(c.getColumnIndexOrThrow("note")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return result
    }

    fun insertMedicine(medicine: Medicine): Long =
        writableDatabase.insert(TABLE_MEDICINE, null, medicine.toValues())

    fun updateMedicine(medicine: Medicine) {
        writableDatabase.update(
            TABLE_MEDICINE, medicine.toValues(), "id = ?", arrayOf(medicine.id.toString())
        )
    }

    fun deleteMedicine(id: Long) {
        writableDatabase.delete(TABLE_MEDICINE, "id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Member.toValues() = ContentValues().apply {
        put("name", name)
        put("relation", relation)
        put("birthday", birthday)
        put("color_index", colorIndex)
        put("is_self", if (isSelf) 1 else 0)
        put("needs_care", if (needsCare) 1 else 0)
    }

    private fun Medicine.toValues() = ContentValues().apply {
        put("name", name)
        put("ingredient", ingredient)
        put("category", category)
        put("spec", spec)
        put("form", form)
        put("quantity", quantity)
        put("unit", unit)
        put("expiry_date", expiryDate)
        if (memberId == null) putNull("member_id") else put("member_id", memberId)
        put("location", location)
        put("note", note)
        put("created_at", createdAt)
    }

    private fun Member.toValuesWithId() = toValues().apply {
        if (id > 0L) put("id", id)
    }

    private fun Medicine.toValuesWithId() = toValues().apply {
        if (id > 0L) put("id", id)
    }

    // ---------------- 首次启动的示例数据 ----------------

    private fun seed(db: SQLiteDatabase) {
        val today = DateUtils.today()
        fun day(offset: Long): String = DateUtils.toIso(today.plusDays(offset))

        val members = listOf(
            Member(name = "我", relation = "本人", colorIndex = 0, isSelf = true),
            Member(name = "妈妈", relation = "母亲", colorIndex = 1),
            Member(name = "爸爸", relation = "父亲", colorIndex = 3),
            Member(name = "爷爷", relation = "祖父", colorIndex = 4, needsCare = true),
            Member(name = "奶奶", relation = "祖母", colorIndex = 5, needsCare = true)
        )
        members.forEach { db.insert(TABLE_MEMBER, null, it.toValues()) }

        val seeds = listOf(
            Medicine(
                name = "芬必得 布洛芬缓释胶囊",
                ingredient = "布洛芬",
                category = "解热镇痛",
                spec = "0.3g×20粒",
                form = "胶囊",
                quantity = 8,
                unit = "粒",
                expiryDate = day(400),
                memberId = 2,
                location = "客厅药箱"
            ),
            Medicine(
                name = "美林 布洛芬混悬液",
                ingredient = "布洛芬",
                category = "解热镇痛",
                spec = "100ml",
                form = "混悬液",
                quantity = 1,
                unit = "瓶",
                expiryDate = day(150),
                memberId = null,
                location = "儿童药盒"
            ),
            Medicine(
                name = "泰诺林 对乙酰氨基酚缓释片",
                ingredient = "对乙酰氨基酚",
                category = "解热镇痛",
                spec = "0.65g×12片",
                form = "片剂",
                quantity = 5,
                unit = "片",
                expiryDate = day(220),
                memberId = 1,
                location = "卧室抽屉"
            ),
            Medicine(
                name = "999 感冒灵颗粒",
                ingredient = "对乙酰氨基酚",
                category = "感冒用药",
                spec = "10g×9袋",
                form = "颗粒剂",
                quantity = 3,
                unit = "袋",
                expiryDate = day(18),
                memberId = 1,
                location = "客厅药箱"
            ),
            Medicine(
                name = "蒙脱石散",
                ingredient = "蒙脱石",
                category = "肠胃用药",
                spec = "3g×10袋",
                form = "散剂",
                quantity = 6,
                unit = "袋",
                expiryDate = day(-12),
                memberId = 3,
                location = "客厅药箱"
            ),
            Medicine(
                name = "阿莫西林胶囊",
                ingredient = "阿莫西林",
                category = "抗感染",
                spec = "0.25g×24粒",
                form = "胶囊",
                quantity = 12,
                unit = "粒",
                expiryDate = day(300),
                memberId = 3,
                location = "客厅药箱"
            ),
            Medicine(
                name = "开瑞坦 氯雷他定片",
                ingredient = "氯雷他定",
                category = "抗过敏",
                spec = "10mg×6片",
                form = "片剂",
                quantity = 2,
                unit = "片",
                expiryDate = day(45),
                memberId = 2,
                location = "随身包"
            ),
            Medicine(
                name = "星鲨 维生素D滴剂",
                ingredient = "维生素D",
                category = "营养补充",
                spec = "400IU×30粒",
                form = "滴剂",
                quantity = 20,
                unit = "粒",
                expiryDate = day(500),
                memberId = null,
                location = "儿童药盒"
            ),
            Medicine(
                name = "络活喜 苯磺酸氨氯地平片",
                ingredient = "氨氯地平",
                category = "降压用药",
                spec = "5mg×7片",
                form = "片剂",
                quantity = 21,
                unit = "片",
                expiryDate = day(260),
                memberId = 4,
                location = "客厅药箱"
            ),
            Medicine(
                name = "格华止 盐酸二甲双胍缓释片",
                ingredient = "二甲双胍",
                category = "降糖用药",
                spec = "0.5g×30片",
                form = "片剂",
                quantity = 28,
                unit = "片",
                expiryDate = day(210),
                memberId = 5,
                location = "客厅药箱"
            ),
            Medicine(
                name = "拜阿司匹灵 阿司匹林肠溶片",
                ingredient = "阿司匹林",
                category = "抗血小板",
                spec = "100mg×30片",
                form = "片剂",
                quantity = 24,
                unit = "片",
                expiryDate = day(330),
                memberId = 4,
                location = "客厅药箱"
            ),
            Medicine(
                name = "立普妥 阿托伐他汀钙片",
                ingredient = "阿托伐他汀",
                category = "调脂用药",
                spec = "20mg×7片",
                form = "片剂",
                quantity = 5,
                unit = "片",
                expiryDate = day(120),
                memberId = 5,
                location = "客厅药箱"
            ),
            Medicine(
                name = "吗丁啉 多潘立酮片",
                ingredient = "多潘立酮",
                category = "肠胃用药",
                spec = "10mg×30片",
                form = "片剂",
                quantity = 18,
                unit = "片",
                expiryDate = day(240),
                memberId = 2,
                location = "卧室抽屉"
            ),
            Medicine(
                name = "口服补液盐 III",
                ingredient = "口服补液盐",
                category = "补液用药",
                spec = "5.125g×6袋",
                form = "散剂",
                quantity = 4,
                unit = "袋",
                expiryDate = day(9),
                memberId = null,
                location = "儿童药盒"
            ),
            Medicine(
                name = "小儿氨酚黄那敏颗粒",
                ingredient = "对乙酰氨基酚",
                category = "感冒用药",
                spec = "6g×10袋",
                form = "颗粒剂",
                quantity = 7,
                unit = "袋",
                expiryDate = day(60),
                memberId = null,
                location = "儿童药盒"
            ),
            Medicine(
                name = "云南白药气雾剂",
                ingredient = "云南白药",
                category = "外用止痛",
                spec = "85g",
                form = "气雾剂",
                quantity = 1,
                unit = "瓶",
                expiryDate = day(480),
                memberId = 3,
                location = "玄关柜"
            ),
            Medicine(
                name = "碘伏消毒棉签",
                ingredient = "碘伏",
                category = "外用消毒",
                spec = "50 支",
                form = "棉签",
                quantity = 42,
                unit = "支",
                expiryDate = day(700),
                memberId = null,
                location = "玄关柜"
            ),
            Medicine(
                name = "妥布霉素滴眼液",
                ingredient = "妥布霉素",
                category = "眼科用药",
                spec = "5ml",
                form = "滴眼液",
                quantity = 1,
                unit = "瓶",
                expiryDate = day(-5),
                memberId = 1,
                location = "卧室抽屉"
            ),
            Medicine(
                name = "复方甘草片",
                ingredient = "甘草",
                category = "止咳化痰",
                spec = "100片",
                form = "片剂",
                quantity = 32,
                unit = "片",
                expiryDate = day(35),
                memberId = 3,
                location = "客厅药箱"
            ),
            Medicine(
                name = "钙尔奇 碳酸钙D3片",
                ingredient = "碳酸钙",
                category = "营养补充",
                spec = "600mg×60片",
                form = "片剂",
                quantity = 51,
                unit = "片",
                expiryDate = day(420),
                memberId = 5,
                location = "卧室抽屉"
            ),
            Medicine(
                name = "连花清瘟胶囊",
                ingredient = "连翘",
                category = "中成药",
                spec = "0.35g×24粒",
                form = "胶囊",
                quantity = 2,
                unit = "粒",
                expiryDate = day(90),
                memberId = 2,
                location = "随身包"
            )
        )
        seeds.forEach { db.insert(TABLE_MEDICINE, null, it.toValues()) }
    }

    companion object {
        const val DB_NAME = "medibox.db"
        const val DB_VERSION = 3
        const val TABLE_MEMBER = "member"
        const val TABLE_MEDICINE = "medicine"
    }
}
