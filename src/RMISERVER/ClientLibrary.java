package RMISERVER;

import java.rmi.*;

public interface ClientLibrary extends Remote {
    public void notification(String sms) throws RemoteException;
}
