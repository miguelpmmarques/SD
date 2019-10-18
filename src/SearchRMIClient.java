import java.io.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

public class SearchRMIClient extends UnicastRemoteObject implements ClientLibrary{
    //------ Remote Methods ---------
    static private final int rmiLOGIN = 1;
    static private final int rmiREGISTRATION = 2;
    static private final int rmiSEARCH = 3;
    static private final int rmiHISTORY = 4;
    static private final int rmiRELATEDPAGES = 5;
    static private final int rmiMANAGEUSERS = 6;
    static private final int rmiGETSYSTEMINFO = 7;
    static private final int rmiGETACTIVEUSERS = 8;
    static private final String ADMIMENU ="    --ADMIN MENU--\n\n[ADMIN] MANAGE PLAYERS PRIVILEGES - 1\n[ADMIN] CHECK SYSTEM INFO - 2\n[ADMIN] VIEW ACTIVE USERS - 3\nUcBusca -4\nHistory - 5\nRelated Pages - 6\nExit - 7\n>>> ";
    static private final String MAINMENU ="    --MAIN MENU--\n\nUcBusca - 1\nHistory -2\nRelated Pages -3\nExit -4\n>>> ";
    static private final String VIEWPLAYERSMENU ="    --MANAGE PLAYERS --\n\nLIST ALL PLAYERS - 1\nSEARCH PLAYERS BY NAME -2\nLIST ACTIVE PLAYERS -3\nBACK -4\n>>> ";

    private ServerLibrary ucBusca;
    private Scanner keyboard;
    private int intKeyboardInput;
    private BufferedReader keyboardStrings = new BufferedReader(new InputStreamReader(System.in));

    private SearchRMIClient(ServerLibrary ucBusca) throws RemoteException {
        super();
        this.ucBusca = ucBusca;
    }
    // RMI METHODS ----------------------------------------------------------------------------------------------
    public void notification(String sms) throws RemoteException{
        System.out.println(sms);
    }

    // PRIVATE METHODS ------------------------------------------------------------------------------------------
    private void retry(int rmiMethod,Object parameter) throws RemoteException, InterruptedException, NotBoundException {

        try {
            this.ucBusca=(ServerLibrary) LocateRegistry.getRegistry(1401).lookup("ucBusca" );
            switch (rmiMethod){
                case rmiLOGIN:
                    if(this.ucBusca.userLogin((User)parameter)){
                        this.adminMenu();
                    } else{
                        this.mainMenu();
                    }
                    break;
                case rmiREGISTRATION:
                    if(this.ucBusca.userRegistration((User)parameter)){
                        this.adminMenu();
                    } else{
                        this.mainMenu();
                    }
                    break;
                case rmiSEARCH:
                    String searchOutput = this.ucBusca.searchWords((String[]) parameter);
                    System.out.println(" --- Resultados de pesquisa ---\n\n"+searchOutput);
                    pressToContinue();
                    break;
                case rmiGETACTIVEUSERS:
                    ArrayList<User> listUsers = this.ucBusca.listActiveUsers();
                    Iterator it = listUsers.iterator();
                    while (it.hasNext()) {
                        User move = (User)it.next();
                        // Um if para verificar e indicar se e Admin ou nao
                        System.out.println("[Name] "+move.name+"   [Username] "+move.username);
                        it.remove(); // avoids a ConcurrentModificationException
                    }
                    pressToContinue();
                    break;
                case rmiGETSYSTEMINFO:
                    System.out.print(this.ucBusca.sendSystemInfo());
                    pressToContinue();
                    break;
                default:
                    break;
            }
        }catch (Exception e) {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e2) {
                System.out.println("Interrupted");
            }
            System.out.println("Retransmiting...");
            retry(rmiMethod,parameter);
        }
    }
    private void pressToContinue() throws IOException {
        System.out.println("\n\nPress any key to come back");
        System.in.read();
    }
    private String readInputMessages(String message){
        System.out.print(message+"\n>>> ");
        String input = "";
        while (input.equals("")) {
            try {
                input = keyboardStrings.readLine();
                if (input.equals("")){
                    System.out.print("Type something please\n>>> ");
                }
            } catch (IOException e) {
                System.out.print("Error reading please try again\n>>> ");
            }
        }
        return input;
    }
    private void doLogin() throws RemoteException, InterruptedException, NotBoundException {
        String username = this.readInputMessages("Username");
        String password1 = this.readInputMessages("Passwork");
        User clientRequest = new User(username,password1,this);
        retry(rmiLOGIN,clientRequest);
    }
    private void doRegistration() throws RemoteException, InterruptedException, NotBoundException {
        String name = this.readInputMessages("Name");
        String username = this.readInputMessages("Username");
        String password1 = this.readInputMessages("Passwork");
        String password2 = this.readInputMessages("Confirm passwork");
        while (!password1.equals(password2)){
            System.out.println("The passwords do not match, type Again");
            password1 = this.readInputMessages("Passwork");
            password2 = this.readInputMessages("Confirm passwork");
        }
        User clientRequest = new User(name,username,password1,this);
        retry(rmiREGISTRATION,clientRequest);

    }
    private void doSearch() throws RemoteException, InterruptedException, NotBoundException {
        String searchWords = this.readInputMessages("Search");
        String[] searchWordsSplited = searchWords.split("\\s+");
        retry(rmiSEARCH,searchWordsSplited);

    }
    // INTERFACES ------------------------------------------------------------------------------------------
    private void welcomePage(String sms) throws RemoteException, InterruptedException, NotBoundException {
        while (true){
            System.out.print(sms);
            intKeyboardInput=getIntProtected();
            if (intKeyboardInput == 1){
                System.out.println(" --- Login ---");
                this.doLogin();
            } else if (intKeyboardInput == 2){
                System.out.println(" --- Register ---");
                this.doRegistration();
            } else if (intKeyboardInput == 3){
                System.out.println(" --- UcBusca ---");
                this.doSearch();
            } else if (intKeyboardInput == 4){
                System.out.println(" --- Thank you come again --- \n");
                System.exit(0);
            }
        }

    }
    private int getIntProtected() {
        int intKeyboardInput = -1;
        keyboard = new Scanner(System.in);
        try {
            intKeyboardInput = keyboard.nextInt();
        }catch (Exception e){
            System.out.print("PLEASE SELECT ONE OF THE FOLLOWING OPTIONS\n>>> ");
            return intKeyboardInput;
        }
        return intKeyboardInput;
    }
    // MENUS ----------------------------------------------------------------------------------------------------
    private void adminMenu() throws RemoteException, InterruptedException, NotBoundException {
        while (true){
            System.out.print(ADMIMENU);
            intKeyboardInput=getIntProtected();
            if (intKeyboardInput == 1){
                System.out.println("\n --- Manage Players Privileges--");
                // TO DO
            } else if (intKeyboardInput == 2){
                System.out.println("\n --- Check System Info ---");
                retry(rmiGETSYSTEMINFO,null);
            } else if (intKeyboardInput == 3){
                System.out.println("\n --- View Active Users ---");
                retry(rmiGETACTIVEUSERS,null);
            }else if (intKeyboardInput == 4){
                System.out.println("\n --- UcBusca ---");
                this.doSearch();
            } else if (intKeyboardInput == 5){
                System.out.println("\n --- History ---");
                // TO DO
            } else if (intKeyboardInput == 6){
                System.out.println("\n --- Related Pages ---");
                // TO DO
            } else if (intKeyboardInput == 7){
                System.out.println("\n --- Thank you come again ---");
                System.exit(0);
            }
        }
    }
    private void mainMenu() throws RemoteException, InterruptedException, NotBoundException {
        while (true){
            System.out.print(MAINMENU);
            intKeyboardInput = getIntProtected();
            if (intKeyboardInput == 1){
                System.out.println(" --- UcBusca ---");
                this.doSearch();
                break;
            } else if (intKeyboardInput == 2){
                System.out.println(" --- History ---");
                break;
            } else if (intKeyboardInput == 3){
                System.out.println(" --- Related Pages ---");
                break;
            } else if (intKeyboardInput == 4){
                System.out.println(" --- Thank you come again ---");
                System.exit(0);
                return;
            }
        }
        System.out.println("MAIN MENU...");

    }
    // MAIN ----------------------------------------------------------------------------------------------------
    public static void main(String[] args) {
        try {
            ServerLibrary ucBusca = (ServerLibrary) LocateRegistry.getRegistry(1401).lookup("ucBusca" );
            SearchRMIClient client = new SearchRMIClient(ucBusca);
            System.out.println("Connected to UcBusca");
            client.welcomePage(ucBusca.connected((SearchRMIClient) client));
        } catch (Exception e) {
            System.out.println("Exception in main: " + e);
        }
    }
}
