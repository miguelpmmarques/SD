import java.io.*;
import java.util.*;
import RMISERVER.*;
// Class responsible for keeping all the information relating to the database files
// and with all the functions necessary for modifying those files
// all the methods are synchronized, so that the whole class is, in practice synchronized, and no
// threads using an object from this class to get or save something into the database
// can enter unsynchronized
public class FilesNamesObject {
    String userFile;
    String indexFile;
    String referenceFile;
    String descriptionTitleFile;
    public FilesNamesObject(int portId){
         this.indexFile = "indexURL"+portId+".tmp";;
         this.referenceFile = "referenceURL"+portId+".tmp";
         this.userFile = "users"+portId+".tmp";
         this.descriptionTitleFile = "descriptionTitle"+portId+".tmp";
    }

    // self-explanatory
    public synchronized ArrayList<User> loadUsersFromDataBase() {
        File f_users = new File(this.userFile);
        FileInputStream fis;
        ObjectInputStream ois;
        ArrayList<User> users_list = new ArrayList<>();
        try {
            fis = new FileInputStream(f_users);
            ois = new ObjectInputStream(fis);
            users_list = (ArrayList) ois.readObject();
            return users_list;
        } catch (FileNotFoundException ex) {
            return new ArrayList<User>();
        } catch (IOException e) {
            e.printStackTrace();
            new ArrayList<User>();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            new ArrayList<User>();
        }
        return users_list;
    }

    // loading the indexURL and referenceURL hashsets from database
    public synchronized HashMap<String, HashSet<String>> loadDataBase(String file) {
        String file_name = "";
        switch (file) {
            case "INDEX":
                file_name = this.indexFile;
                break;
            case "REFERENCE":
                file_name = this.referenceFile;
                break;
            case "DESCRIPTION":
                file_name = this.descriptionTitleFile;
                break;
        }
        File f_ref = new File(file_name);
        FileInputStream fis;
        ObjectInputStream ois;
        HashMap<String, HashSet<String>> map;
        try {
            fis = new FileInputStream(f_ref);
            ois = new ObjectInputStream(fis);
            map = (HashMap<String, HashSet<String>>) ois.readObject();
            return map;

        } catch (FileNotFoundException ex) {
            System.out.println("Ficheiro ainda nao existe");
            return new HashMap<>();
        } catch (IOException e) {
            System.out.println("ERROR READING FILE --> "+file_name);
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR FIDING CLASS FROM FILE --> "+file_name);
        }
        return new HashMap<>();
    }

    // self-explanatory
    public synchronized void saveUsersToDataBase(ArrayList<User> objectToSave){
        try {
            FileOutputStream fos = new FileOutputStream(this.userFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objectToSave);
            oos.close();
            fos.close();
        } catch (IOException ex) {
            System.out.println("Erro a escrever para o ficheiro.");
            ex.printStackTrace();
        }
    }
    // self-explanatory
    public synchronized void saveHashSetsToDataBase(String typeObject, HashMap<String, HashSet<String>> objectToSave) {
        String file_name = "";
        switch (typeObject) {
            case "INDEX":
                file_name = this.indexFile;
                break;
            case "REFERENCE":
                file_name = this.referenceFile;
                break;
            case "DESCRIPTION":
                file_name = this.descriptionTitleFile;
                break;
        }

        try {
            FileOutputStream fos = new FileOutputStream(file_name);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objectToSave);

            //System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> " + typeObject);
            oos.close();
            fos.close();
        } catch (IOException ex) {
            System.out.println("Erro a escrever para o ficheiro.");
            ex.printStackTrace();
        }
    }

}
