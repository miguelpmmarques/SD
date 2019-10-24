import java.io.*;
import java.util.ArrayList;

public class User implements Serializable {
    //Mudar depois
    public String username;
    public String password;
    public ClientLibrary client;
    public boolean isAdmin = false;
    public boolean notify = false;
    public ArrayList<String> userHistory = new ArrayList<>();
    public User(String username,String password) {
        this.username = username;
        this.password = password;
    }

    public User(String username,String password,ClientLibrary client) {
        this.username = username;
        this.password = password;
        this.client = (ClientLibrary)client;
    }
    public String getUsername(){
        return this.username;
    }
    public String getPassword(){
        // subject to change
        return this.password;
    }
    public void setThis(ClientLibrary client){
        this.client = client;
    }
    public void addSearchToHistory(String words){
        userHistory.add(words);
    }
    public ArrayList<String> getSearchToHistory(){
        return userHistory;
    }
    public ClientLibrary getClient(){
        return this.client;
    }
    public Boolean getIsAdmin(){
        return this.isAdmin;
    }
    public Boolean getNotify() { return this.notify; }
    public void setIsAdmin() { this.isAdmin = true;}
    public void setNotify(Boolean decision) { this.notify = decision;}
}