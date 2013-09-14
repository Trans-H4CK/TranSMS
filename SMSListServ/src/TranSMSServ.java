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
    static String APIURL;
    private final static Logger smsLogger = Logger.getLogger("TransSMS");
    static Voice voice;
    static Pattern patRegMessage = Pattern
            .compile("([0-9]+)"
                  + "(\\s(\\S*))?"
                  + "(\\s([0-9]*))?");
    static Pattern patCommand = Pattern
            .compile("([a-zA-Z]*)\\s*(.*)");

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
            FileInputStream in = new FileInputStream("settings.env");
            defaultProps.load(in);
            if (defaultProps.containsKey("username")) userName = defaultProps.getProperty("username");
            if (defaultProps.containsKey("password")) pass = defaultProps.getProperty("password");
            if (defaultProps.containsKey("APIURL")) APIURL = defaultProps.getProperty("APIURL");
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
                smsLogger.info("Sending message" + e);
                sendMessage(e);
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
        for (SMS message : messages) {
            //check if newer than last datastamp
                if (message.getDateTime().after(lastCheckDate) && !message.getFrom().getName().equals("Me")) {   //if so, process, generate the messages to be sent,  and add to our list

                    smsLogger.info(message.toString());
                Matcher matchReg = patRegMessage.matcher(message.getContent());
                Matcher matchCommand = patCommand.matcher(message.getContent());
                if (matchReg.find()) {
                //If normal message (prefixed with a zip)

                    generateResponse(message,newMessages,matchReg);
                }
                //If command

                else if (matchCommand.find()) {
                    //create a response that includes a command
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

    private static void generateResponse(SMS in_message,ArrayList<Message> outgoing, Matcher matchReg) {
     //Make API call and build response with relevant data.
        String requestURL = APIURL + "?";
        if(matchReg.group(1) !=null) {
           String zip = matchReg.group(1);
            requestURL = requestURL + zip;
        }
        if(matchReg.group(3) !=null){
            String category = matchReg.group(3);
            requestURL = requestURL + category;
        }
        if(matchReg.group(5) !=null){
            String id = matchReg.group(5);
            requestURL = requestURL + id;
        }

     outgoing.add(new Message(in_message.getFrom().getNumber(),in_message.getFrom(), requestURL));
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
            default:
            case HELP:
                resultText = "Welcome to TranSMS.  Msg a zipcode \"#####\" for categories in that area. "
                      + "Msg a zip and category for a list of trans-friendly resources" ;
        }

        Message resultMessage = new Message(e.from.getNumber(), e.from, resultText);
        sendMessage(resultMessage);

    }


    static private void sendMessage(Message message) {
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
}