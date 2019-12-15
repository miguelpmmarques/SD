import java.io.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;

import RMISERVER.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class SearchRMIClient extends UnicastRemoteObject implements ClientLibrary {
    //------ Remote Methods ---------
    static private final int REPLYCOUNTER = 0;
    static private final int REPLYCOUNTERTIMEOUT = 16;

    static private final int rmiLOGIN = 1;
    static private final int rmiREGISTRATION = 2;
    static private final int rmiSEARCH = 3;
    static private final int rmiHISTORY = 4;
    static private final int rmiRELATEDPAGES = 5;
    static private final int rmiCHANGEUSERPRIVILEGES = 6;
    static private final int rmiUSERSLIST = 7;
    static private final int rmiGETSYSTEMINFO = 8;
    static private final int rmiADDURL = 9;
    static private final String ADMIMENU ="    --ADMIN MENU--\n\n[ADMIN] MANAGE PLAYERS PRIVILEGES - 1\n[ADMIN] CHECK SYSTEM INFO - 2\n[ADMIN] ADD URL TO UCBUSCA - 3\nUcBusca - 4\nHistory - 5\nRelated Pages - 6\nExit - 7\n>>> ";
    static private final String MAINMENU ="    --MAIN MENU--\n\nUcBusca - 1\nHistory - 2\nRelated Pages - 3\nExit - 4\n>>> ";
    static private final String VIEWPLAYERSMENU ="\n --- MANAGE PLAYERS MENU ---\n\nLIST ALL USERS - 1\nCHANGE USER PRIVILEGES - 2\nBACK - 3\n>>> ";

    private ServerLibrary ucBusca;
    private Scanner keyboard;
    private int intKeyboardInput;
    private BufferedReader keyboardStrings = new BufferedReader(new InputStreamReader(System.in));
    private User thisUser = null;
    private Properties prop;

    private SearchRMIClient(ServerLibrary ucBusca, Properties prop) throws RemoteException {
        super();
        this.prop = prop;
        this.ucBusca = ucBusca;
    }
    // CLIENT RMI METHODS ----------------------------------------------------------------------------------------------
    public void notification(String sms) throws RemoteException{
        System.out.println("\n\n"+sms);
        thisUser.setIsAdmin();
    }
    // PRIVATE METHODS ------------------------------------------------------------------------------------------
    private void pressToContinue() throws IOException {
        System.out.println("\n\nPress any key to come back");
        System.in.read();
    }

    /*
    * Metodo para proteger quando o utilizador submete um url para o servidor RMI
    * As protecoes aqui feitas sao, obrigar o utilizador a escrever um '.',
    * impedir que ele envie um espaco em branco ou so espacos
    * e caso ele nao colocoque http:// nem https://, nos colocamos http:// por ele :P
    * */
    private String writeURL() throws IOException {
        String out = "";
        boolean flag;
        do{
            System.out.print(">>>");
            flag = false;
            try {
                out = keyboardStrings.readLine();
            }catch (IOException e){
                flag = true;
            }
            if (flag || out.length()==0 || !out.contains(".")){
                System.out.println("Invalid, rewrite please");
            }
        }while (flag || out.length()==0 || !out.contains("."));
        out = out.trim();
        if (! out.startsWith("http://") && ! out.startsWith("https://"))
            out = "http://".concat(out);
        return out;
    }
    /*
    * Metodo que nao deixa o utilizador escrever letras quando se pede um numero :/
    * */
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
    /*
    * Metodo utilizado para ler conjuntos de palavras escritas pelo utilizador
    * Utilizado para o login, registo e pesquisa :)
    * */
    private String readInputMessages(String message){
        System.out.print(message+"\n>>> ");
        String input="";
        do {
            try {
                input = keyboardStrings.readLine();
                if (input.trim().length() == 0){
                    System.out.print("Type something please\n>>> ");
                }
            } catch (IOException e) {
                System.out.print("Error reading please try again\n>>> ");
            }
        }while (input.trim().length() == 0);
        return input;
    }
    /*
     * Um print do array :P
     * */
    private void printArray(String name,HashMap myDic){
        int arraySize = Integer.parseInt((String)myDic.get(name+"_count"));
        if (arraySize == 0)
            System.out.println(" -- EMPTY --");
        for(int i =1 ;i<arraySize+1;i++){
            System.out.println(i+"º --> "+(String)myDic.get(name+"_"+i));
        }
    }
    /*
    * Print do ULR com a descricao, titulo e URL :o
    * */
    private void printURLS(HashMap myDic)  {
        int arraySize = Integer.parseInt((String)myDic.get("url_count"));
        if (arraySize == 0)
            System.out.println(" -- EMPTY --");
        for(int i =1 ;i<arraySize+1;i++){
            String info = (String)myDic.get("url_"+i);
            String[] split_info = info.split("\\*oo#&");
            String url = split_info[0];
            String title = split_info[1];
            String description = split_info[2];
            System.out.println("TITLE--> "+title);
            System.out.println("DESCRIPTION-->"+ description);
            System.out.println("URL ---> "+url+ "\n\n");
        }
    }
    /*
    * Metodo que traduz as opcoes selecionadas do utilizador para metodos remotos
    * para o servidor RMI
    * */
    private void retry(int rmiMethod,Object parameter,int replyCounter) throws RemoteException, InterruptedException, NotBoundException {
        HashMap<String,String> myDic;
        try {
            this.ucBusca=(ServerLibrary) LocateRegistry.getRegistry(prop.getProperty("REGISTRYIP"), Integer.parseInt(prop.getProperty("REGISTRYPORT"))).lookup(prop.getProperty("LOOKUP"));
            switch (rmiMethod){
                case rmiLOGIN:
                    myDic = this.ucBusca.userLogin((User)parameter, false);
                    if(myDic.get("status").equals("logged on")){
                        this.thisUser = new User(((User) parameter).getUsername(),((User) parameter).getPassword(),this);
                        System.out.println("YOU JUST LOGGED ON");
                        this.mainMenu();
                    } else if(myDic.get("status").equals("logged admin")){
                        this.thisUser = new User(((User) parameter).getUsername(),((User) parameter).getPassword(),this);
                        System.out.println("YOU JUST LOGGED ON AS ADMIN");
                        this.adminMenu();
                    }
                    else {
                        System.out.println("INVALID LOGIN");
                        return;
                    }
                    break;
                case rmiREGISTRATION:
                    myDic = this.ucBusca.userRegistration((User)parameter, false);
                    if(myDic.get("status").equals("Success")){
                        System.out.println("YOU JUST LOGGED ON");
                        this.mainMenu();
                    } else if(myDic.get("status").equals("Admin")){
                        System.out.println("YOU JUST LOGGED ON AS ADMIN");
                        this.adminMenu();
                    }
                    else {
                        System.out.println("INVALID REGISTER, USERNAME ALREADY IN USE");
                        return;
                    }
                    break;
                case rmiSEARCH:
                    myDic = this.ucBusca.searchWords((String[]) parameter);
                    System.out.println(" --- Resultados de pesquisa ---\n\n");
          System.out.println("DIC===" +myDic);
                    printURLS(myDic);
                    pressToContinue();
                    break;
                case rmiADDURL:
                    this.ucBusca.addURLbyADMIN((String) parameter);
                    break;
                case rmiGETSYSTEMINFO:
                    myDic = this.ucBusca.sendSystemInfo();
                    System.out.println("MULTICAST SERVERS ACTIVE");
                    printArray("activeMulticast",myDic);
                    System.out.println("\nTOP 10 IMPORTANT PAGES");
                    printArray("important_pages",myDic);
                    System.out.println("\nTOP 10 SEARCHES");
                    printArray("top_search",myDic);
                    pressToContinue();
                    break;
                case rmiUSERSLIST:
                    myDic = this.ucBusca.getAllUsers();
                    printArray("user",myDic);
                    pressToContinue();
                    break;
                case rmiRELATEDPAGES:
                    myDic = this.ucBusca.getReferencePages((String) parameter);
                    printArray("url",myDic);
                    pressToContinue();
                    return;
                case rmiHISTORY:
                    myDic = this.ucBusca.getHistory((User)parameter);
                    printArray("word",myDic);
                    pressToContinue();
                    break;
                case rmiCHANGEUSERPRIVILEGES:
                    myDic = this.ucBusca.changeUserPrivileges((String) parameter);
                    System.out.println(myDic.get("status"));
                    return;
                default:
                    break;
            }
        }catch (Exception e) {
            try {
                Thread.sleep(2000);
            } catch(InterruptedException e2) {
                System.out.println("Interrupted");
            }
            if (replyCounter>REPLYCOUNTERTIMEOUT){
                System.out.println("Please, try no reconnect to the UcBusca");
                System.exit(0);
            }
            System.out.println("Retransmiting... "+replyCounter);
            retry(rmiMethod,parameter,++replyCounter);
        }
    }
    /*
    * Self-explanatory
    * */
    private void doLogin() throws RemoteException, InterruptedException, NotBoundException {
        String username = this.readInputMessages("Username");
        String password1 = this.readInputMessages("Passwork");
        retry(rmiLOGIN,new User(username,password1,this),REPLYCOUNTER);
    }
    /*
     * Self-explanatory
     * */
    private void doRegistration() throws RemoteException, InterruptedException, NotBoundException {
        String username = this.readInputMessages("Username");
        String password1 = this.readInputMessages("Passwork");
        String password2 = this.readInputMessages("Confirm passwork");
        while (!password1.equals(password2)){
            System.out.println("The passwords do not match, type Again");
            password1 = this.readInputMessages("Passwork");
            password2 = this.readInputMessages("Confirm passwork");
        }
        this.thisUser = new User(username,password1,this);
        retry(rmiREGISTRATION,this.thisUser,REPLYCOUNTER);

    }
    /*
     * Self-explanatory
     * */
    private void doSearch() throws RemoteException, InterruptedException, NotBoundException {
        String searchWords = this.readInputMessages("Search");
        if (this.thisUser==null){
            searchWords = "Anonymous "+searchWords;
        }
        else {
            searchWords = thisUser.getUsername()+" "+searchWords;
        }
        String[] searchWordsSplited = searchWords.split("\\s+");
        retry(rmiSEARCH,searchWordsSplited,REPLYCOUNTER);

    }

    // MENUS ----------------------------------------------------------------------------------------------------
    /*
     * Menu dentro do menu do admin onde este pode ver todos os utilizadores
     * que se registaram na aplicacao e permite tambem tornar estes mesmos
     * administradores
     * */
    private void manageUsersMenu() throws IOException, InterruptedException, NotBoundException {
        while (true){
            System.out.print(VIEWPLAYERSMENU);
            intKeyboardInput=getIntProtected();
            switch (intKeyboardInput){
                case 1:
                    retry(rmiUSERSLIST,null,REPLYCOUNTER);
                    break;
                case 2:
                    retry(rmiCHANGEUSERPRIVILEGES,this.readInputMessages("Name User"),REPLYCOUNTER);
                    break;
                case 3:
                    return;
                default:
                    System.out.println("Choose one of the options");
            }

        }
    }
    /*
    * Menu de administracao
    * */
    private void adminMenu() throws IOException, InterruptedException, NotBoundException {
        while (true){
            System.out.print(ADMIMENU);
            intKeyboardInput=getIntProtected();
            switch (intKeyboardInput){
                case 1:
                    this.manageUsersMenu();
                    break;
                case 2:
                    System.out.println("\n --- Check System Info ---");
                    retry(rmiGETSYSTEMINFO,null,REPLYCOUNTER);
                    break;
                case 3:
                    System.out.println("\n --- ADD URL TO UCBUSCA ---");
                    retry(rmiADDURL,this.writeURL(),REPLYCOUNTER);
                    break;
                case 4:
                    System.out.println("\n --- UcBusca ---");
                    this.doSearch();
                    break;
                case 5:
                    System.out.println("\n --- History ---");
                    retry(rmiHISTORY,this.thisUser,REPLYCOUNTER);
                    break;
                case 6:
                    System.out.println("\n --- Related Pages ---");
                    retry(rmiRELATEDPAGES,this.writeURL(),REPLYCOUNTER);
                    break;
                case 7:
                    System.out.println("\n --- Thank you come again ---");
                    System.exit(0);
                default:
                    System.out.println("Choose one of the options");
            }
        }
    }
    /*
     * Menu de utilizadores nao administradores
     * */
    private void mainMenu() throws IOException, InterruptedException, NotBoundException {
        while (true){

            System.out.print(MAINMENU);
            intKeyboardInput = getIntProtected();
            if (thisUser.getIsAdmin()){
                this.adminMenu();
                return;
            }

            switch (intKeyboardInput){
                case 1:
                    System.out.println(" --- UcBusca ---");
                    this.doSearch();
                    break;
                case 2:
                    System.out.println(" --- History ---");
                    retry(rmiHISTORY,this.thisUser,REPLYCOUNTER);
                    break;
                case 3:
                    System.out.println(" --- Related Pages ---");
                    retry(rmiRELATEDPAGES,this.writeURL(),REPLYCOUNTER);
                    break;
                case 4:
                    System.out.println(" --- Thank you come again ---");
                    System.exit(0);
                    return;
                default:
                    System.out.println("Choose one of the options");
            }
        }
    }
    /*
     * Menu inicial
     * */
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
    // MAIN ----------------------------------------------------------------------------------------------------
    public static void main(String[] args)  {
        final int TIMEOUT = 15;
        String propFileName = "config.properties";
        InputStream inputStream = SearchRMIClient.class.getClassLoader().getResourceAsStream(propFileName);
        Properties prop = new Properties();
        try {
            prop.load(inputStream);

        } catch (Exception e){
            System.out.println("Cannot read properties File");
            return;
        }
        for (int i =0 ;i<TIMEOUT;i++){
            try {
                ServerLibrary ucBusca = (ServerLibrary) LocateRegistry.getRegistry(prop.getProperty("REGISTRYIP"), Integer.parseInt(prop.getProperty("REGISTRYPORT") )).lookup(prop.getProperty("LOOKUP") );
                SearchRMIClient client = new SearchRMIClient(ucBusca,prop);
                System.out.println("Connected to UcBusca");
                client.welcomePage(ucBusca.connected((SearchRMIClient) client));
                return;
            } catch (Exception e) {
                System.out.println("Connecting...");
                try {
                    Thread.sleep(2000);
                }catch (InterruptedException es){
                    System.out.println("Sleep interrupted");
                }
            }
        }
        System.out.println("Server is offline");
    }
}

