import com.techventus.server.voice.datatypes.Contact;

class Message {
    boolean isCommand;
    Command command;
    Contact from;
    String address = "";
    String text = "";

    Message(String toWho, Contact fromWhom, String intext) {
        from = fromWhom;
        address = toWho;
        isCommand = false;
        text = intext;
    }

    Message(Command inCommand, Contact fromWhom, String intext) {
        isCommand = true;
        command = inCommand;
        from = fromWhom;
        text = intext;

    }

    public String toString() {
        if (isCommand) {
            return "Command: " + command.name() + text;
        }
        return "from: " + from + " to: " + address + " message: " + text;

    }
}
