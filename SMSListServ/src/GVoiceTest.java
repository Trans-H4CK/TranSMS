/**
 * Created with IntelliJ IDEA.
 * User: Christopher
 * Date: 4/29/13
 * Time: 10:14 PM
 * To change this template use File | Settings | File Templates.
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GVoiceTest {

    public static void main(String[] arg) {
        String testString = "commandX  commandy commandZ";
        Pattern pat1Arg = Pattern
                .compile("(\\S+)");
        Pattern pat2Arg = Pattern
                .compile("([a-zA-Z]+)\\s*(.*)");
        Matcher match1Arg = pat1Arg.matcher(testString);
        Matcher match2Arg = pat2Arg.matcher(testString);
        if (match1Arg.find()) {
            System.out.println(match1Arg.group(1));
        }
        if (match2Arg.find()) {
            System.out.println(match2Arg.group(1));
            System.out.println(match2Arg.group(2));
        }
    }


}