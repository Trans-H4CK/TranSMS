import SMSListerv.Util.AddrBookManager;
import com.techventus.server.voice.Voice;
import com.techventus.server.voice.datatypes.Contact;
import com.techventus.server.voice.datatypes.records.SMS;
import com.techventus.server.voice.datatypes.records.SMSThread;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Christopher
 * Date: 4/30/13
 * Time: 9:42 AM
 */
public class TranSMSServ {

    private static String userName;
    private static String pass;
    private static Date lastCheckDate;
    static Properties defaultProps;
    static Properties dateProps;

    private final static Logger smsLogger = Logger.getLogger("TransSMS");
    static Voice voice;

    public static void main(String[] arg) {
        //todo:refactor
        //todo: multiple message merge logic
        //todo: stability

        if (init_properties()) return;  // returns true if init_properties failed

        try {
            smsLogger.addHandler(new FileHandler("TransSMS.log"));
            smsLogger.info("TranSMSServ server Spinning Up");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            voice = new Voice(userName, pass);

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }


        Calendar gc = new GregorianCalendar();
        gc.set(Calendar.SECOND, 58);
        gc.set(Calendar.MILLISECOND, 0);
        gc.add(Calendar.MINUTE, 0);


        Timer time = new Timer(); // Instantiate Timer Object
        time.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handleMessages();
            }
        }, gc.getTime(), 1000 * 60);
    smsLogger.info("SMS Checks scheduled to begin on " + gc.getTime());

    }


    private static boolean init_properties() {
        defaultProps = new Properties();
        try {
            String machineReadablePath = "";
            String humanReadablePath = "";
            FileInputStream in = new FileInputStream("settings.env");
            defaultProps.load(in);
            if (defaultProps.containsKey("username")) userName = defaultProps.getProperty("username");
            if (defaultProps.containsKey("password")) pass = defaultProps.getProperty("password");
            if (defaultProps.containsKey("humanReadablePath")) {
                humanReadablePath = defaultProps.getProperty("humanReadablePath").trim();
            }
            if (defaultProps.containsKey("machineReadablePath")) {
                machineReadablePath = defaultProps.getProperty("machineReadablePath");
            }
            in.close();
        } catch (IOException e) {
            System.out.println("settings.env file not found");
            return true;
        }
        dateProps = new Properties();
        try {
            FileInputStream in = new FileInputStream("date.env");
            dateProps.load(in);
            DateFormat df = new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy");
            if (dateProps.containsKey("freshDate")) lastCheckDate = df.parse(dateProps.getProperty("freshDate"));
            else {
                lastCheckDate = new Date();
            }
            in.close();
        } catch (IOException e) {
            System.out.println("date.env file not found");
            lastCheckDate = new Date();
        } catch (ParseException e) {
            System.out.println("Date malformed");
            lastCheckDate = new Date();
        }
        return false;
    }

    static private void handleMessages() {
        smsLogger.info("HandleMessage Called");
        Date whenInvoked=new Date(); // NOW s
        ArrayList<Message> allMessages;
        if (!voice.isLoggedIn()) {
            try {
                voice.login();
            } catch (IOException e) {
                e.printStackTrace();
                smsLogger.severe(("Login Failed"));
                return;
            }
        }
        try {
            allMessages = parseMessages(voice.getSMSThreads());
        } catch (IOException e) {
            e.printStackTrace();
            smsLogger.severe(("Could not fetch SMS's"));
            return;
        }

        smsLogger.info(allMessages.size() + " messages recieved from GVOICE");
        for (Message e : allMessages) {

            if (e.isCommand) {
                commandProcess(e);
            } else {
                smsLogger.info("Sending to normal message processing" + e);
                normalMessageProcess(e);
            }
        }
        SetNewDate(whenInvoked);


    }

    private static void SetNewDate(Date newDate) {
        //correcting our date to 0 seconds

        lastCheckDate = newDate; //NOW

        dateProps.setProperty("freshDate", lastCheckDate.toString());
        try {
            dateProps.store(new FileOutputStream("date.env"), null);
        } catch (IOException e) {
            smsLogger.severe("Can not write to date.env to update Date");
        }
        smsLogger.info("Date set to " + lastCheckDate);
    }


    static private ArrayList<Message> parseMessages(Collection<SMSThread> inThreads) {
        ArrayList<Message> newMessages = new ArrayList<Message>();

        for (SMSThread thread : inThreads) {
            Collection<SMS> messages = thread.getAllSMS();
            parseMessagesFromThread(newMessages, messages);
        }

        return newMessages;
    }

    private static void parseMessagesFromThread(ArrayList<Message> newMessages, Collection<SMS> messages) {
        Pattern patRegMessage = Pattern
                .compile("(.+?):(.+)");
        Pattern patCommand = Pattern
                .compile("([a-zA-Z]+)\\s*(.*)");

        for (SMS message : messages) {
            //check if newer than last datastamp
            if (message.getDateTime().after(lastCheckDate) && !message.getFrom().getName().equals("Me")) {   //if so, process, generate the messages to be sent,  and add to our list

                smsLogger.info(message.toString());
                Matcher matchReg = patRegMessage.matcher(message.getContent());
                Matcher matchCommand = patCommand.matcher(message.getContent());
                if (matchReg.find()) { //If normal message
                    List<AbstractMap.SimpleEntry<String, String>> messageAddr = myAddrManager.evaluateAddresses(message.getFrom().getNumber(), matchReg.group(1));
                    for (AbstractMap.SimpleEntry<String, String> AddrAndPath : messageAddr) {
                        String messageText = AddrAndPath.getValue() + " : " + matchReg.group(2);

                        newMessages.add(new Message(AddrAndPath.getKey(), message.getFrom(), messageText));
                    }
                }
                //If command

                else if (matchCommand.find()) {

                    Command messageCMD;
                    try {
                        messageCMD = Command.valueOf(matchCommand.group(1).toUpperCase());
                    } catch (IllegalArgumentException e) {
                        messageCMD = Command.HELP;
                        smsLogger.warning("Possible error while parsing " + message.getContent() + " Defaulted to HELP");
                    }

                    newMessages.add(new Message(messageCMD, message.getFrom(), matchCommand.group(2)));

                } else {
                    smsLogger.severe("Unable to parse message: " + message);
                }

            }
        }
    }


    static private void normalMessageProcess(Message message) {
        if (!voice.isLoggedIn()) {
            try {
                voice.login();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            voice.sendSMS(message.address, message.text);
        } catch (IOException e) {
            smsLogger.severe("Failed to send " + message);
        }
    }


    static private void commandProcess(Message e) {
        smsLogger.info(e.toString());
        StringBuilder sb = new StringBuilder();
        String resultText = "Command Succeeded:" + e.text;
        switch (e.command) {
            //0 arguments
            case COMMANDS:
                resultText ="Commands are: "+
                        "(you must specify X and Y if needed) " +
                        " HELP (gives a help text)\n" +
                        " LISTS(shows all lists)\n" +
                        " COMMANDS(shows this list)\n" +
                        " CATFACT(gives you a true catfact)\n" +
                        " REGISTER(registers you in the database as X)" +
                        " SUBSCRIBE(adds you to list X)\n" +
                        " UNSUBSCRIBE(removes you from list X)\n" +
                        " ADD(create a new list X)\n" +
                        " REMOVE(delete list X)\n" +
                        " WHO(shows who is on list X)\n" +
                        " SUBOTHER(adds X to list Y)\n" +
                        " UNSUBOTHER(removes X from group Y)\n";
                break;
            case CATFACT:
                Random r = new Random(lastCheckDate.getTime());
                String[] catFacts = {"Cats spend nearly 16 hours a day sleeping!",
                        "Cat skulls are harder than lead",
                        "The average cat will be photographed over 1000 times",
                        "Cat DNA is 100 percent similar to Human",
                        "Cats live amongst Humans in order to study them",
                        "Housecats never stop growing! The largest housecat of all time grew to 136 inches long before it was discovered to be a panther",
                        "Cats know how to use knives",
                        "One out of every three cats is a murder cat",
                        "Cats are unable to truely love",
                        "If you aren't watching your cat, your cat is watching you",
                        "If your cat is exceptionally good at climbing, you should take it to a vet. It may be a racoon",
                        "The cat's dorsal fin is entirely vestigial",
                        "Cats mate while standing on two legs",
                        "A bull cat's bellow can be heard as far away as 8 miles",
                        "Cats  live in social groups of five to several hundred. They use echolocation to find prey and often hunt together by surrounding a school of fish, trapping them and taking turns swimming through the school and catching fish. ",
                        "Cats are technically a fungus"};
                resultText = catFacts[r.nextInt(catFacts.length)];
                break;
            //1 arguments
                break;
            // 2 arguments
            default:
            case HELP:
                resultText = "Welcome to Chris Beacham's SMS Listserv. txt \'COMMANDS\' for a list of commands," +
                        " and 'LISTS' to see the current Lists.  To send a message to a list, txt with \'ListName1 ListName2 ... : YourMessage\" ";
        }

        Message resultMessage = new Message(e.from.getNumber(), e.from, resultText);
        normalMessageProcess(resultMessage);

    }

}

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

enum Command {
    HELP,         // RETURN A HELP TEXT (DEFAULT)
    COMMANDS,     // RETURN A LIST OF ALL COMMANDS
    CATFACT,       // RETURN A RANDOM CAT FACT
}