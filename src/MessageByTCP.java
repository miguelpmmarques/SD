import java.io.Serializable;
import java.util.*;

public class MessageByTCP implements Serializable {
    String type;
    HashMap<String, HashSet<String>> refereceURL;
    HashMap<String, HashSet<String>> indexURL;
    ArrayList<User> users_list;
    public MessageByTCP(String type, HashMap<String, HashSet<String>> refereceURL,HashMap<String, HashSet<String>> indexURL,ArrayList<User> users_list){
        this.type = type;
        this.refereceURL= refereceURL;
        this.indexURL= indexURL;
        this.users_list= users_list;
    }
    public MessageByTCP(String type, HashMap<String, HashSet<String>> indexURL){
        this.type = type;
        this.indexURL = indexURL;
    }
    public String getType(){ return this.type; }
    public ArrayList<User> getUsers(){
        return this.users_list;
    }
    public HashMap<String, HashSet<String>> getIndexURL(){
        return this.indexURL;
    }
    public HashMap<String, HashSet<String>> getRefereceURL(){
        return this.refereceURL;
    }
}
