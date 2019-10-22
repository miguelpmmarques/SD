import java.net.UnknownHostException;
import java.rmi.*;
import java.util.*;

public interface ServerLibrary extends Remote {
    public String connected(ClientLibrary newUser) throws RemoteException;
    public boolean userRegistration(User newUser) throws RemoteException, UnknownHostException;
    public String userLogin(User newUser) throws RemoteException, InterruptedException;
    public String searchWords(String[] words) throws RemoteException;
    public int checkMe() throws RemoteException;
    public String sendSystemInfo() throws RemoteException;
    public void addURLbyADMIN(String url) throws RemoteException;
    public String getAllUsers() throws RemoteException;
    public String getReferencePages(String url) throws RemoteException;
    public String getHistory(User thisUser) throws RemoteException;
    public String changeUserPrivileges(String username) throws RemoteException;
    }

