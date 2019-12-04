package RMISERVER;

import java.io.Serializable;
import java.util.ArrayList;

public class User implements Serializable {

    private String username;
    private String password;
    private ClientLibrary client;
    private boolean isAdmin = false;
    private boolean notify = false;
    private ArrayList<String> userHistory = new ArrayList<>();
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public User(String username, String password, ClientLibrary client) {
        this.username = username;
        this.password = password;
        this.client = (ClientLibrary)client;
    }
    public ArrayList<String> getHistory(){
        return this.userHistory;
    }
    public String getUsername(){
        return this.username;
    }
    public String getPassword(){ return this.password; }
    public ClientLibrary getClient(){
        return this.client;
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
    public Boolean getIsAdmin(){
        return this.isAdmin;
    }
    public Boolean getNotify() { return this.notify; }
    public void setIsAdmin() { this.isAdmin = true;}
    public void setNotify(Boolean decision) { this.notify = decision;}
}