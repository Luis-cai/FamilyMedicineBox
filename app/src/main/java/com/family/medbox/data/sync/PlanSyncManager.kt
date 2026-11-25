package com.family.medbox.data.sync

import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.prefs.MedicationPlanStore
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 把本机服药计划同步到家庭组后端：
 * 本机新增/删除计划会即时推送，也可以手动「立即同步」做一次全量对齐。
 *
 * 冲突处理是字段级 LWW：只上报本次改过的字段，服务端按字段比较时间戳，
 * 时间新的值胜出；没改过的字段不会被本机覆盖，家人那边的修改能保留下来。
 */
class PlanSyncManager(
    private val store: MedicationPlanStore,
    private val syncStore: SyncStore,
    private val client: SyncClient = SyncClient(),
    private val stream: EventStreamClient = EventStreamClient()
) {

    val info: StateFlow<SyncInfo> = syncStore.info

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private val _events = MutableStateFlow<List<FamilyEvent>>(emptyList())

    /** 家庭动态，最新在前；未加入家庭组时为空。 */
    val events: StateFlow<List<FamilyEvent>> = _events.asStateFlow()

    @Volatile
    private var lastEventId = 0L

    private var streamJob: Job? = null

    suspend fun login(nickname: String, phone: String): Boolean {
        val serverUrl = info.value.serverUrl
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val session = client.login(serverUrl, nickname.trim(), phone.trim())
                syncStore.update {
                    it.copy(
                        token = session.token,
                        userId = session.userId,
                        nickname = session.nickname,
                        phone = phone.trim()
                    )
                }
                runCatching { client.registerDevice(serverUrl, session.token, "android-${session.userId}") }
                val family = client.myFamilies(serverUrl, session.token).firstOrNull()
                if (family != null) adoptFamily(family, session.userId)
                session.nickname
            }
        }
        return finish(result) { nicknameValue ->
            if (info.value.inFamily) "已登录 $nicknameValue，并恢复家庭组「${info.value.familyName}」"
            else "已登录 $nicknameValue，接着创建或加入家庭组"
        }
    }

    suspend fun createFamily(name: String): Boolean {
        val session = info.value
        if (!session.loggedIn) return fail("请先登录")
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val family = client.createFamily(session.serverUrl, session.token, name.trim())
                adoptFamily(family, session.userId)
                family.name
            }
        }
        return finish(result) { "已创建「$it」，把邀请码 ${info.value.inviteCode} 告诉家人" }
    }

    suspend fun joinFamily(inviteCode: String): Boolean {
        val session = info.value
        if (!session.loggedIn) return fail("请先登录")
        if (inviteCode.isBlank()) return fail("请输入邀请码")
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val family = client.joinFamily(session.serverUrl, session.token, inviteCode.trim().uppercase())
                adoptFamily(family, session.userId)
                family.name
            }
        }
        return finish(result) { "已加入「$it」" }
    }

    /** 全量同步：先推本机改动，再按上次时间增量拉取并做字段级合并。 */
    suspend fun syncNow(): Boolean {
        val session = info.value
        if (!session.loggedIn) return fail("请先登录")
        if (!session.inFamily) return fail("先创建或加入一个家庭组")
        val familyId = session.familyId ?: return fail("先创建或加入一个家庭组")
        val memberId = session.memberId ?: return fail("先创建或加入一个家庭组")

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val changes = store.changes.value
                var pushed = 0

                // 1. 还没同步过的新计划、以及有本地改动的计划，逐个推上去。
                store.plans.value.forEach { plan ->
                    val change = changes[plan.id]
                    val neverSynced = plan.id !in syncStore.pulledIds()
                    if (change == null && !neverSynced) return@forEach
                    runCatching {
                        val merged = client.upsertPlan(
                            serverUrl = session.serverUrl,
                            token = session.token,
                            plan = plan,
                            familyId = familyId,
                            memberId = memberId,
                            changedFields = change?.fields ?: MedicationPlanStore.SYNCED_FIELDS.toSet(),
                            updatedAt = change?.at ?: DateUtils.timestamp()
                        )
                        adoptMerged(merged, plan)
                        store.clearChange(plan.id)
                        syncStore.addPulledIds(listOf(plan.id))
                        pushed += 1
                    }
                }

                // 2. 本地已删除、但还没同步出去的墓碑。
                changes.filterValues { "deleted" in it.fields }.forEach { (planId, change) ->
                    runCatching {
                        client.deletePlan(session.serverUrl, session.token, planId, change.at)
                        store.clearChange(planId)
                        syncStore.removePulledIds(setOf(planId))
                    }
                }

                // 3. 拉云端改动，按字段合并进本地。
                val remote = client.pullPlans(
                    session.serverUrl,
                    session.token,
                    session.lastSyncAt.ifBlank { null }
                )
                val names = if (remote.isEmpty()) {
                    emptyMap()
                } else {
                    runCatching {
                        client.myFamilies(session.serverUrl, session.token)
                            .flatMap { it.members }
                            .associate { it.id to it.nickname }
                    }.getOrDefault(emptyMap())
                }

                val upserts = mutableListOf<MedicationPlan>()
                val deletes = mutableSetOf<String>()
                remote.forEach { item ->
                    val local = store.plans.value.firstOrNull { it.id == item.id }
                    if (item.deleted) {
                        // 墓碑优先：别人已经删掉这条计划，本地也不再保留。
                        deletes += item.id
                        store.clearChange(item.id)
                    } else {
                        val dirty = store.changeOf(item.id)?.fields.orEmpty()
                        upserts += mergeRemote(item, local, dirty, names)
                    }
                }
                if (upserts.isNotEmpty() || deletes.isNotEmpty()) {
                    store.applyRemote(upserts, deletes)
                }
                syncStore.addPulledIds(upserts.map { it.id })
                syncStore.removePulledIds(deletes)

                val newest = remote.map { it.updatedAt }
                    .filter { it.isNotBlank() }
                    .maxOrNull()
                if (newest != null) syncStore.update { it.copy(lastSyncAt = newest) }

                Triple(pushed, upserts.size, deletes.size)
            }
        }
        return finish(result) { (pushedCount, pulledCount, removed) ->
            buildString {
                append("已上传 $pushedCount 条、拉取 $pulledCount 条计划")
                if (removed > 0) append("，移除 $removed 条已删除计划")
            }
        }
    }

    /** 新增/修改计划后静默推送，只上传改动过的字段。 */
    suspend fun pushPlan(plan: MedicationPlan) {
        val session = info.value
        if (!session.enabled || !session.inFamily) return
        val familyId = session.familyId ?: return
        val memberId = session.memberId ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val change = store.changeOf(plan.id)
                val merged = client.upsertPlan(
                    serverUrl = session.serverUrl,
                    token = session.token,
                    plan = plan,
                    familyId = familyId,
                    memberId = memberId,
                    changedFields = change?.fields ?: MedicationPlanStore.SYNCED_FIELDS.toSet(),
                    updatedAt = change?.at ?: DateUtils.timestamp()
                )
                adoptMerged(merged, plan)
                store.clearChange(plan.id)
                syncStore.addPulledIds(listOf(plan.id))
            }.onFailure { reportQuietly(it) }
        }
    }

    suspend fun pushDelete(planId: String) {
        val session = info.value
        val change = store.changeOf(planId)
        syncStore.removePulledIds(setOf(planId))
        if (!session.enabled || !session.inFamily) {
            store.clearChange(planId)
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                client.deletePlan(
                    session.serverUrl,
                    session.token,
                    planId,
                    change?.at ?: DateUtils.timestamp()
                )
            }.onSuccess { store.clearChange(planId) }
                .onFailure { reportQuietly(it) }
        }
    }

    /** 打卡状态同步给家庭组，避免家人收到「还没打卡」的误报。 */
    suspend fun pushCheck(date: String, planId: String, time: String, taken: Boolean) {
        val session = info.value
        if (!session.enabled || !session.inFamily) return
        withContext(Dispatchers.IO) {
            runCatching {
                client.reportCheck(session.serverUrl, session.token, planId, date, time, taken)
            }.onFailure { reportQuietly(it) }
        }
    }

    /**
     * 把一次打卡动作作为 SceneEvent 广播给家庭组，
     * 家人设备在「家庭动态」里就能看到是谁什么时候吃了药。
     */
    suspend fun broadcastDose(plan: MedicationPlan, time: String, taken: Boolean) {
        val session = info.value
        if (!session.enabled || !session.inFamily) return
        val familyId = session.familyId ?: return
        val type = if (taken) FamilyEvent.TYPE_DOSE_TAKEN else FamilyEvent.TYPE_DOSE_UNDO
        withContext(Dispatchers.IO) {
            runCatching {
                val event = client.pushEvent(
                    serverUrl = session.serverUrl,
                    token = session.token,
                    familyId = familyId,
                    type = type,
                    planId = plan.id,
                    memberName = plan.memberName,
                    medicineName = plan.medicineName,
                    time = time
                )
                mergeEvents(listOf(event))
            }
        }
    }

    /**
     * 打开家庭动态的实时通道（SSE）。断线后自动重连，
     * 重连前会先做一次增量拉取，避免断线期间漏掉事件。
     */
    fun startEventStream(scope: CoroutineScope) {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch(Dispatchers.IO) {
            var backoff = INITIAL_BACKOFF
            while (isActive) {
                val session = info.value
                if (!session.enabled || !session.loggedIn || !session.inFamily) break
                try {
                    stream.open(
                        serverUrl = session.serverUrl,
                        token = session.token,
                        sinceId = lastEventId,
                        onOpen = { backoff = INITIAL_BACKOFF }
                    ) { event ->
                        mergeEvents(listOf(event))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 断网或服务端重启：退避后重连
                }
                if (!isActive) break
                runCatching { refreshEvents() }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
            }
        }
    }

    fun stopEventStream() {
        stream.close()
        streamJob?.cancel()
        streamJob = null
    }

    /** 增量拉取家庭动态（实时通道的兜底，进入家庭页或回到前台时调用）。 */
    suspend fun refreshEvents() {
        val session = info.value
        if (!session.enabled || !session.inFamily) return
        withContext(Dispatchers.IO) {
            runCatching {
                val remote = client.pullEvents(session.serverUrl, session.token, lastEventId)
                if (remote.isNotEmpty()) mergeEvents(remote)
            }
        }
    }

    private fun mergeEvents(incoming: List<FamilyEvent>) {
        lastEventId = maxOf(lastEventId, incoming.maxOf { it.id })
        _events.value = (incoming + _events.value)
            .distinctBy { it.id }
            .sortedByDescending { it.id }
            .take(30)
    }

    /**
     * 云端计划合并到本地：本机改过但还没推上去的字段保留本机值，
     * 其余字段以服务端为准（服务端已经按 LWW 定过输赢）。
     */
    private fun mergeRemote(
        remote: SyncClient.RemotePlan,
        local: MedicationPlan?,
        dirty: Set<String>,
        names: Map<Long, String>
    ): MedicationPlan {
        fun keep(field: String): Boolean = local != null && field in dirty
        val remoteName = names[remote.memberId].orEmpty()
        return MedicationPlan(
            id = remote.id,
            medicineName = if (keep("medicine_name")) local!!.medicineName else remote.medicineName,
            dose = if (keep("dose")) local!!.dose else remote.dose,
            memberId = local?.memberId,
            memberName = remoteName.ifBlank { local?.memberName.orEmpty() },
            times = if (keep("times")) local!!.times else remote.times.ifEmpty { listOf("08:00") },
            startDate = if (keep("start_date")) local!!.startDate else remote.startDate,
            days = if (keep("days")) local!!.days else remote.days
        )
    }

    /** 服务端返回的合并结果就是最终状态，直接覆盖本地并清掉脏标记。 */
    private fun adoptMerged(remote: SyncClient.RemotePlan, local: MedicationPlan) {
        store.applyRemote(listOf(mergeRemote(remote, local, emptySet(), emptyMap())), emptySet())
    }

    fun setEnabled(enabled: Boolean) {
        syncStore.update { it.copy(enabled = enabled) }
    }

    fun updateServerUrl(url: String) {
        syncStore.update { it.copy(serverUrl = url.trim().trimEnd('/')) }
    }

    fun clearSession() {
        stopEventStream()
        syncStore.clearSession()
        _events.value = emptyList()
        lastEventId = 0L
        _message.value = "已退出家庭同步"
        _error.value = false
    }

    private fun adoptFamily(family: SyncClient.FamilyResult, userId: Long) {
        val me = family.members.firstOrNull { it.userId == userId }
        syncStore.update {
            it.copy(
                familyId = family.id,
                familyName = family.name,
                inviteCode = family.inviteCode,
                memberId = me?.id
            )
        }
    }

    private suspend fun <T> finish(result: Result<T>, success: (T) -> String): Boolean {
        return result.fold(
            onSuccess = { value ->
                _error.value = false
                _message.value = success(value)
                true
            },
            onFailure = { throwable ->
                reportQuietly(throwable)
                false
            }
        )
    }

    private fun reportQuietly(throwable: Throwable) {
        _error.value = true
        _message.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: "连不上服务器，检查地址和网络"
    }

    private fun fail(text: String): Boolean {
        _error.value = true
        _message.value = text
        return false
    }

    private companion object {
        const val INITIAL_BACKOFF = 1_000L
        const val MAX_BACKOFF = 30_000L
    }
}
