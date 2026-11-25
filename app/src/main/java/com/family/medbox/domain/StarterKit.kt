package com.family.medbox.domain

/**
 * 家庭常备药模板：按家庭最常见的用药场景分组。
 * 纯端侧数据，不联网；勾选后一次性写入本地药箱，有效期待用户补录。
 */
data class StarterDrug(
    val name: String,
    val ingredient: String,
    val category: String,
    val spec: String,
    val form: String,
    val quantity: Int,
    val unit: String,
    val note: String
)

data class StarterGroup(
    val title: String,
    val hint: String,
    val drugs: List<StarterDrug>
)

object StarterKit {

    val groups = listOf(
        StarterGroup(
            title = "退热镇痛",
            hint = "家庭使用频率最高，先备上",
            drugs = listOf(
                StarterDrug(
                    "布洛芬缓释胶囊", "布洛芬", "解热镇痛",
                    "0.3g×20粒", "胶囊", 1, "盒",
                    "成人退热镇痛，胃不好的人饭后服"
                ),
                StarterDrug(
                    "对乙酰氨基酚片", "对乙酰氨基酚", "解热镇痛",
                    "0.5g×12片", "片剂", 1, "盒",
                    "退热首选，不要和其他复方感冒药同服"
                ),
                StarterDrug(
                    "布洛芬混悬液", "布洛芬", "解热镇痛",
                    "100ml", "混悬液", 1, "瓶",
                    "儿童退热，按体重给药"
                )
            )
        ),
        StarterGroup(
            title = "感冒鼻塞",
            hint = "换季时最容易临时缺",
            drugs = listOf(
                StarterDrug(
                    "复方氨酚烷胺胶囊", "对乙酰氨基酚", "感冒用药",
                    "12粒", "胶囊", 1, "盒",
                    "缓解鼻塞流涕，注意已在吃退热药就别叠加"
                ),
                StarterDrug(
                    "生理性海水鼻腔喷雾", "氯化钠", "感冒用药",
                    "50ml", "喷雾", 1, "瓶",
                    "鼻塞时冲洗鼻腔，儿童也能用"
                )
            )
        ),
        StarterGroup(
            title = "肠胃不适",
            hint = "吃坏肚子、积食都用得上",
            drugs = listOf(
                StarterDrug(
                    "蒙脱石散", "蒙脱石", "肠胃用药",
                    "3g×10袋", "散剂", 1, "盒",
                    "急性腹泻用，和其他药间隔 1 小时"
                ),
                StarterDrug(
                    "口服补液盐散", "口服补液盐", "肠胃用药",
                    "5.125g×6袋", "散剂", 1, "盒",
                    "腹泻补水防脱水，按说明冲调"
                ),
                StarterDrug(
                    "健胃消食片", "山楂", "肠胃用药",
                    "0.8g×32片", "片剂", 1, "盒",
                    "积食胀气时嚼服"
                )
            )
        ),
        StarterGroup(
            title = "过敏与皮肤",
            hint = "换季、蚊虫叮咬常备",
            drugs = listOf(
                StarterDrug(
                    "氯雷他定片", "氯雷他定", "抗过敏",
                    "10mg×6片", "片剂", 1, "盒",
                    "过敏性鼻炎、荨麻疹，服后可能犯困"
                ),
                StarterDrug(
                    "炉甘石洗剂", "炉甘石", "皮肤用药",
                    "100ml", "洗剂", 1, "瓶",
                    "蚊虫叮咬、痱子止痒，破皮处不用"
                )
            )
        ),
        StarterGroup(
            title = "外伤处理",
            hint = "小伤口第一时间能处理",
            drugs = listOf(
                StarterDrug(
                    "碘伏消毒液", "聚维酮碘", "外用消毒",
                    "100ml", "消毒液", 1, "瓶",
                    "伤口消毒，不用酒精直接冲伤口"
                ),
                StarterDrug(
                    "无菌创可贴", "无", "外用敷料",
                    "20片", "创可贴", 1, "盒",
                    "小创口包扎，湿透或渗液要及时更换"
                ),
                StarterDrug(
                    "医用纱布块", "无", "外用敷料",
                    "5cm×7cm×10片", "纱布", 1, "包",
                    "擦伤覆盖，配合胶带固定"
                )
            )
        ),
        StarterGroup(
            title = "老人慢病",
            hint = "按家人实际情况选择",
            drugs = listOf(
                StarterDrug(
                    "苯磺酸氨氯地平片", "氨氯地平", "降压药",
                    "5mg×7片", "片剂", 1, "盒",
                    "需遵医嘱长期服用，别自行停药"
                ),
                StarterDrug(
                    "盐酸二甲双胍片", "二甲双胍", "降糖药",
                    "0.5g×20片", "片剂", 1, "盒",
                    "随餐服用，需遵医嘱"
                )
            )
        )
    )

    val totalCount: Int get() = groups.sumOf { it.drugs.size }
}
