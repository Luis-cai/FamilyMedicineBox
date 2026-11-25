"""药品知识库与用药安全规则的种子数据。

- DRUGS：通用名 / 别名（商品名）/ 分类 / 药理类别 / 说明，来源 App 内置 drug_kb.json；
- INTERACTION_RULES：常见家庭用药相互作用，来源 App 内置 InteractionRules.kt。

两组数据在服务启动时按幂等方式写入，改这里后重启即可同步到库。
"""

from .db import SessionLocal
from .drug_seed import DRUGS
from .models import Drug, InteractionRule

# severity 与端侧 InteractionSeverity 对齐：high / medium
INTERACTION_RULES = [
    {
        "group_a": ["阿司匹林", "乙酰水杨酸"],
        "group_b": ["布洛芬", "双氯芬酸", "萘普生", "洛索洛芬"],
        "severity": "high",
        "title": "出血风险叠加",
        "detail": "两者都属非甾体抗炎药，同服会增加胃出血风险。需要止痛时先咨询医生，避免长期同服。",
    },
    {
        "group_a": ["对乙酰氨基酚", "扑热息痛"],
        "group_b": ["感冒灵", "感康", "泰诺", "白加黑", "新康泰克", "复方氨酚烷胺", "氨咖黄敏"],
        "severity": "high",
        "title": "退烧成分可能重复",
        "detail": "复方感冒药常含对乙酰氨基酚，叠加服用容易超过安全剂量、损伤肝脏。",
    },
    {
        "group_a": ["阿司匹林", "氯吡格雷"],
        "group_b": ["华法林", "利伐沙班", "达比加群", "阿哌沙班"],
        "severity": "high",
        "title": "抗凝与抗血小板同服",
        "detail": "出血风险明显升高，必须遵医嘱并定期监测凝血功能。",
    },
    {
        "group_a": ["布洛芬", "双氯芬酸", "萘普生"],
        "group_b": ["氨氯地平", "硝苯地平", "缬沙坦", "厄贝沙坦", "依那普利", "美托洛尔"],
        "severity": "medium",
        "title": "可能减弱降压效果",
        "detail": "非甾体抗炎药可能升高血压并影响肾功能，长期同服需监测血压。",
    },
    {
        # 第二组为空表示「与任意其他口服药」，详情里说明间隔时间
        "group_a": ["蒙脱石"],
        "group_b": [],
        "severity": "medium",
        "title": "与其他口服药间隔 2 小时",
        "detail": "蒙脱石会吸附其他药物、降低吸收。先服其他药，至少 2 小时后再服蒙脱石。",
    },
    {
        "group_a": ["阿莫西林", "头孢", "阿奇霉素", "左氧氟沙星", "克拉霉素"],
        "group_b": ["益生菌", "双歧杆菌", "乳酸菌", "妈咪爱"],
        "severity": "medium",
        "title": "抗菌药会杀灭益生菌",
        "detail": "两者间隔 2 小时以上，抗菌药疗程结束后再补益生菌更合适。",
    },
    {
        "group_a": ["左氧氟沙星", "莫西沙星", "环丙沙星"],
        "group_b": ["钙片", "碳酸钙", "铁剂", "硫酸亚铁", "铝碳酸镁", "氢氧化铝"],
        "severity": "medium",
        "title": "影响抗菌药吸收",
        "detail": "钙、铁、铝会与喹诺酮类结合，建议间隔 2 小时以上服用。",
    },
    {
        "group_a": ["头孢"],
        "group_b": ["藿香正气水"],
        "severity": "high",
        "title": "警惕双硫仑样反应",
        "detail": "藿香正气水含酒精，与头孢同用可能引起面红、心慌、呼吸困难，应避免。",
    },
    {
        "group_a": ["奥美拉唑", "泮托拉唑", "埃索美拉唑"],
        "group_b": ["氯吡格雷"],
        "severity": "medium",
        "title": "可能降低抗血小板效果",
        "detail": "部分抑酸药会影响氯吡格雷活化，长期同服需医生调整方案。",
    },
    {
        "group_a": ["阿托伐他汀", "辛伐他汀", "洛伐他汀"],
        "group_b": ["红霉素", "克拉霉素"],
        "severity": "high",
        "title": "他汀浓度升高、肌病风险",
        "detail": "大环内酯类抗菌药会抑制他汀代谢，出现肌肉酸痛、乏力要及时就医。",
    },
    {
        "group_a": ["氯雷他定", "西替利嗪", "扑尔敏", "苯海拉明"],
        "group_b": ["地西泮", "艾司唑仑", "唑吡坦", "安眠"],
        "severity": "medium",
        "title": "嗜睡作用叠加",
        "detail": "抗过敏药与镇静催眠药同服可能加重困倦，服药后避免开车和高空作业。",
    },
    {
        "group_a": ["右美沙芬"],
        "group_b": ["氟西汀", "帕罗西汀", "舍曲林", "文拉法辛"],
        "severity": "high",
        "title": "5-羟色胺综合征风险",
        "detail": "止咳药右美沙芬与 SSRI 类抗抑郁药同服需医生评估，出现发热、震颤应停药就医。",
    },
    {
        "group_a": ["钙片", "碳酸钙", "葡萄糖酸钙"],
        "group_b": ["铁剂", "硫酸亚铁", "琥珀酸亚铁"],
        "severity": "medium",
        "title": "钙铁吸收互相影响",
        "detail": "钙和铁同服会竞争吸收，建议错开 2 小时服用。",
    },
    {
        "group_a": ["阿仑膦酸钠", "双膦酸盐"],
        "group_b": ["钙片", "碳酸钙", "牛奶"],
        "severity": "medium",
        "title": "影响双膦酸盐吸收",
        "detail": "阿仑膦酸钠需空腹单服，至少 30 分钟后再进食或补钙。",
    },
]


def seed_knowledge() -> None:
    """把药品目录和相互作用规则按通用名/标题幂等写入，重复启动不会产生重复行。

    以代码里的种子为准覆盖库中内容，改完种子重启服务即可生效。
    """
    with SessionLocal() as db:
        drugs = {row.generic_name: row for row in db.query(Drug).all()}
        for item in DRUGS:
            row = drugs.get(item["generic_name"])
            if row is None:
                db.add(Drug(**item))
                continue
            for key, value in item.items():
                setattr(row, key, value)

        rules = {(row.title, row.severity): row for row in db.query(InteractionRule).all()}
        for item in INTERACTION_RULES:
            row = rules.get((item["title"], item["severity"]))
            if row is None:
                db.add(InteractionRule(**item))
                continue
            for key, value in item.items():
                setattr(row, key, value)
        db.commit()

