import java.io.*;

public class User implements Serializable {
    //Mudar depois
    public String username;
    public String password;
    public ClientLibrary client;
    public boolean isAdmin = false;
    public boolean notify = false;
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
    public ClientLibrary getClient(){
        return this.client;
    }
    public Boolean getIsAdmin(){
        return this.isAdmin;
    }
    public Boolean getNotify() { return this.notify; }
    public void setIsAdmin(Boolean decision) { this.isAdmin = true;}
    public void setNotify(Boolean decision) { this.notify = decision;}
}