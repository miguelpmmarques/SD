import java.rmi.*;

public interface ServerLibrary extends Remote {
    public String connected(ClientLibrary newUser) throws RemoteException;
    public boolean userRegistration(User newUser) throws RemoteException;
    public boolean userLogin(User newUser) throws RemoteException;
    public String searchWords(String[] words) throws RemoteException;
    public int  checkMe() throws RemoteException;
}
