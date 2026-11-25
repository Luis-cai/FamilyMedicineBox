package com.family.medbox.domain

/**
 * 智能体统一消费的场景事件。
 * 所有入口（闹钟、打卡、扫码、家人操作）都收敛成这几种事件，
 * 编排层只认事件，不再关心它从哪个页面产生。
 */
enum class SceneEventType {
    DOSE_DUE,
    DOSE_MISSED,
    STOCK_RUNNING_OUT,
    EXPIRING_SOON
}

data class SceneEvent(
    val type: SceneEventType,
    val actorName: String,
    val planId: String? = null,
    val medicineId: Long? = null,
    val time: String? = null
)
