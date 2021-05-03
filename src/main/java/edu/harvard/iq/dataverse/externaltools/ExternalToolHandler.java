package edu.harvard.iq.dataverse.externaltools;

import cern.colt.Arrays;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.GlobalId;
import edu.harvard.iq.dataverse.authorization.users.ApiToken;
import edu.harvard.iq.dataverse.externaltools.ExternalTool.ReservedWord;
import edu.harvard.iq.dataverse.util.SystemConfig;
import java.io.StringReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.net.HttpURLConnection;
import org.json.simple.JSONObject;
import java.net.URL;
import java.io.OutputStream;
import java.io.IOException;
import javax.ws.rs.core.Response;

/**
 * Handles an operation on a specific file. Requires a file id in order to be
 * instantiated. Applies logic based on an {@link ExternalTool} specification,
 * such as constructing a URL to access that file.
 */
public class ExternalToolHandler {

    private static final Logger logger = Logger.getLogger(ExternalToolHandler.class.getCanonicalName());

    private final ExternalTool externalTool;
    private final DataFile dataFile;
    private final Dataset dataset;
    private final FileMetadata fileMetadata;

    private ApiToken apiToken;
    private Long userId;
    private Long guestbookId;
    private String localeCode;
    
    /**
     * File level tool
     *
     * @param externalTool The database entity.
     * @param dataFile Required.
     * @param apiToken The apiToken can be null because "explore" tools can be
     * used anonymously.
     */
    public ExternalToolHandler(ExternalTool externalTool, DataFile dataFile, ApiToken apiToken, FileMetadata fileMetadata, String localeCode) {
        this.externalTool = externalTool;
        if (dataFile == null) {
            String error = "A DataFile is required.";
            logger.warning("Error in ExternalToolHandler constructor: " + error);
            throw new IllegalArgumentException(error);
        }
        if (fileMetadata == null) {
            String error = "A FileMetadata is required.";
            logger.warning("Error in ExternalToolHandler constructor: " + error);
            throw new IllegalArgumentException(error);
        }
        this.dataFile = dataFile;
        this.apiToken = apiToken;
        this.fileMetadata = fileMetadata;
        dataset = fileMetadata.getDatasetVersion().getDataset();
        this.localeCode = localeCode;
    }
    
    public ExternalToolHandler(ExternalTool externalTool, DataFile dataFile, ApiToken apiToken, Long userId, FileMetadata fileMetadata, Long guestbookId, String localeCode){
        this(externalTool,dataFile,apiToken,fileMetadata,localeCode);
        this.userId = userId;
        this.guestbookId = guestbookId;
    }     

    /**
     * Dataset level tool
     *
     * @param externalTool The database entity.
     * @param dataset Required.
     * @param apiToken The apiToken can be null because "explore" tools can be
     * used anonymously.
     */
    public ExternalToolHandler(ExternalTool externalTool, Dataset dataset, ApiToken apiToken, String localeCode) {
        this.externalTool = externalTool;
        if (dataset == null) {
            String error = "A Dataset is required.";
            logger.warning("Error in ExternalToolHandler constructor: " + error);
            throw new IllegalArgumentException(error);
        }
        this.dataset = dataset;
        this.apiToken = apiToken;
        this.dataFile = null;
        this.fileMetadata = null;
        this.localeCode = localeCode;
    }

    public DataFile getDataFile() {
        return dataFile;
    }

    public FileMetadata getFileMetadata() {
        return fileMetadata;
    }

    public ApiToken getApiToken() {
        return apiToken;
    }

    public String getLocaleCode() {
        return localeCode;
    }

    // TODO: rename to handleRequest() to someday handle sending headers as well as query parameters.
    public String getQueryParametersForUrl() {
        return getQueryParametersForUrl(false);
    }
    
    // TODO: rename to handleRequest() to someday handle sending headers as well as query parameters.
    public String getQueryParametersForUrl(boolean preview) {
        String toolParameters = externalTool.getToolParameters();
        JsonReader jsonReader = Json.createReader(new StringReader(toolParameters));
        JsonObject obj = jsonReader.readObject();
        JsonArray queryParams = obj.getJsonArray("queryParameters");
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        List<String> params = new ArrayList<>();
        queryParams.getValuesAs(JsonObject.class).forEach((queryParam) -> {
            queryParam.keySet().forEach((key) -> {
                String value = queryParam.getString(key);
                String param = getQueryParam(key, value);
                if (param != null && !param.isEmpty()) {
                    params.add(param);
                }
            });
        });
        if (!preview) {
            return "?" + String.join("&", params);
        } else {
            return "?" + String.join("&", params) + "&preview=true";
        }
    }
    
    private String getQueryParam(String key, String value) {
        ReservedWord reservedWord = ReservedWord.fromString(value);
        switch (reservedWord) {
            case FILE_ID:
                // getDataFile is never null for file tools because of the constructor
                return key + "=" + getDataFile().getId();
            case FILE_PID:
                GlobalId filePid = getDataFile().getGlobalId();
                if (filePid != null) {
                    return key + "=" + getDataFile().getGlobalId();
                }
                break;
            case SITE_URL:
                return key + "=" + SystemConfig.getDataverseSiteUrlStatic();
            case API_TOKEN:
                String apiTokenString = null;
                ApiToken theApiToken = getApiToken();
                if (theApiToken != null) {
                    apiTokenString = theApiToken.getTokenString();
                    return key + "=" + apiTokenString;
                }
                break;
            case USER_ID:
                return key + "=" + this.userId;
            case GUESTBOOK_ID:
                if(dataset.getGuestbook() != null){
                    return key + "=" + dataset.getGuestbook().getId().toString();
                }
                break;
            case DATASET_ID:
                return key + "=" + dataset.getId();
            case DATASET_PID:
                return key + "=" + dataset.getGlobalId().asString();
            case DATASET_VERSION:
                String versionString = null;
                if(fileMetadata!=null) { //true for file case
                    versionString = fileMetadata.getDatasetVersion().getFriendlyVersionNumber();
                } else { //Dataset case - return the latest visible version (unless/until the dataset case allows specifying a version)
                    if (getApiToken() != null) {
                        versionString = dataset.getLatestVersion().getFriendlyVersionNumber();
                    } else {
                        versionString = dataset.getLatestVersionForCopy().getFriendlyVersionNumber();
                    }
                }
                if (("DRAFT").equals(versionString)) {
                    versionString = ":draft"; // send the token needed in api calls that can be substituted for a numeric
                                              // version.
                }
                return key + "=" + versionString;
            case FILE_METADATA_ID:
                if(fileMetadata!=null) { //true for file case
                    return key + "=" + fileMetadata.getId();
                }
            case LOCALE_CODE:
                return key + "=" + getLocaleCode();
            default:
                break;
        }
        return null;
    }

    public String getEncryptedQueryParametersForUrl() throws IOException{
        
        JSONObject cipherPayload = this.getJSONPayloadForEncryption();
        
        String ciphertextUserXAgentValue = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.13TNSHDaxJyPUvJhoQ0z2bSwH7jUjXqBNxvikDgRSQM";
        String encryptedParams = "";
        String ciphertextUserXAgentHeader = "User-X-Agent";
        //get this from the database? where? needs to be changed frequently I would think
        
        URL ciphertextUrl = new URL("https://dataverse-tools.ada.edu.au/api/ciphertext");
        HttpURLConnection connection = (HttpURLConnection)ciphertextUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty(ciphertextUserXAgentHeader,ciphertextUserXAgentValue);
        connection.setDoOutput(true);
        
        try(OutputStream os = connection.getOutputStream()) {
            byte[] input = cipherPayload.toString().getBytes();
            os.write(input, 0, input.length);			
        }
        
        int status = connection.getResponseCode();
        if (status != 200) {
            logger.warning("Failed to get cipher from " + ciphertextUrl.toString());
            return encryptedParams;
        }
        
        JsonObject cipherJSON = Json.createReader(connection.getInputStream()).readObject();
        encryptedParams = cipherJSON.getString("ciphertext");
        
        return encryptedParams;
    }
    
    private JSONObject getJSONPayloadForEncryption(){
        List<String> params = this.getQueryKeyValuePairs();
        
        //create a json object with the payload to send for encryption
        JSONObject json = new JSONObject();
        JSONObject payload = new JSONObject();
        String[] param_key_value = null;
        String param_key = null;
        String param_value = null;
        for (String param : params) {   
           param_key_value = param.split("=");
           param_key = param_key_value[0];
           param_value = param_key_value[1];
           if(param_key.indexOf("Id")>0){
               if(param_key.equalsIgnoreCase("fileId")){ //hack but want the value to be an actual array
                   //param_value = param_value.replace("[", "").replace("]",""); //future proofing to convert string to a string of comma separated longs
                   //long[] array = java.util.Arrays.stream(param_value.substring(1, param_value.length()-1).split(",")).map(String::trim).mapToLong(Long::parseLong).toArray();
                           //mapToInt(Integer::parseInt).toArray();
                   long fileIdAsLong = Long.parseLong(param_value);
                   payload.put(param_key,Json.createArrayBuilder().add(fileIdAsLong).build());//for an array of long's, would have to loop and .add() to createArrayBuilder
               } else{
                 payload.put(param_key,Long.parseLong(param_value)); //single int value like datasetId=45
               }
           }
           else{
            payload.put(param_key_value[0], param_key_value[1]);
           }
        }   
        json.put("payload", payload); //needs to be configurable
        return json;
    } 
    
    private List<String> getQueryKeyValuePairs(){
        List<String> params = new ArrayList<>();
        String toolParameters = externalTool.getToolParameters();
        JsonReader jsonReader = Json.createReader(new StringReader(toolParameters));
        JsonObject obj = jsonReader.readObject();
        JsonArray queryParams = obj.getJsonArray("queryParameters");
        if (queryParams == null || queryParams.isEmpty()) {
            return params;
        }
        
        queryParams.getValuesAs(JsonObject.class).forEach((queryParam) -> {
            queryParam.keySet().forEach((key) -> {
                String value = queryParam.getString(key);
                String param = getQueryParam(key, value);
                if (param != null && !param.isEmpty()) {
                    params.add(param);
                }
            });
        });
        
        return params;
    }   
    
    
            
    public String getToolUrlWithQueryParams() {
        return externalTool.getToolUrl() + getQueryParametersForUrl();
    }
    
    public String getToolUrlWithEncryptedParams() {
        String url = "";
        try{
           url = externalTool.getToolUrl() + getEncryptedQueryParametersForUrl();
        } catch(IOException exc){
            logger.severe("Error getting Encrypted Params.");
        }
        return url;
    }
    
    public String getToolUrlForPreviewMode() {
        return externalTool.getToolUrl() + getQueryParametersForUrl(true);
    }

    public ExternalTool getExternalTool() {
        return externalTool;
    }

    public void setApiToken(ApiToken apiToken) {
        this.apiToken = apiToken;
    }

}