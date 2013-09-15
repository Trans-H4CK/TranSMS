package SMSListerv;

import gvjava.org.json.JSONArray;
import gvjava.org.json.JSONException;
import gvjava.org.json.JSONObject;

public class MessageParser {
    public static String zipHeader = "TranSMS: Categories Near You";
    public static String zipFooter = "Text Zip and Category for List. Or, text HELP.";
    public static String resourceListHeader = "TranSMS: Resources in ";
    public static String resourceListFooter = "Text Zip, Cat and Resource No. for Info. Or, text HELP.";
    public static String resourceDetailHeader = "TranSMS: Resource Info";
    public static String resourceDetailFooter = "Text Zip and Cat to Go Back. Or, text HELP.";

    public static String parseCats(JSONObject jsonResponse) {  //MEOW
        //parses all the categories for a given zip code           MEOW
        try {
            JSONArray categorylist = jsonResponse.getJSONArray("categories");
            StringBuilder sb = new StringBuilder();
            sb.append(zipHeader);
            for (int index = 0; index < categorylist.length(); index++) {
                JSONObject curResource = categorylist.getJSONObject(index).getJSONObject("categories");
                sb.append(index + 1);
                sb.append(". ");
                sb.append(curResource.get("name"));
                sb.append("\n  ");
            }
            sb.append(zipFooter);
            return sb.toString();
        } catch (Exception e) {
            TranSMSServ.smsLogger.severe("PARSE_ERROR " + e.toString() + jsonResponse.toString());

            return "PARSE_ERROR ";
        }
    }

    public static String parseResources(JSONObject jsonResponse, int id, String category) {
        try {
            JSONArray resourceList = jsonResponse.getJSONArray("resources");
            if (id != -1) { //build view for individual resource details
                return parseResource(id, resourceList);
            } else {  //view for nearby resource summary
                return ParseResourceList(resourceList, category);
            }

        } catch (Exception e) {
            TranSMSServ.smsLogger.severe("PARSE_ERROR " + e.toString() + jsonResponse.toString());
            return "PARSE_ERROR ";
        }

    }

    public static String ParseResourceList(JSONArray resourceList, String category) throws JSONException {
        StringBuilder sb = new StringBuilder();

        sb.append(resourceListHeader);
        sb.append(category);
        for (int index = 0; index < resourceList.length(); index++) {
            JSONObject curResource = resourceList.getJSONObject(index).getJSONObject("properties");
            sb.append(index + 1);
            sb.append(". ");
            sb.append(curResource.get("name"));
            sb.append("  ");
            sb.append(curResource.get("trans_friendliness_rating"));
            sb.append("/5  ");
            sb.append(curResource.get("distance"));
            sb.append(" miles \n");

        }
        sb.append(resourceListFooter);
        return sb.toString();
    }

    public static String parseResource(int id, JSONArray resourceList) throws JSONException {
        JSONObject curResource = resourceList.getJSONObject(id).getJSONObject("properties");
        StringBuilder sb = new StringBuilder();
        sb.append(resourceDetailHeader);
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
        sb.append(resourceDetailFooter);
        return sb.toString();
    }
}