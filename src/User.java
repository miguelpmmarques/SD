import java.io.*;

public class User implements Serializable {
    private String username;
    private String password;
    private ClientLibrary client;
    private boolean isAdmin = false;
    private boolean notify = false;

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
    public void setIsAdmin(Boolean decision) { this.isAdmin = decision;}
    public void setNotify(Boolean decision) { this.notify = decision;}
}