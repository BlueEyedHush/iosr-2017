import graphics as g
import math


class Node:
    def __init__(self, id, obj, position, in_port, out_port):
        self.id = id
        self.obj = obj
        self.position = position
        self.inPort = in_port
        self.outPort = out_port


class NodeFactory:
    def __init__(self, node_shape, port_size=None):
        (self.h, self.w) = node_shape
        self.port_size = port_size if port_size else self.port_size

    def get_port_position(self, node_position, cluster_center, in_port):
        x = node_position.getX()
        y = node_position.getY()
        if in_port:
            if x < cluster_center.getX():
                if y > cluster_center.getY():
                    return g.Point(x, y - self.h)
                elif y < cluster_center.getY():
                    return g.Point(x + self.w, y)
                else:
                    return g.Point(x + self.w, y - self.h)
            elif x > cluster_center.getX():
                if y > cluster_center.getY():
                    return g.Point(x - self.w, y)
                elif y < cluster_center.getY():
                    return g.Point(x, y + self.h)
                else:
                    return g.Point(x - self.w, y + self.h)
            else:
                if y > cluster_center.getY():
                    return g.Point(x - self.w, y - self.h)
                elif y < cluster_center.getY():
                    return g.Point(x + self.w, y + self.h)
        else:
            if x < cluster_center.getX():
                if y > cluster_center.getY():
                    return g.Point(x + self.w, y)
                elif y < cluster_center.getY():
                    return g.Point(x, y + self.h)
                else:
                    return g.Point(x + self.w, y + self.h)
            elif x > cluster_center.getX():
                if y > cluster_center.getY():
                    return g.Point(x, y - self.h)
                elif y < cluster_center.getY():
                    return g.Point(x - self.w, y)
                else:
                    return g.Point(x - self.w, y - self.h)
            else:
                if y > cluster_center.getY():
                    return g.Point(x + self.w, y - self.h)
                elif y < cluster_center.getY():
                    return g.Point(x - self.w, y + self.h)

    def create_node(self, node_id, cluster_size, cluster_shape, window):
        angle = (2 * math.pi / cluster_size) * node_id
        x = cluster_shape.getCenter().getX() + cluster_shape.getRadius() * math.sin(angle)
        y = cluster_shape.getCenter().getY() - cluster_shape.getRadius() * math.cos(angle)
        position = g.Point(x, y)

        obj = g.Rectangle(g.Point(x - self.w, y - self.h), g.Point(x + self.w, y + self.h))
        obj.draw(window)

        in_port = g.Circle(self.get_port_position(position, cluster_shape.getCenter(), True), self.port_size)
        in_port.setFill("pink")
        in_port.draw(window)

        out_port = g.Circle(self.get_port_position(position, cluster_shape.getCenter(), False), self.port_size)
        out_port.setFill("purple")
        out_port.draw(window)

        return Node(node_id, obj, position, in_port, out_port)


class Cluster:
    def __init__(self, title, size, window_shape, node_factory):
        self.width, self.height = window_shape
        self.shape = g.Circle(g.Point(self.width / 2, self.height / 2), min(self.width, self.height) / 3)
        self.win = g.GraphWin(title, self.width, self.height)
        self.nodes = dict()
        for node_id in range(size):
            self.nodes[node_id] = node_factory.create_node(node_id, size, self.shape, self.win)

    def close(self):
        self.win.close()
