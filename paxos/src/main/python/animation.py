from log_parser import LogParser
from cluster import Cluster, NodeFactory
from converter import LogsToEventsConverter
from events import EventLoop

N = 4
WIDTH = HEIGHT = 600
TITLE = "Fast Paxos"
LOGFILE = "../../../logs/test.log"


factory = NodeFactory((20, 20), 8)
cluster = Cluster(TITLE, N, (WIDTH, HEIGHT), factory)

loop = EventLoop()

logs = LogParser.parse(LOGFILE)
converter = LogsToEventsConverter(cluster, loop)
converter.convert(logs)

loop.run()

cluster.close()
