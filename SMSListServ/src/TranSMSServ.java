import com.techventus.server.voice.Voice;
import com.techventus.server.voice.datatypes.Contact;
import com.techventus.server.voice.datatypes.records.SMS;
import com.techventus.server.voice.datatypes.records.SMSThread;
import gvjava.org.json.JSONArray;
import gvjava.org.json.JSONException;
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
        Boolean zipOnly = true;
        int id = -1;
        String requestURL = APIURL;
        String responseText = "";
        String GETargs = "?";
        if (matchReg.group(1) != null) {
            String zip = matchReg.group(1);
            GETargs = GETargs + "zip_code=" + zip + "&";
        }
        if (matchReg.group(3) != null) {
            zipOnly = false;
            String category = matchReg.group(3);
            GETargs = GETargs + "category=" + category + "&per_page=5&";
        }
        if (matchReg.group(5) != null) {
            id = Integer.parseInt(matchReg.group(5));
        }
        try {
            if (zipOnly) {
                requestURL = requestURL + ZIPURL;
            } else {
                requestURL = requestURL + CATURL;
            }

            URL url = new URL(requestURL + GETargs);
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
            JSONObject jsonResponse = new JSONObject(InputStreamtoString(is));
            if (zipOnly) {
                responseText = parseCats(jsonResponse);
            } else {
                responseText = parseResources(jsonResponse, id);
            }
        } catch (Exception e) {
            responseText = "We encountered trouble accessing our database.  Please try again" + e.toString();
        }

        outgoing.add(new Message(in_message.getFrom().getNumber(), in_message.getFrom(), responseText));
    }

    private static String parseCats(JSONObject jsonResponse) {  //MEOW
        //parses all the categories for a given zip code           MEOW
        try {
            JSONArray categorylist = jsonResponse.getJSONArray("categories");
            StringBuilder sb = new StringBuilder();
            sb.append("Trans resources near that zipcode:")
            for(int index=0;index<categorylist.length();index++)
            {
                JSONObject curResource = categorylist.getJSONObject(index).getJSONObject("categories");
                sb.append(index+1);
                sb.append(". ");
                sb.append(curResource.get("name"));
                sb.append("\n  ");
            }
            return sb.toString();
        }
            catch (Exception e) {
                smsLogger.severe("PARSE_ERROR " + e.toString() + jsonResponse.toString());

                return "PARSE_ERROR ";
            }
    }

    private static String parseResources(JSONObject jsonResponse, int id) {
        try {
            JSONArray resourceList = jsonResponse.getJSONArray("resources");
            if (id != -1) { //build view for individual resource details
                return parseResource(id, resourceList);
            } else {  //view for nearby resource summary
                return ParseResourceList(resourceList);
            }

        } catch (Exception e) {
            smsLogger.severe("PARSE_ERROR " + e.toString() + jsonResponse.toString());
            return "PARSE_ERROR ";
        }

    }

    private static String ParseResourceList(JSONArray resourceList) throws JSONException {
        StringBuilder sb = new StringBuilder();
        for(int index=0;index<resourceList.length();index++)
        {
            JSONObject curResource = resourceList.getJSONObject(index).getJSONObject("properties");
            sb.append(index+1);
            sb.append(". ");
            sb.append(curResource.get("name"));
            sb.append("  ");
            sb.append(curResource.get("trans_friendliness_rating"));
            sb.append("/5  ");
            sb.append(curResource.get("distance"));
            sb.append(" miles \n");
        }
        return sb.toString();
    }

    private static String parseResource(int id, JSONArray resourceList) throws JSONException {
        JSONObject curResource = resourceList.getJSONObject(id).getJSONObject("properties");
        StringBuilder sb = new StringBuilder();
        sb.append(curResource.get("name"));
        sb.append("  ");
        sb.append(curResource.get("trans_friendliness_rating"));
        sb.append("/5  ");
        sb.append(curResource.get("street_address_1"));
        sb.append("  ");
        sb.append(curResource.get("street_address_2"));
        sb.append("  ");
        sb.append(curResource.get("city"));
        sb.append(",  ");
        sb.append(curResource.get("state"));
        sb.append("  ");
        sb.append(curResource.get("zip"));
        sb.append("  ");
        sb.append(curResource.get("distance"));
        sb.append(" miles ");
        sb.append(curResource.get("phone"));
        sb.append("  ");
        sb.append(curResource.get("contact_name"));
        return sb.toString();
    }

    static private String InputStreamtoString(InputStream in) {
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
        String resultText = "Command Succeeded:" + e.text;
        switch (e.command) {
            //0 arguments
            case COMMANDS:
                resultText = "Commands are: " +
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
                        + "Msg a zip and category for a list of trans-friendly resources";
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