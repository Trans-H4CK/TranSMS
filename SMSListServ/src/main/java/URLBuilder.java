import java.util.regex.Matcher;

public class URLBuilder {
    private final Matcher matchReg;
    private Boolean zipOnly;
    private int id;
    private String category;
    private String finalURL;

    public URLBuilder(Matcher matchReg) {
        this.matchReg = matchReg;
    }

    public Boolean getZipOnly() {
        return zipOnly;
    }

    public int getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getFinalURL() {
        return finalURL;
    }

    public URLBuilder invoke( String APIURL, String ZIPURL, String CATURL) {
        zipOnly = true;
        id = -1;
        String requestURL = APIURL;
        String GETargs = "?";
        category = "";
        if (matchReg.group(1) != null) {
            String zip = matchReg.group(1);
            GETargs = GETargs + "zip_code=" + zip + "&";
        }
        if (matchReg.group(3) != null) {
            zipOnly = false;
            category = matchReg.group(3);
            category = category.toLowerCase();
            category = Character.toUpperCase(category.charAt(0)) + category.substring(1); //capitalize First letter
            GETargs = GETargs + "category=" + category + "&per_page=5&";
        }
        if (matchReg.group(5) != null) {
            id = Integer.parseInt(matchReg.group(5));
        }
        if (zipOnly) {
            requestURL = requestURL + ZIPURL;
        } else {
            requestURL = requestURL + CATURL;
        }

        finalURL = requestURL + GETargs;
        return this;
    }
}
