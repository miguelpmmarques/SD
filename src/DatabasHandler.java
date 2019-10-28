import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


// Thread responsible for synchronized saving of objects to our database-- Object Files as of now
class DatabaseHandler extends Thread {
    private HashMap<String, HashSet<String>> refereceURL = null;
    private HashMap<String, HashSet<String>> indexURL = null;
    private ArrayList<User> users_list = null;
    private FilesNamesObject fileManager;

    // constructor for saving users
    public DatabaseHandler(ArrayList<User> users_list, FilesNamesObject fileManager) {
        super();
        this.setName("DatabaseHandler-" + this.getId());
        this.users_list = users_list;
        this.fileManager = fileManager;
    }

    // constructor for saving urls
    public DatabaseHandler(
            HashMap<String, HashSet<String>> refereceURL, HashMap<String, HashSet<String>> indexURL, FilesNamesObject fileManager) {
        super();
        this.setName("DatabaseHandler-" + this.getId());
        this.refereceURL = refereceURL;
        this.indexURL = indexURL;
        this.fileManager = fileManager;
    }

    // depending on the initialization of the class, the thread may save the users or the url HashMaps
    public void run() {
        if (this.users_list == null) {
            this.fileManager.saveHashSetsToDataBase("INDEX", this.indexURL);
            this.fileManager.saveHashSetsToDataBase("REFERENCE", this.refereceURL);
        } else {
            this.fileManager.saveUsersToDataBase(this.users_list);

        }
    }

}
