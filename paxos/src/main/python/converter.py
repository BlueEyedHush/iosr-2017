import re
from utils import GraphicsHelper, MessageFactory
from events import MovingObject


class LogsToEventsConverter:
    def __init__(self, cluster, event_loop):
        self.cluster = cluster
        self.event_loop = event_loop

    def handle_message(self, logs, matched, i, unicast):
        name = matched.group(1)
        src = self.cluster.nodes[logs[i].node].outPort.getCenter()
        dept = logs[i].timestamp

        payload = matched.group(2)
        received = "ReceivedMessage(" + name + payload + "," + str(logs[i].node) + ")"

        for j in range(i + 1, len(logs)):
            if logs[j].msg == received:
                dest = self.cluster.nodes[logs[j].node].inPort.getCenter()
                arvl = logs[j].timestamp
                obj = MessageFactory.create_message(name, src)
                self.event_loop.add_event(MovingObject(obj, src, dest, dept, arvl, self.cluster.win))
                if unicast:
                    break

    def convert(self, logs):
        for i in range(len(logs)):
            matched = re.match('^SendUnicast\((\w+)(.+),\d+\)$', logs[i].msg)
            unicast = True
            if not matched:
                matched = re.match('^SendMulticast\((\w+)(.+)\)$', logs[i].msg)
                unicast = False
            if matched:
                self.handle_message(logs, matched, i, unicast)
            else:
                pass    # TODO: Handle other events
