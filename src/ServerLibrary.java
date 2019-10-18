import java.rmi.*;
import java.util.*;

public interface ServerLibrary extends Remote {
    public String connected(ClientLibrary newUser) throws RemoteException;
    public boolean userRegistration(User newUser) throws RemoteException;
    public boolean userLogin(User newUser) throws RemoteException;
    public String searchWords(String[] words) throws RemoteException;
    public int  checkMe() throws RemoteException;
    public ArrayList<User> listActiveUsers() throws RemoteException;
    public String sendSystemInfo() throws RemoteException;
}
