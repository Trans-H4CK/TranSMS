import SMSListerv.MessageParser;
import com.techventus.server.voice.Voice;
import com.techventus.server.voice.datatypes.records.SMS;
import com.techventus.server.voice.datatypes.records.SMSThread;
import gvjava.org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
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
    static String ZIPURL;
    static String CATURL;
    private final static Logger smsLogger = Logger.getLogger("TransSMS");
    static Voice voice;
    static Pattern patRegMessage = Pattern
            .compile("([0-9]+)"
                    + "(\\s(\\S*))?"
                    + "(\\s([0-9]*))?");
    static Pattern patCommand = Pattern
            .compile("([a-zA-Z]*)\\s*(.*)");
    private static String ACCEPTHEADER = "application/vnd.trans_resource.v1";
    private static String introTxt = "Welcome to TranSMS! Text HELP for support, INFO for information, or your zip code to begin looking for resources. Brought to you by Trans*Resource US.";
    private static String helpTxt = "Text zip code for categories. Text zip plus category for resource list (94560 health). Text zip, category, resource number for full listing (94560 health 3).";
    private static String infoTxt = "TranSMS (c) 2013 Trans*Resource US. A Trans*H4CK Oakland project. More info at www.transresource.us.";


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
            if (defaultProps.containsKey("ACCEPTHEADER")) ACCEPTHEADER = defaultProps.getProperty("ACCEPTHEADER");
            if (defaultProps.containsKey("CATURL")) CATURL = defaultProps.getProperty("CATURL");
            if (defaultProps.containsKey("ZIPURL")) ZIPURL = defaultProps.getProperty("ZIPURL");


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
        Date whenInvoked = new Date(); // NOW s
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

                    generateResponse(message, newMessages, matchReg);
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

    private static void generateResponse(SMS in_message, ArrayList<Message> outgoing, Matcher matchReg) {
        //Make API call and build response with relevant data.
        URLBuilder URLBuilder = new URLBuilder(matchReg).invoke(null, null, null);
        String finalURL = URLBuilder.getFinalURL();
        Boolean zipOnly = URLBuilder.getZipOnly();
        int id = URLBuilder.getId();
        String category = URLBuilder.getCategory();

        String responseText = "";
        try {
            URL url = new URL(finalURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", ACCEPTHEADER);

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }
            // Retrieving byte stream object from URL source
            InputStream is = conn.getInputStream();

            // Creating JSON object model from stream
            JSONObject jsonResponse = new JSONObject(InputStreamToString(is));
            if (zipOnly) {
                responseText = MessageParser.parseCats(jsonResponse);
            } else {
                responseText = MessageParser.parseResources(jsonResponse, id, category);
            }
        } catch (Exception e) {
            responseText = "We encountered trouble accessing our database.  Please try again" + e.toString();
        }

        outgoing.add(new Message(in_message.getFrom().getNumber(), in_message.getFrom(), responseText));
    }

    static private String InputStreamToString(InputStream in) {
        InputStreamReader is = new InputStreamReader(in);
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(is);
        try {
            String read = br.readLine();

            while (read != null) {
                //System.out.println(read);
                sb.append(read);
                read = br.readLine();

            }

            return sb.toString();
        } catch (Exception e) {
            return "";
        }

    }

    static private void commandProcess(Message e) {
        smsLogger.info(e.toString());
        StringBuilder sb = new StringBuilder();
        String resultText = "";
        switch (e.command) {
            //0 arguments
            case INFO:
                resultText = infoTxt;
                break;
            case HELP:
                resultText = helpTxt;
                break;
            default:
            case INTRO:
                resultText = introTxt;
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

    static private String generateSummary(SMS message)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(message.getDateTime().toString());
        sb.append(message.getFrom());
        sb.append(message.getContent());
        return sb.toString();
    }

}

enum Command {
    HELP,         // RETURN A HELP TEXT (DEFAULT)
    INFO,         // RETURN A INFO TEXT ABOUT THE SERVICE
    INTRO,        // RETURN A INTRO TEXT
}