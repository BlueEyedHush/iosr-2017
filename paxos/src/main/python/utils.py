import graphics as g


class GraphicsHelper:
    @staticmethod
    def move_point(p, v):
        return g.Point(p.getX() + v.getX(), p.getY() + v.getY())

    @staticmethod
    def scale_vector(v, a):
        return g.Point(v.getX() * a, v.getY() * a)

    @staticmethod
    def calculate_velocity(source, destination, departure, arrival):
        return GraphicsHelper.scale_vector(
            GraphicsHelper.move_point(destination, GraphicsHelper.scale_vector(source, -1)), 1 / (arrival - departure))


class MessageFactory:
    msgColors = {
        "Prepare": "white",
        "Promise": "yellow",
        "AcceptRequest": "orange",
        "Accepted": "red",

        "RoundTooOld": "grey",
        "HigherProposalReceived": "black",

        "LearnerQuestionForValue": "blue",
        "LearnerAnswerWithValue": "green",
        "TestMessage": "brown"
    }

    @staticmethod
    def create_message(name, position):
        msg = g.Circle(position, 5)
        msg.setFill(MessageFactory.msgColors[name])
        return msg
