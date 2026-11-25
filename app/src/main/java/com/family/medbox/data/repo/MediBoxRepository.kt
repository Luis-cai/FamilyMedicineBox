package com.family.medbox.data.repo

import com.family.medbox.data.db.MediBoxDbHelper
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 药箱仓储层：内存中维护一份可被 Compose 观察的状态，写入后自动刷新。
 */
class MediBoxRepository(private val helper: MediBoxDbHelper) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _medicines = MutableStateFlow<List<Medicine>>(emptyList())
    val medicines: StateFlow<List<Medicine>> = _medicines.asStateFlow()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    init {
        scope.launch { reload() }
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        _medicines.value = helper.loadMedicines()
        _members.value = helper.loadMembers()
    }

    fun refresh() {
        scope.launch { reload() }
    }

    suspend fun addMedicine(medicine: Medicine): Long = withContext(Dispatchers.IO) {
        val id = helper.insertMedicine(medicine)
        reload()
        id
    }

    suspend fun updateMedicine(medicine: Medicine) = withContext(Dispatchers.IO) {
        helper.updateMedicine(medicine)
        reload()
    }

    /** 批量写入（如家庭常备药模板），只刷新一次状态 */
    suspend fun addMedicines(items: List<Medicine>): Int = withContext(Dispatchers.IO) {
        items.forEach { helper.insertMedicine(it) }
        reload()
        items.size
    }

    suspend fun deleteMedicine(id: Long) = withContext(Dispatchers.IO) {
        helper.deleteMedicine(id)
        reload()
    }

    suspend fun addMember(member: Member): Long = withContext(Dispatchers.IO) {
        val id = helper.insertMember(member)
        reload()
        id
    }

    /** 切换「需要重点关注」：影响提醒频率与家庭页的今日打卡展示。 */
    suspend fun setMemberCare(member: Member, needsCare: Boolean) = withContext(Dispatchers.IO) {
        helper.updateMember(member.copy(needsCare = needsCare))
        reload()
    }

    suspend fun deleteMember(id: Long) = withContext(Dispatchers.IO) {
        helper.deleteMember(id)
        reload()
    }

    suspend fun snapshot(): Pair<List<Medicine>, List<Member>> = withContext(Dispatchers.IO) {
        helper.loadMedicines() to helper.loadMembers()
    }
}
