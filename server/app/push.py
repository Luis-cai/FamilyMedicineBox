import logging

logger = logging.getLogger("medibox.push")


class Pusher:
    """推送通道抽象：演示阶段只打日志，接入极光/厂商推送时替换实现。"""

    def send(self, token: str, title: str, body: str) -> None:
        raise NotImplementedError


class MockPusher(Pusher):
    def send(self, token: str, title: str, body: str) -> None:
        logger.info("[push] token=...%s | %s | %s", token[-6:], title, body)


pusher: Pusher = MockPusher()
