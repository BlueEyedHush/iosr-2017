import re


class LogEntry:
    def __init__(self, timestamp, node, message):
        self.timestamp = int(timestamp)
        self.node = int(node)
        self.msg = message


class LogParser:

    @staticmethod
    def parse(filenames):
        filenames = [filenames] if isinstance(filenames, str) else filenames
        regex = '^INFO: (\d+) - (\d+) - (.+)$'
        logs = []

        for filename in filenames:
            with open(filename) as f:
                for line in f:
                    m = re.match(regex, line.strip())
                    if m:
                        logs.append(LogEntry(m.group(1), m.group(2), m.group(3)))

        logs.sort(key=lambda x: x.timestamp)
        return logs
