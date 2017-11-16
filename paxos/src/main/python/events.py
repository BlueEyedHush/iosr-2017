import abc
import graphics as g
import time
from utils import GraphicsHelper


class Event:
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def perform_action(self, time):
        """Performs action at given time and returns true if the event is outdated."""
        return

    @abc.abstractmethod
    def get_start(self):
        """Return starting time of the event."""
        return

    @abc.abstractmethod
    def get_end(self):
        """Return ending time of the event."""
        return


class MovingObject(Event):
    def __init__(self, obj, source, destination, departure, arrival, window):
        self.obj = obj
        self.src = source
        self.dest = destination
        self.dept = departure
        self.arvl = arrival
        self.win = window
        self.v = GraphicsHelper.calculate_velocity(source, destination, departure, arrival)
        self.acc = g.Point(0, 0)

    def draw(self):
        self.obj.draw(self.win)

    def undraw(self):
        self.obj.undraw()

    def move(self):
        self.acc = GraphicsHelper.move_point(self.acc, self.v)
        dx = int(self.acc.getX())
        dy = int(self.acc.getY())
        self.acc = GraphicsHelper.move_point(self.acc, g.Point(-dx, -dy))
        self.obj.move(dx, dy)

    def perform_action(self, time, ):
        if self.dept == time:
            self.draw()

        if self.dept <= time < self.arvl:
            self.move()

        if time == self.arvl + 100:
            self.undraw()
            return True

        return False

    def get_start(self):
        return self.dept

    def get_end(self):
        return self.arvl + 100


class EventLoop:
    def __init__(self):
        self.count = 0
        self.events = dict()

    def add_event(self, ev):
        self.events[self.count] = ev
        self.count += 1

    def run(self):
        start = self.events[0].get_start() - 10
        stop = self.events[self.count - 1].get_end() + 10

        for t in range(start, stop):
            remove = []
            for ev_id, event in self.events.items():
                if event.perform_action(t):
                    remove.append(ev_id)

            for ev_id in remove:
                del self.events[ev_id]

            time.sleep(0.1)
