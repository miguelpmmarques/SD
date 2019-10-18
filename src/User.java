import java.io.*;

public class User implements Serializable {
    public String name;
    public String username;
    public String password;
    public ClientLibrary client;

    public User(String name,String username,String password,ClientLibrary client) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.client = (ClientLibrary)client;
    }
    public User(String username,String password,ClientLibrary client) {
        this.username = username;
        this.password = password;
        this.client = (ClientLibrary)client;
    }
}