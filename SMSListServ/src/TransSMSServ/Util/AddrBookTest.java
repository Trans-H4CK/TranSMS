package TransSMSServ.Util;

/**
 * Created with IntelliJ IDEA.
 * User: Christopher
 * Date: 5/4/13
 * Time: 12:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class AddrBookTest {

    public static void main(String[] args) {

        AddrBookManager myAddrManager = new AddrBookManager("listServData.txt", "humanEnteredData.txt");
        System.out.println(myAddrManager.evaluateAddresses("Test", "Family"));
        System.out.println(myAddrManager.evaluateAddresses("Test", "Shed"));
        System.out.println(myAddrManager.evaluateAddresses("Test", "Family Shed Becca Lips"));
        System.out.println(myAddrManager.myAddrBook);
    }

}

