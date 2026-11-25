package com.family.medbox.data.sync

import com.family.medbox.data.model.MedicationPlan
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SyncClient {

    class ApiException(message: String) : Exception(message)

    data class LoginResult(val token: String, val userId: Long, val nickname: String)

    data class FamilyMember(
        val id: Long,
        val userId: Long,
        val nickname: String,
        val relation: String
    )

    data class FamilyResult(
        val id: Long,
        val name: String,
        val inviteCode: String,
        val members: List<FamilyMember>
    )

    data class RemotePlan(
        val id: String,
        val medicineName: String,
        val dose: String,
        val times: List<String>,
        val startDate: String,
        val days: Int,
        val memberId: Long,
        val deleted: Boolean,
        val fieldTimes: Map<String, String>,
        val updatedAt: String
    )

    data class RemoteNotification(
        val id: Long,
        val planId: String,
        val date: String,
        val time: String,
        val kind: String,
        val title: String,
        val body: String,
        val createdAt: String
    )

    fun login(serverUrl: String, nickname: String, phone: String): LoginResult {
        val body = JSONObject().apply {
            put("nickname", nickname)
            if (phone.isNotBlank()) put("phone", phone)
        }
        val json = JSONObject(request("POST", url(serverUrl, "/auth/login"), null, body.toString()))
        return LoginResult(
            token = json.getString("token"),
            userId = json.getLong("user_id"),
            nickname = json.optString("nickname", nickname)
        )
    }

    fun createFamily(serverUrl: String, token: String, name: String): FamilyResult {
        val body = JSONObject().put("name", name)
        return parseFamily(JSONObject(request("POST", url(serverUrl, "/families"), token, body.toString())))
    }

    fun joinFamily(serverUrl: String, token: String, inviteCode: String): FamilyResult {
        val body = JSONObject().apply {
            put("invite_code", inviteCode)
            put("relation", "家人")
        }
        return parseFamily(
            JSONObject(request("POST", url(serverUrl, "/families/join"), token, body.toString()))
        )
    }

    fun myFamilies(serverUrl: String, token: String): List<FamilyResult> {
        val array = JSONArray(request("GET", url(serverUrl, "/families/me"), token, null))
        return buildList {
            for (i in 0 until array.length()) add(parseFamily(array.getJSONObject(i)))
        }
    }

    fun registerDevice(serverUrl: String, token: String, pushToken: String) {
        val body = JSONObject().apply {
            put("platform", "android")
            put("push_token", pushToken)
        }
        request("POST", url(serverUrl, "/devices"), token, body.toString())
    }

    /**
     * 新增/更新计划。只上报本次真正改动过的字段，服务端按字段做 LWW，
     * 返回合并后的最终状态（可能包含家人更新的字段）。
     */
    fun upsertPlan(
        serverUrl: String,
        token: String,
        plan: MedicationPlan,
        familyId: Long,
        memberId: Long,
        changedFields: Set<String>,
        updatedAt: String
    ): RemotePlan {
        val body = JSONObject().apply {
            put("family_id", familyId)
            put("member_id", memberId)
            put("medicine_name", plan.medicineName)
            put("dose", plan.dose)
            put("times", JSONArray(plan.times))
            put("start_date", plan.startDate)
            put("days", plan.days)
            put("updated_at", updatedAt)
            put("changed_fields", JSONArray(changedFields.toList()))
        }
        val json = request("PUT", url(serverUrl, "/plans/${plan.id}"), token, body.toString())
        return parsePlan(JSONObject(json))
    }

    fun deletePlan(serverUrl: String, token: String, planId: String, updatedAt: String) {
        val stamp = URLEncoder.encode(updatedAt, "UTF-8")
        request("DELETE", url(serverUrl, "/plans/$planId?updated_at=$stamp"), token, null)
    }

    fun reportCheck(
        serverUrl: String,
        token: String,
        planId: String,
        date: String,
        time: String,
        taken: Boolean
    ) {
        val body = JSONObject().apply {
            put("plan_id", planId)
            put("date", date)
            put("time", time)
            put("taken", taken)
        }
        request("POST", url(serverUrl, "/doses/check"), token, body.toString())
    }

    /** 上报一条家庭动态（SceneEvent），返回服务端生成的事件。 */
    fun pushEvent(
        serverUrl: String,
        token: String,
        familyId: Long,
        type: String,
        planId: String,
        memberName: String,
        medicineName: String,
        time: String
    ): FamilyEvent {
        val body = JSONObject().apply {
            put("family_id", familyId)
            put("type", type)
            put("plan_id", planId)
            put("member_name", memberName)
            put("medicine_name", medicineName)
            put("time", time)
        }
        return parseEvent(JSONObject(request("POST", url(serverUrl, "/events"), token, body.toString())))
    }

    /** 增量拉取家庭动态，按时间正序返回。 */
    fun pullEvents(serverUrl: String, token: String, sinceId: Long): List<FamilyEvent> {
        val array = JSONArray(request("GET", url(serverUrl, "/events?since_id=$sinceId"), token, null))
        return buildList {
            for (i in 0 until array.length()) add(parseEvent(array.getJSONObject(i)))
        }
    }

    private fun parseEvent(json: JSONObject): FamilyEvent = FamilyEvent.fromJson(json)

    fun pullPlans(serverUrl: String, token: String, since: String?): List<RemotePlan> {
        val path = if (since.isNullOrBlank()) "/plans" else "/plans?since=$since"
        val array = JSONArray(request("GET", url(serverUrl, path), token, null))
        return buildList {
            for (i in 0 until array.length()) add(parsePlan(array.getJSONObject(i)))
        }
    }

    private fun parsePlan(item: JSONObject): RemotePlan {
        val timesArray = item.optJSONArray("times") ?: JSONArray()
        val fieldTimesJson = item.optJSONObject("field_times") ?: JSONObject()
        return RemotePlan(
            id = item.getString("id"),
            medicineName = item.optString("medicine_name"),
            dose = item.optString("dose", "1 次用量"),
            times = buildList {
                for (t in 0 until timesArray.length()) add(timesArray.getString(t))
            },
            startDate = item.optString("start_date"),
            days = item.optInt("days", 7),
            memberId = item.optLong("member_id"),
            deleted = item.optBoolean("deleted", false),
            fieldTimes = buildMap {
                fieldTimesJson.keys().forEach { key ->
                    put(key, fieldTimesJson.optString(key))
                }
            },
            updatedAt = item.optString("updated_at", "")
        )
    }

    /** 拉取云端排队的提醒（到点、漏服、家属升级提醒）。 */
    fun pullNotifications(serverUrl: String, token: String): List<RemoteNotification> {
        val array = JSONArray(request("GET", url(serverUrl, "/notifications/pending"), token, null))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    RemoteNotification(
                        id = item.getLong("id"),
                        planId = item.optString("plan_id"),
                        date = item.optString("date"),
                        time = item.optString("time"),
                        kind = item.optString("kind", "due"),
                        title = item.optString("title"),
                        body = item.optString("body"),
                        createdAt = item.optString("created_at")
                    )
                )
            }
        }
    }

    fun ackNotifications(serverUrl: String, token: String, ids: List<Long>) {
        if (ids.isEmpty()) return
        val body = JSONObject().put("ids", JSONArray(ids))
        request("POST", url(serverUrl, "/notifications/ack"), token, body.toString())
    }

    private fun parseFamily(json: JSONObject): FamilyResult {
        val membersArray = json.optJSONArray("members") ?: JSONArray()
        return FamilyResult(
            id = json.getLong("id"),
            name = json.optString("name"),
            inviteCode = json.optString("invite_code"),
            members = buildList {
                for (i in 0 until membersArray.length()) {
                    val member = membersArray.getJSONObject(i)
                    add(
                        FamilyMember(
                            id = member.getLong("id"),
                            userId = member.optLong("user_id"),
                            nickname = member.optString("nickname"),
                            relation = member.optString("relation")
                        )
                    )
                }
            }
        )
    }

    private fun url(serverUrl: String, path: String): String =
        serverUrl.trim().trimEnd('/') + path

    private fun request(method: String, url: String, token: String?, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(errorMessage(text, code))
            return text
        } catch (e: ApiException) {
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching {
            val value = JSONObject(body).opt("detail")
            (value as? String).orEmpty().ifBlank {
                (value as? JSONArray)?.optJSONObject(0)?.optString("msg").orEmpty()
            }
        }.getOrDefault("")
        return when {
            detail.isNotBlank() -> detail
            code == 401 -> "登录已过期，请重新登录"
            code == 404 -> "服务器上没有这个地址，检查服务器地址"
            else -> "请求失败（HTTP $code）"
        }
    }
}
