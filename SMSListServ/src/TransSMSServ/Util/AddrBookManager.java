package TransSMSServ.Util;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Christopher
 * Date: 5/4/13
 * Time: 10:33 AM
 * To change this template use File | Settings | File Templates.
 */
public class AddrBookManager {
    String HumanPath;
    String MachinePath;
    public HashMap<String, ArrayList<String>> myAddrBook;

    public AddrBookManager(String inHumanPath, String inMachinePath) {
        HumanPath = inHumanPath;
        MachinePath = inMachinePath;
        myAddrBook = new HashMap<String, ArrayList<String>>();
        buildAddrBook();
    }

    private void buildAddrBook() {
        BufferedReader br = null;

        try {

            String curLine;

            br = new BufferedReader(new FileReader(MachinePath));

            while ((curLine = br.readLine()) != null) {
                addContact(curLine);
            }

            br = new BufferedReader(new FileReader(HumanPath));

            while ((curLine = br.readLine()) != null) {
                addContact(curLine);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }


    }

    private void addContact(String curLine) {
        Pattern patContact = Pattern
                .compile("(.+?):(.+)");
        Matcher matchReg = patContact.matcher(curLine);
        if (curLine.length() > 5 && curLine.charAt(0) != '#' && matchReg.find()) {
            String name = matchReg.group(1).trim();
            List<String> unfilteredValues = Arrays.asList(matchReg.group(2).split(", "));
            ArrayList<String> toInsert;
            if (myAddrBook.containsKey(name)) {
                toInsert = myAddrBook.get(name);
            } else {
                toInsert = new ArrayList<String>(unfilteredValues.size());
            }

            for (String value : unfilteredValues) {
                String trimmedValue = value.trim();
                if (!toInsert.contains(trimmedValue)) {
                    toInsert.add(trimmedValue);
                }
            }
            myAddrBook.put(name, toInsert);

        }

    }


    public void writeAddrBook() {
        BufferedWriter bw;
        try {
            bw = new BufferedWriter(new FileWriter(new File(MachinePath)));
            StringBuilder sb;
            for (String key : myAddrBook.keySet()) {
                sb = new StringBuilder(40);
                sb.append(key);
                sb.append(": ");
                boolean first = true;
                for (String value : myAddrBook.get(key)) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append(value);

                }
                sb.append("\n");
                bw.write(sb.toString());
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public List<AbstractMap.SimpleEntry<String, String>> evaluateAddresses(String from, String group) {

        List<String> unEvaluatedAddrs = Arrays.asList(group.split(" ,"));
        List<String> SeenAddrs = new ArrayList();
        List<AbstractMap.SimpleEntry<String, String>> toReturn = new ArrayList<AbstractMap.SimpleEntry<String, String>>();
        for (String addr : unEvaluatedAddrs) {
            List<AbstractMap.SimpleEntry<String, String>> derivedPaths = evaluateAddress(addr, SeenAddrs);

            for (AbstractMap.SimpleEntry<String, String> entry : derivedPaths) {
                toReturn.add(new AbstractMap.SimpleEntry<String, String>(entry.getKey(), identify(from) + entry.getValue()));
            }

        }
        return toReturn;
    }

    private List<AbstractMap.SimpleEntry<String, String>> evaluateAddress(String addr, List<String> SeenAddrs) {
        List<AbstractMap.SimpleEntry<String, String>> toReturn = new ArrayList<AbstractMap.SimpleEntry<String, String>>();
        if (SeenAddrs.contains(addr)) {
            return toReturn; // base case, return nothing
        }
        SeenAddrs.add(addr);
        if (!myAddrBook.containsKey(addr)) {
            toReturn.add(new AbstractMap.SimpleEntry<String, String>(addr, "")); // if not in book, just return this with a null path
            return toReturn;
        }
        ArrayList<String> addrValues = myAddrBook.get(addr);
        for (String value : addrValues) {
            List<AbstractMap.SimpleEntry<String, String>> derivedPaths = evaluateAddress(value, SeenAddrs);

            for (AbstractMap.SimpleEntry<String, String> entry : derivedPaths) {
                toReturn.add(new AbstractMap.SimpleEntry<String, String>(entry.getKey(), "->" + addr + entry.getValue()));

            }
        }
        return toReturn;
    }

    public String identify(String number) {
        if (number.length() == 12 && number.charAt(0) == '+' && number.charAt(1) == '1') {
            number = number.substring(2);
        }
        for (String key : myAddrBook.keySet()) {
            if (myAddrBook.get(key).size() == 1 && myAddrBook.get(key).get(0).equals(number)) {
                return key;
            }
        }
        return number;
    }


}
