import java.io.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.Scanner;

public class SearchRMIClient extends UnicastRemoteObject implements ClientLibrary{
    //------ Remote Methods ---------
    static final int rmiLOGIN = 1;
    static final int rmiREGISTRATION = 2;
    static final int rmiSEARCH = 3;
    static final int rmiHISTORY = 4;
    static final int rmiRELATEDPAGES = 5;
    static final String ADMIMENU ="    --ADMIN MENU--\n\n[ADMIN] MANAGE PLAYERS PRIVILEGES - 1\n[ADMIN] CHECK SYSTEM INFO - 2\nUcBusca - 3\nHistory -4\nRelated Pages -5\nExit -6\n>>> ";
    static final String MAINMENU ="    --MAIN MENU--\n\nUcBusca - 1\nHistory -2\nRelated Pages -3\nExit -4\n>>> ";
    static final String VIEWPLAYERSMENU ="    --MANAGE PLAYERS --\n\nLIST ALL PLAYERS - 1\nSEARCH PLAYERS BY NAME -2\nLIST ACTIVE PLAYERS -3\nBACK -4\n>>> ";

    ServerLibrary ucBusca;
    Scanner keyboard = new Scanner(System.in);
    int intKeyboardInput;
    BufferedReader keyboardStrings = new BufferedReader(new InputStreamReader(System.in));

    SearchRMIClient(ServerLibrary ucBusca) throws RemoteException {
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
            Thread.sleep(1000);
        } catch(InterruptedException e) {
            System.out.println("Interrupted");
        }
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
                    System.out.println(" --- Resultados de pesquisa ---\n\n"+searchOutput+"\n\nPress any key to come back");
                    System.in.read();
                    break;
                default:
                    break;
            }
        }catch (Exception e) {
            System.out.println("Retransmiting...");
            retry(rmiMethod,parameter);
        }
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
    public void welcomePage(String sms) throws RemoteException, InterruptedException, NotBoundException {
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
            }else{
                System.out.print("Please choose a number between 1 and 3 to select the options\n>>> ");
            }
        }

    }
    private int getIntProtected(){
        intKeyboardInput = -1;
        keyboard.reset();
        try {
            intKeyboardInput = keyboard.nextInt();
        }catch (Exception e){
            return intKeyboardInput;
        }
        return intKeyboardInput;
    }
    public void adminMenu() throws RemoteException, InterruptedException, NotBoundException {
        while (true){
            System.out.print(ADMIMENU);
            intKeyboardInput=getIntProtected();
            if (intKeyboardInput == 1){
                System.out.println(" --- Manage Players Privileges--");
                System.exit(0);
            } else if (intKeyboardInput == 2){
                System.out.println(" --- Check System Info ---");
                System.exit(0);
            } else if (intKeyboardInput == 3){
                System.out.println(" --- UcBusca ---");
                this.doSearch();
            } else if (intKeyboardInput == 4){
                System.out.println(" --- History ---");
                System.exit(0);
            } else if (intKeyboardInput == 5){
                System.out.println(" --- Related Pages ---");
                System.exit(0);
            } else if (intKeyboardInput == 6){
                System.out.println(" --- Thank you come again ---");
                System.exit(0);
            } else {
                System.out.print("Please choose a number between 1 and 3 to select the options\n>>> ");
            }
        }
    }
    public void mainMenu() throws RemoteException, InterruptedException, NotBoundException {
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
            } else if (intKeyboardInput == 4){
                System.out.println(" --- Thank you come again ---");
                System.exit(0);
                return;
            }else {
                System.out.print("Please choose a number between 1 and 3 to select the options\n>>> ");
            }
        }
        System.out.println("MAIN MENU...");

    }
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
